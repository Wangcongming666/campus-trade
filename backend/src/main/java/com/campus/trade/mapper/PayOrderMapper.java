package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.PayOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    /** 条件更新 WAITING→PAID（防并发、幂等），返回影响行数。 */
    @Update("UPDATE pay_order SET status = 'PAID', pay_time = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND status = 'WAITING' AND deleted = 0")
    int markPaid(@Param("id") Long id);

    /** 订单下所有 WAITING 支付单置为 PAID（兼容旧 PATCH action=PAY）。 */
    @Update("UPDATE pay_order SET status = 'PAID', pay_time = NOW(), update_time = NOW() "
            + "WHERE order_id = #{orderId} AND status = 'WAITING' AND deleted = 0")
    int markPaidByOrder(@Param("orderId") Long orderId);

    /** 订单下所有 WAITING 支付单置为 CLOSED（取消、超时）。 */
    @Update("UPDATE pay_order SET status = 'CLOSED', update_time = NOW() "
            + "WHERE order_id = #{orderId} AND status = 'WAITING' AND deleted = 0")
    int closeByOrder(@Param("orderId") Long orderId);

    /** 下单时间早于 deadline 仍为 PENDING 的订单 ID（超时扫描）。 */
    @Select("SELECT id FROM trade_order WHERE status = 'PENDING' AND create_time < #{deadline} "
            + "ORDER BY create_time LIMIT #{limit}")
    List<Long> selectTimeoutOrderIds(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);
}
