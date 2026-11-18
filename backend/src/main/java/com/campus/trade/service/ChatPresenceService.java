package com.campus.trade.service;

import com.campus.trade.common.BizException;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.entity.ChatSession;
import com.campus.trade.mapper.ChatSessionMapper;
import com.campus.trade.vo.PresenceVO;
import com.campus.trade.vo.TypingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天在线状态与「正在输入」。
 * <p>在线状态存 Redis ZSET {@code chat:online:{uid}}：member = STOMP 会话 ID，score = 最近心跳时间（毫秒）。
 * 一个用户多个标签页/设备对应多个 member；有任一 member 的心跳在 ttl 内即视为在线。
 * 本实例持有的连接每 heartbeat-ms（默认 60s）刷新一次 score，key 的过期时间为 2×ttl；
 * 实例异常退出（未触发断开事件）时，残留 member 在 ttl（默认 180s）后自动视为离线并被清理。</p>
 * <p>用户由离线变为在线、或最后一个连接断开时，向所有与其有会话的对方推送 /user/queue/presence {userId, online}。</p>
 */
@Slf4j
@Service
public class ChatPresenceService {

    public static final String QUEUE_PRESENCE = "/queue/presence";
    public static final String QUEUE_TYPING = "/queue/typing";
    public static final int MAX_QUERY_IDS = 100;

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatSessionMapper sessionMapper;
    private final ChatService chatService;
    private final long ttlMs;
    /** 本实例持有的 STOMP 会话：sessionId → userId（心跳刷新、断开时兜底取 userId） */
    private final Map<String, Long> localSessions = new ConcurrentHashMap<>();

    public ChatPresenceService(StringRedisTemplate redis, SimpMessagingTemplate messagingTemplate,
                               ChatSessionMapper sessionMapper, ChatService chatService,
                               @Value("${campus.chat.presence-ttl-seconds:180}") long ttlSeconds) {
        this.redis = redis;
        this.messagingTemplate = messagingTemplate;
        this.sessionMapper = sessionMapper;
        this.chatService = chatService;
        this.ttlMs = ttlSeconds * 1000;
    }

    public static String key(Long userId) {
        return "chat:online:" + userId;
    }

    /** STOMP 连接建立（CONNECTED）。 */
    public void onConnect(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        boolean wasOnline = isOnline(userId);
        localSessions.put(sessionId, userId);
        touch(userId, sessionId);
        if (!wasOnline) {
            broadcast(userId, true);
        }
    }

    /** STOMP 连接断开（可能被多次触发，按 ZREM 结果去重）。 */
    public void onDisconnect(Long userId, String sessionId) {
        if (sessionId == null) {
            return;
        }
        Long local = localSessions.remove(sessionId);
        Long uid = userId != null ? userId : local;
        if (uid == null) {
            return;
        }
        Long removed = redis.opsForZSet().remove(key(uid), sessionId);
        if (removed != null && removed > 0 && !isOnline(uid)) {
            broadcast(uid, false);
        }
    }

    public boolean isOnline(Long userId) {
        Long n = redis.opsForZSet().count(key(userId), System.currentTimeMillis() - ttlMs, Double.POSITIVE_INFINITY);
        return n != null && n > 0;
    }

    /** GET /chat/online：批量查询，返回 {userId: true/false}（保持请求顺序，去重）。 */
    public Map<Long, Boolean> online(Collection<Long> userIds) {
        Map<Long, Boolean> m = new LinkedHashMap<>();
        if (userIds == null) {
            return m;
        }
        if (userIds.size() > MAX_QUERY_IDS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "userIds 最多 " + MAX_QUERY_IDS + " 个");
        }
        for (Long id : userIds) {
            if (id != null && !m.containsKey(id)) {
                m.put(id, isOnline(id));
            }
        }
        return m;
    }

    /** STOMP /app/chat.typing：校验发送方属于会话后转发给对方 /user/queue/typing {sessionId, userId}。不落库。 */
    public void typing(Long me, Long sessionId) {
        ChatSession s = chatService.requireParticipant(sessionId, me);
        Long peer = me.equals(s.getInitiatorId()) ? s.getPeerId() : s.getInitiatorId();
        messagingTemplate.convertAndSendToUser(String.valueOf(peer), QUEUE_TYPING, new TypingVO(sessionId, me));
    }

    /** 心跳兜底：刷新本实例连接的 score 与 key 过期时间，并清理超时 member。 */
    @Scheduled(fixedDelayString = "${campus.chat.presence-heartbeat-ms:60000}")
    public void heartbeat() {
        Set<Long> users = new HashSet<>();
        localSessions.forEach((sid, uid) -> {
            touch(uid, sid);
            users.add(uid);
        });
        long expired = System.currentTimeMillis() - ttlMs;
        users.forEach(uid -> redis.opsForZSet().removeRangeByScore(key(uid), Double.NEGATIVE_INFINITY, expired));
    }

    private void touch(Long userId, String sessionId) {
        String k = key(userId);
        redis.opsForZSet().add(k, sessionId, System.currentTimeMillis());
        redis.expire(k, Duration.ofMillis(ttlMs * 2));
    }

    private void broadcast(Long userId, boolean online) {
        try {
            PresenceVO vo = new PresenceVO(userId, online);
            for (Long peer : sessionMapper.selectPeerIds(userId)) {
                messagingTemplate.convertAndSendToUser(String.valueOf(peer), QUEUE_PRESENCE, vo);
            }
        } catch (Exception e) {
            log.warn("presence broadcast failed for user {}: {}", userId, e.getMessage());
        }
    }
}
