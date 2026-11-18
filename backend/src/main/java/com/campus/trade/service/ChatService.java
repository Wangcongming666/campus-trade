package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trade.common.BizException;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.common.PageQuery;
import com.campus.trade.common.PageResult;
import com.campus.trade.dto.ChatSendDTO;
import com.campus.trade.entity.ChatMessage;
import com.campus.trade.entity.ChatSession;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.SysUser;
import com.campus.trade.mapper.ChatMessageMapper;
import com.campus.trade.mapper.ChatSessionMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.SysUserMapper;
import com.campus.trade.vo.ChatMessageVO;
import com.campus.trade.vo.ChatSessionVO;
import com.campus.trade.vo.ReadReceiptVO;
import com.campus.trade.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 即时聊天：会话（同一商品同一对用户唯一）、消息持久化、STOMP 推送（/user/queue/messages、/user/queue/read）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    public static final String QUEUE_MESSAGES = "/queue/messages";
    public static final String QUEUE_READ = "/queue/read";

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final GoodsMapper goodsMapper;
    private final SysUserMapper userMapper;
    private final UserBehaviorService behaviorService;
    private final SimpMessagingTemplate messagingTemplate;

    /** POST /chat/sessions：已存在返回原会话，新建时记录聊天行为（type=3）。 */
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionVO openSession(Long me, Long goodsId, Long toUserId) {
        ChatSession s = findOrCreate(me, goodsId, toUserId);
        return toVOs(List.of(s), me).get(0);
    }

    public List<ChatSessionVO> listSessions(Long me) {
        List<ChatSession> list = sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .and(w -> w.eq(ChatSession::getInitiatorId, me).or().eq(ChatSession::getPeerId, me))
                .last("ORDER BY COALESCE(last_time, create_time) DESC, id DESC"));
        return toVOs(list, me);
    }

    /** 历史消息：pageNum=1 为最新一页（按时间倒序分页），页内记录按 create_time 升序返回，便于直接渲染。 */
    public PageResult<ChatMessageVO> messages(Long me, Long sessionId, PageQuery pq) {
        requireParticipant(sessionId, me);
        Page<ChatMessage> page = messageMapper.selectPage(pq.toPage(), new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getCreateTime).orderByDesc(ChatMessage::getId));
        List<ChatMessageVO> records = new ArrayList<>(page.getRecords().stream().map(ChatMessageVO::of).toList());
        Collections.reverse(records);
        return PageResult.of(page, records);
    }

    /** 标记发给我的消息为已读，清零未读数，并向对方推送已读回执。 */
    @Transactional(rollbackFor = Exception.class)
    public ReadReceiptVO markRead(Long me, Long sessionId) {
        ChatSession s = requireParticipant(sessionId, me);
        int n = messageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId).eq(ChatMessage::getToUserId, me)
                .eq(ChatMessage::getReadFlag, 0).set(ChatMessage::getReadFlag, 1));
        sessionMapper.clearUnread(sessionId, me);
        ReadReceiptVO push = new ReadReceiptVO(sessionId, me, null);
        Long peer = me.equals(s.getInitiatorId()) ? s.getPeerId() : s.getInitiatorId();
        afterCommit(() -> messagingTemplate.convertAndSendToUser(String.valueOf(peer), QUEUE_READ, push));
        return new ReadReceiptVO(sessionId, me, n);
    }

    /**
     * STOMP /app/chat.send：找到或创建会话，落库，更新会话摘要与未读数，推送给收发双方。
     * type=3（商品卡片）的 content 为前端序列化的 JSON 字符串 {goodsId,title,coverUrl,price}，原样存储，仅校验长度。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO send(Long me, ChatSendDTO dto) {
        int type = dto.getType() == null ? 1 : dto.getType();
        if (type < 1 || type > 3 || dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_INVALID);
        }
        ChatSession s = findOrCreate(me, dto.getGoodsId(), dto.getToUserId());
        ChatMessage m = new ChatMessage();
        m.setSessionId(s.getId());
        m.setFromUserId(me);
        m.setToUserId(dto.getToUserId());
        m.setGoodsId(dto.getGoodsId());
        m.setContent(dto.getContent());
        m.setType(type);
        m.setReadFlag(0);
        m.setCreateTime(LocalDateTime.now().withNano(0));
        messageMapper.insert(m);
        sessionMapper.onNewMessage(s.getId(), dto.getToUserId(), summary(type, dto.getContent()), m.getCreateTime());
        ChatMessageVO vo = ChatMessageVO.of(m);
        afterCommit(() -> {
            messagingTemplate.convertAndSendToUser(String.valueOf(dto.getToUserId()), QUEUE_MESSAGES, vo);
            messagingTemplate.convertAndSendToUser(String.valueOf(me), QUEUE_MESSAGES, vo);
        });
        return vo;
    }

    public ChatSession requireParticipant(Long sessionId, Long me) {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null) {
            throw new BizException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        if (!me.equals(s.getInitiatorId()) && !me.equals(s.getPeerId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return s;
    }

    private ChatSession findOrCreate(Long me, Long goodsId, Long toUserId) {
        if (me.equals(toUserId)) {
            throw new BizException(ErrorCode.CHAT_SELF);
        }
        Goods g = goodsMapper.selectById(goodsId);
        if (g == null) {
            throw new BizException(ErrorCode.GOODS_NOT_FOUND);
        }
        if (!me.equals(g.getSellerId()) && !toUserId.equals(g.getSellerId())) {
            throw new BizException(ErrorCode.CHAT_NOT_SELLER);
        }
        if (userMapper.selectById(toUserId) == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        ChatSession existing = find(goodsId, me, toUserId);
        if (existing != null) {
            return existing;
        }
        ChatSession s = new ChatSession();
        s.setGoodsId(goodsId);
        s.setInitiatorId(me);
        s.setPeerId(toUserId);
        s.setInitiatorUnread(0);
        s.setPeerUnread(0);
        try {
            sessionMapper.insert(s);
        } catch (DuplicateKeyException e) {
            // 并发创建：返回已存在的会话
            return find(goodsId, me, toUserId);
        }
        // 买家发起咨询才计入行为（卖家主动联系不算对自己商品的兴趣）
        if (!me.equals(g.getSellerId())) {
            afterCommit(() -> behaviorService.recordChat(me, goodsId));
        }
        return sessionMapper.selectById(s.getId());
    }

    private ChatSession find(Long goodsId, Long a, Long b) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getGoodsId, goodsId)
                .eq(ChatSession::getUserLowId, Math.min(a, b))
                .eq(ChatSession::getUserHighId, Math.max(a, b)));
    }

    private static String summary(int type, String content) {
        if (type == 2) {
            return "[图片]";
        }
        if (type == 3) {
            return "[商品]";
        }
        return content.length() > 255 ? content.substring(0, 255) : content;
    }

    private List<ChatSessionVO> toVOs(List<ChatSession> list, Long me) {
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Goods> goods = goodsMapper.selectByIdsIncludingDeleted(
                        list.stream().map(ChatSession::getGoodsId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Goods::getId, Function.identity()));
        Set<Long> peers = list.stream().map(s -> me.equals(s.getInitiatorId()) ? s.getPeerId() : s.getInitiatorId())
                .collect(Collectors.toSet());
        Map<Long, SysUser> users = userMapper.selectBatchIds(peers).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<ChatSessionVO> result = new ArrayList<>();
        for (ChatSession s : list) {
            boolean initiator = me.equals(s.getInitiatorId());
            ChatSessionVO vo = new ChatSessionVO();
            vo.setId(s.getId());
            vo.setGoodsId(s.getGoodsId());
            Goods g = goods.get(s.getGoodsId());
            if (g != null) {
                vo.setGoodsTitle(g.getTitle());
                vo.setGoodsCover(g.getCoverUrl());
            }
            vo.setPeer(UserBriefVO.of(users.get(initiator ? s.getPeerId() : s.getInitiatorId())));
            vo.setLastContent(s.getLastContent());
            vo.setLastTime(s.getLastTime());
            Integer unread = initiator ? s.getInitiatorUnread() : s.getPeerUnread();
            vo.setUnreadCount(unread == null ? 0 : unread);
            result.add(vo);
        }
        return result;
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
