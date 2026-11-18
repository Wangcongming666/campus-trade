package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trade.common.BizException;
import com.campus.trade.common.Constants.GoodsStatus;
import com.campus.trade.common.Constants.OrderStatus;
import com.campus.trade.common.Constants.PayStatus;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.entity.PayOrder;
import com.campus.trade.entity.TradeOrder;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.PayOrderMapper;
import com.campus.trade.mapper.TradeOrderMapper;
import com.campus.trade.security.LoginUser;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.vo.PayOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 模拟支付：
 * <ul>
 *   <li>下单时（同一事务）生成 WAITING 支付单，过期时间 = 下单时间 + timeout（默认 15 分钟）；
 *       POST /orders/{id}/pay 返回该订单唯一的 WAITING 支付单（数据库生成列 + 唯一索引兜底，重复请求返回同一张）。</li>
 *   <li>确认支付：先条件更新订单 PENDING→PAID（锁订单行），再条件更新支付单 WAITING→PAID，任一失败整体回滚；
 *       已支付的支付单重复确认直接返回（幂等）。</li>
 *   <li>超时：@Scheduled 每分钟扫描下单满 timeout 仍 PENDING 的订单，条件更新为 CANCELLED（同样先锁订单行），
 *       成功后关闭 WAITING 支付单、商品 RESERVED→ON_SHELF。确认支付与超时关单争同一订单行，只有一个能成功。</li>
 * </ul>
 */
@Slf4j
@Service
public class PayService {

    public static final String TIMEOUT_REASON = "超时未支付，系统自动取消";
    private static final int SCAN_BATCH = 200;

    private final TradeOrderMapper orderMapper;
    private final PayOrderMapper payMapper;
    private final GoodsMapper goodsMapper;
    private final TransactionTemplate tx;
    private final TransactionTemplate newTx;
    private final long timeoutMinutes;

    public PayService(TradeOrderMapper orderMapper, PayOrderMapper payMapper, GoodsMapper goodsMapper,
                      PlatformTransactionManager tm,
                      @Value("${campus.pay.timeout-minutes:15}") long timeoutMinutes) {
        this.orderMapper = orderMapper;
        this.payMapper = payMapper;
        this.goodsMapper = goodsMapper;
        this.tx = new TransactionTemplate(tm);
        this.newTx = new TransactionTemplate(tm);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.timeoutMinutes = timeoutMinutes;
    }

    public long timeoutMinutes() {
        return timeoutMinutes;
    }

    /** 下单后在同一事务内调用：生成 WAITING 支付单。 */
    public PayOrder createForNewOrder(TradeOrder o) {
        PayOrder p = newPayOrder(o);
        payMapper.insert(p);
        return p;
    }

    /** POST /orders/{id}/pay：买家创建或获取订单唯一的 WAITING 支付单；已支付订单返回已支付的支付单。 */
    public PayOrderVO createOrGet(Long orderId) {
        Long uid = SecurityUtils.requireUserId();
        TradeOrder o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        requireBuyer(o, uid);
        if (!OrderStatus.PENDING.equals(o.getStatus())) {
            if (OrderStatus.PAID.equals(o.getStatus()) || OrderStatus.COMPLETED.equals(o.getStatus())) {
                PayOrder paid = latest(orderId, PayStatus.PAID);
                if (paid != null) {
                    return PayOrderVO.of(paid, o.getStatus());
                }
            }
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        }
        if (isTimedOut(o)) {
            cancelIfTimeout(orderId);
            throw new BizException(ErrorCode.PAY_ORDER_EXPIRED);
        }
        PayOrder p = latest(orderId, PayStatus.WAITING);
        if (p == null) {
            p = newPayOrder(o);
            try {
                payMapper.insert(p);
                p = payMapper.selectById(p.getId());
            } catch (DuplicateKeyException e) {
                p = latest(orderId, PayStatus.WAITING); // 并发创建：返回已存在的那张
            }
        }
        return PayOrderVO.of(p, o.getStatus());
    }

    /** GET /pay/{payNo}：买家本人或管理员。 */
    public PayOrderVO get(String payNo) {
        LoginUser u = SecurityUtils.requireUser();
        PayOrder p = requirePay(payNo);
        if (!u.isAdmin() && !u.userId().equals(p.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        TradeOrder o = orderMapper.selectById(p.getOrderId());
        return PayOrderVO.of(p, o == null ? null : o.getStatus());
    }

    /** POST /pay/{payNo}/confirm：确认模拟支付，订单 PENDING→PAID；重复确认幂等。 */
    public PayOrderVO confirm(String payNo) {
        Long uid = SecurityUtils.requireUserId();
        PayOrder p = requirePay(payNo);
        if (!uid.equals(p.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (PayStatus.PAID.equals(p.getStatus())) {
            return view(p.getId());
        }
        if (PayStatus.CLOSED.equals(p.getStatus())) {
            throw new BizException(ErrorCode.PAY_ORDER_CLOSED);
        }
        if (p.getExpireTime() != null && !LocalDateTime.now().isBefore(p.getExpireTime())) {
            cancelIfTimeout(p.getOrderId());
            throw new BizException(ErrorCode.PAY_ORDER_EXPIRED);
        }
        Long payId = p.getId();
        Long orderId = p.getOrderId();
        Boolean done = tx.execute(status -> {
            // 先锁订单行（与超时关单、取消订单使用同一加锁顺序，避免死锁）
            if (orderMapper.casStatus(orderId, Set.of(OrderStatus.PENDING), OrderStatus.PAID, null) == 0) {
                return false;
            }
            if (payMapper.markPaid(payId) == 0) {
                throw new BizException(ErrorCode.PAY_ORDER_CLOSED); // 回滚订单状态
            }
            return true;
        });
        if (Boolean.TRUE.equals(done)) {
            return view(payId);
        }
        // 订单已不是 PENDING：并发确认已成功则幂等返回，否则说明已取消/超时
        PayOrder now = payMapper.selectById(payId);
        if (now != null && PayStatus.PAID.equals(now.getStatus())) {
            return view(payId);
        }
        TradeOrder o = orderMapper.selectById(orderId);
        if (o != null && OrderStatus.CANCELLED.equals(o.getStatus())) {
            throw new BizException(TIMEOUT_REASON.equals(o.getCancelReason())
                    ? ErrorCode.PAY_ORDER_EXPIRED : ErrorCode.PAY_ORDER_CLOSED);
        }
        throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
    }

    /** 旧接口 PATCH /orders/{id}/status action=PAY 的兼容：订单已置 PAID 后，同步把 WAITING 支付单置为 PAID。 */
    public void onOrderPaid(Long orderId) {
        payMapper.markPaidByOrder(orderId);
    }

    /** 订单取消/退款后关闭其 WAITING 支付单。 */
    public void onOrderCancelled(Long orderId) {
        payMapper.closeByOrder(orderId);
    }

    public boolean isTimedOut(TradeOrder o) {
        return o.getCreateTime() != null && !LocalDateTime.now().isBefore(o.getCreateTime().plusMinutes(timeoutMinutes));
    }

    /**
     * 单个订单超时关单（独立新事务）：条件更新 PENDING→CANCELLED（且下单时间已满 timeout），
     * 成功后关闭 WAITING 支付单并把商品 RESERVED→ON_SHELF。返回是否由本次调用关单。
     */
    public boolean cancelIfTimeout(Long orderId) {
        Boolean r = newTx.execute(status -> {
            TradeOrder o = orderMapper.selectById(orderId);
            if (o == null) {
                return false;
            }
            LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
            if (orderMapper.cancelIfTimeout(orderId, deadline, TIMEOUT_REASON) == 0) {
                return false;
            }
            payMapper.closeByOrder(orderId);
            if (goodsMapper.casStatus(o.getGoodsId(), GoodsStatus.RESERVED, GoodsStatus.ON_SHELF) == 0) {
                log.warn("order {} timed out but goods {} was not RESERVED", orderId, o.getGoodsId());
            }
            return true;
        });
        return Boolean.TRUE.equals(r);
    }

    /** 定时扫描超时未支付订单（默认每分钟一次；campus.pay.scan-enabled=false 可关闭）。返回本次关单数量。 */
    @Scheduled(fixedDelayString = "${campus.pay.scan-interval-ms:60000}", initialDelayString = "${campus.pay.scan-initial-delay-ms:10000}")
    public void scheduledScan() {
        if (!scanEnabled) {
            return;
        }
        int n = scanTimeoutOrders();
        if (n > 0) {
            log.info("[pay] auto-cancelled {} timeout order(s)", n);
        }
    }

    @Value("${campus.pay.scan-enabled:true}")
    private boolean scanEnabled;

    public int scanTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        int count = 0;
        for (Long id : payMapper.selectTimeoutOrderIds(deadline, SCAN_BATCH)) {
            try {
                if (cancelIfTimeout(id)) {
                    count++;
                }
            } catch (Exception e) {
                log.warn("[pay] timeout cancel failed for order {}: {}", id, e.getMessage());
            }
        }
        return count;
    }

    /** 批量查询订单的 WAITING 支付单（订单列表展示 payNo / 倒计时）。 */
    public Map<Long, PayOrder> waitingByOrderIds(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PayOrder> m = new HashMap<>();
        payMapper.selectList(new LambdaQueryWrapper<PayOrder>().in(PayOrder::getOrderId, orderIds)
                .eq(PayOrder::getStatus, PayStatus.WAITING)).forEach(p -> m.put(p.getOrderId(), p));
        return m;
    }

    private PayOrderVO view(Long payId) {
        PayOrder p = payMapper.selectById(payId);
        TradeOrder o = orderMapper.selectById(p.getOrderId());
        return PayOrderVO.of(p, o == null ? null : o.getStatus());
    }

    private PayOrder newPayOrder(TradeOrder o) {
        LocalDateTime created = o.getCreateTime() == null ? LocalDateTime.now() : o.getCreateTime();
        PayOrder p = new PayOrder();
        p.setPayNo("P" + com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr());
        p.setOrderId(o.getId());
        p.setUserId(o.getBuyerId());
        p.setAmount(o.getPrice());
        p.setStatus(PayStatus.WAITING);
        p.setExpireTime(created.withNano(0).plusMinutes(timeoutMinutes));
        p.setDeleted(0);
        return p;
    }

    private PayOrder latest(Long orderId, String status) {
        return payMapper.selectOne(new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOrderId, orderId)
                .eq(PayOrder::getStatus, status).orderByDesc(PayOrder::getCreateTime).orderByDesc(PayOrder::getId)
                .last("LIMIT 1"));
    }

    private PayOrder requirePay(String payNo) {
        PayOrder p = payNo == null ? null : payMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getPayNo, payNo));
        if (p == null) {
            throw new BizException(ErrorCode.PAY_ORDER_NOT_FOUND);
        }
        return p;
    }

    private static void requireBuyer(TradeOrder o, Long uid) {
        if (uid.equals(o.getBuyerId())) {
            return;
        }
        throw new BizException(uid.equals(o.getSellerId()) ? ErrorCode.ORDER_ACTION_FORBIDDEN : ErrorCode.FORBIDDEN);
    }
}
