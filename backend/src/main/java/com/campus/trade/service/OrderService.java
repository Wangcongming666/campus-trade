package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trade.common.*;
import com.campus.trade.common.Constants.GoodsStatus;
import com.campus.trade.common.Constants.OrderAction;
import com.campus.trade.common.Constants.OrderStatus;
import com.campus.trade.dto.OrderActionDTO;
import com.campus.trade.dto.OrderCreateDTO;
import com.campus.trade.dto.OrderQueryDTO;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Review;
import com.campus.trade.entity.SysUser;
import com.campus.trade.entity.TradeOrder;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.ReviewMapper;
import com.campus.trade.mapper.SysUserMapper;
import com.campus.trade.mapper.TradeOrderMapper;
import com.campus.trade.security.LoginUser;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.vo.OrderVO;
import com.campus.trade.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单与状态机（无 DELIVER）：
 * PENDING -PAY(买家)→ PAID -CONFIRM(买家)→ COMPLETED；PENDING/PAID -CANCEL|REFUND(买卖双方)→ CANCELLED。
 * 商品联动：下单 ON_SHELF→RESERVED；取消/退款 RESERVED→ON_SHELF；完成 RESERVED→SOLD。所有状态变更均为条件更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<String> ACTIVE = Set.of(OrderStatus.PENDING, OrderStatus.PAID);

    private final TradeOrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final SysUserMapper userMapper;
    private final ReviewMapper reviewMapper;
    private final UserBehaviorService behaviorService;

    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(OrderCreateDTO dto) {
        Long uid = SecurityUtils.requireUserId();
        Goods g = goodsMapper.selectById(dto.getGoodsId());
        if (g == null) {
            throw new BizException(ErrorCode.GOODS_NOT_FOUND);
        }
        if (uid.equals(g.getSellerId())) {
            throw new BizException(ErrorCode.ORDER_SELF_GOODS);
        }
        if (!GoodsStatus.ON_SHELF.equals(g.getStatus())) {
            throw new BizException(switch (g.getStatus()) {
                case GoodsStatus.RESERVED -> ErrorCode.GOODS_RESERVED;
                case GoodsStatus.OFF_SHELF, GoodsStatus.SOLD -> ErrorCode.GOODS_OFF_SHELF;
                default -> ErrorCode.GOODS_NOT_FOUND;
            });
        }
        if (goodsMapper.casStatus(g.getId(), GoodsStatus.ON_SHELF, GoodsStatus.RESERVED) == 0) {
            throw new BizException(ErrorCode.GOODS_RESERVED);
        }
        TradeOrder o = new TradeOrder();
        o.setGoodsId(g.getId());
        o.setBuyerId(uid);
        o.setSellerId(g.getSellerId());
        o.setPrice(g.getPrice());
        o.setRemark(dto.getRemark());
        o.setStatus(OrderStatus.PENDING);
        orderMapper.insert(o);
        Long goodsId = g.getId();
        afterCommit(() -> behaviorService.recordOrder(uid, goodsId));
        return toVOs(List.of(orderMapper.selectById(o.getId())), uid).get(0);
    }

    public PageResult<OrderVO> page(OrderQueryDTO q) {
        Long uid = SecurityUtils.requireUserId();
        boolean seller = "seller".equals(q.getRole());
        LambdaQueryWrapper<TradeOrder> w = new LambdaQueryWrapper<TradeOrder>()
                .eq(seller ? TradeOrder::getSellerId : TradeOrder::getBuyerId, uid)
                .eq(q.getStatus() != null && !q.getStatus().isBlank(), TradeOrder::getStatus, q.getStatus())
                .orderByDesc(TradeOrder::getCreateTime).orderByDesc(TradeOrder::getId);
        Page<TradeOrder> page = orderMapper.selectPage(q.toPage(), w);
        return PageResult.of(page, toVOs(page.getRecords(), uid));
    }

    public OrderVO detail(Long id) {
        LoginUser u = SecurityUtils.requireUser();
        TradeOrder o = requireVisible(id, u);
        return toVOs(List.of(o), u.userId()).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderVO changeStatus(Long id, OrderActionDTO dto) {
        LoginUser u = SecurityUtils.requireUser();
        TradeOrder o = orderMapper.selectById(id);
        if (o == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        boolean buyer = u.userId().equals(o.getBuyerId());
        boolean seller = u.userId().equals(o.getSellerId());
        if (!buyer && !seller) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        String action = dto.getAction();
        switch (action) {
            case OrderAction.PAY -> {
                requireRole(buyer);
                transit(o.getId(), Set.of(OrderStatus.PENDING), OrderStatus.PAID, null);
            }
            case OrderAction.CONFIRM -> {
                requireRole(buyer);
                transit(o.getId(), Set.of(OrderStatus.PAID), OrderStatus.COMPLETED, null);
                if (goodsMapper.casStatus(o.getGoodsId(), GoodsStatus.RESERVED, GoodsStatus.SOLD) == 0) {
                    log.warn("order {} completed but goods {} was not RESERVED", o.getId(), o.getGoodsId());
                }
            }
            case OrderAction.CANCEL, OrderAction.REFUND -> {
                String reason = dto.getReason() == null || dto.getReason().isBlank()
                        ? (OrderAction.REFUND.equals(action) ? "退款" : "取消订单") : dto.getReason().trim();
                transit(o.getId(), ACTIVE, OrderStatus.CANCELLED, reason);
                if (goodsMapper.casStatus(o.getGoodsId(), GoodsStatus.RESERVED, GoodsStatus.ON_SHELF) == 0) {
                    log.warn("order {} cancelled but goods {} was not RESERVED", o.getId(), o.getGoodsId());
                }
            }
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "action 取值为 PAY/CONFIRM/CANCEL/REFUND");
        }
        return toVOs(List.of(orderMapper.selectById(id)), u.userId()).get(0);
    }

    /** 订单可见性：买家、卖家、管理员。 */
    public TradeOrder requireVisible(Long id, LoginUser u) {
        TradeOrder o = orderMapper.selectById(id);
        if (o == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!u.isAdmin() && !u.userId().equals(o.getBuyerId()) && !u.userId().equals(o.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return o;
    }

    private void transit(Long id, Set<String> from, String to, String reason) {
        if (orderMapper.casStatus(id, from, to, reason) == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        }
    }

    private static void requireRole(boolean ok) {
        if (!ok) {
            throw new BizException(ErrorCode.ORDER_ACTION_FORBIDDEN);
        }
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

    public List<OrderVO> toVOs(List<TradeOrder> list, Long viewerId) {
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> goodsIds = list.stream().map(TradeOrder::getGoodsId).collect(Collectors.toSet());
        // 订单需展示已逻辑删除商品的标题/封面，因此绕过逻辑删除过滤
        Map<Long, Goods> goods = goodsMapper.selectByIdsIncludingDeleted(goodsIds)
                .stream().collect(Collectors.toMap(Goods::getId, Function.identity()));
        Set<Long> userIds = new HashSet<>();
        list.forEach(o -> {
            userIds.add(o.getBuyerId());
            userIds.add(o.getSellerId());
        });
        Map<Long, SysUser> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Set<Long> reviewed = viewerId == null ? Set.of() : reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                        .eq(Review::getFromUserId, viewerId)
                        .in(Review::getOrderId, list.stream().map(TradeOrder::getId).toList()))
                .stream().map(Review::getOrderId).collect(Collectors.toSet());
        List<OrderVO> result = new ArrayList<>();
        for (TradeOrder o : list) {
            OrderVO vo = new OrderVO();
            vo.setId(o.getId());
            vo.setGoodsId(o.getGoodsId());
            Goods g = goods.get(o.getGoodsId());
            if (g != null) {
                vo.setGoodsTitle(g.getTitle());
                vo.setGoodsCover(g.getCoverUrl());
            }
            vo.setBuyer(UserBriefVO.of(users.get(o.getBuyerId())));
            vo.setSeller(UserBriefVO.of(users.get(o.getSellerId())));
            vo.setPrice(o.getPrice());
            vo.setRemark(o.getRemark());
            vo.setStatus(o.getStatus());
            vo.setCancelReason(o.getCancelReason());
            vo.setPayTime(o.getPayTime());
            vo.setCompleteTime(o.getCompleteTime());
            vo.setCreateTime(o.getCreateTime());
            vo.setReviewed(reviewed.contains(o.getId()));
            result.add(vo);
        }
        return result;
    }
}
