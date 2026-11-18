package com.campus.trade.vo;

import com.campus.trade.entity.PayOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "模拟支付单")
public class PayOrderVO {
    private Long id;
    @Schema(description = "支付单号，确认/查询支付时使用")
    private String payNo;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    @Schema(description = "WAITING 待支付 / PAID 已支付 / CLOSED 已关闭", allowableValues = {"WAITING", "PAID", "CLOSED"})
    private String status;
    @Schema(description = "过期时间（下单时间 + 15 分钟）")
    private LocalDateTime expireTime;
    @Schema(description = "距过期剩余秒数，非 WAITING 为 0")
    private long remainSeconds;
    private LocalDateTime payTime;
    @Schema(description = "订单当前状态 PENDING / PAID / COMPLETED / CANCELLED")
    private String orderStatus;
    private LocalDateTime createTime;

    public static PayOrderVO of(PayOrder p, String orderStatus) {
        PayOrderVO vo = new PayOrderVO();
        vo.setId(p.getId());
        vo.setPayNo(p.getPayNo());
        vo.setOrderId(p.getOrderId());
        vo.setUserId(p.getUserId());
        vo.setAmount(p.getAmount());
        vo.setStatus(p.getStatus());
        vo.setExpireTime(p.getExpireTime());
        if ("WAITING".equals(p.getStatus()) && p.getExpireTime() != null) {
            vo.setRemainSeconds(Math.max(0, java.time.Duration.between(LocalDateTime.now(), p.getExpireTime()).getSeconds()));
        }
        vo.setPayTime(p.getPayTime());
        vo.setOrderStatus(orderStatus);
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }
}
