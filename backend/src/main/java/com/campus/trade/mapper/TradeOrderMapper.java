package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    @org.apache.ibatis.annotations.Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, COUNT(*) AS c "
            + "FROM trade_order WHERE create_time >= #{since} GROUP BY d")
    java.util.List<java.util.Map<String, Object>> countByDay(@Param("since") java.time.LocalDateTime since);

    @org.apache.ibatis.annotations.Select("SELECT COALESCE(SUM(price), 0) FROM trade_order WHERE status = 'COMPLETED'")
    java.math.BigDecimal sumCompletedPrice();

    /**
     * 条件状态变更（防并发）：仅当当前状态属于 fromStatuses 时更新。
     * 同时按目标状态写入 pay_time / complete_time / cancel_reason。返回影响行数。
     */
    @Update({"<script>",
            "UPDATE trade_order SET status = #{toStatus}, update_time = NOW()",
            "<if test=\"toStatus == 'PAID'\">, pay_time = NOW()</if>",
            "<if test=\"toStatus == 'COMPLETED'\">, complete_time = NOW()</if>",
            "<if test=\"toStatus == 'CANCELLED'\">, cancel_reason = #{reason}</if>",
            "WHERE id = #{id} AND status IN",
            "<foreach collection='fromStatuses' item='s' open='(' separator=',' close=')'>#{s}</foreach>",
            "</script>"})
    int casStatus(@Param("id") Long id, @Param("fromStatuses") Collection<String> fromStatuses,
                  @Param("toStatus") String toStatus, @Param("reason") String reason);
}
