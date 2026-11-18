package com.campus.trade.price.mapper;

import com.campus.trade.price.service.PriceSampleRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 价格样本查询（只读）。
 * <ul>
 *   <li>已售（status='SOLD'）：优先取该商品最近一笔 COMPLETED 订单的 trade_order.price（成交价快照），
 *       没有订单记录时退回 goods.price；</li>
 *   <li>在售（status='ON_SHELF'）：goods.price 挂牌价，权重较低；</li>
 *   <li>均要求 deleted=0。</li>
 * </ul>
 */
public interface PriceSampleMapper {

    @Select("<script>"
            + "SELECT g.id AS goodsId, g.title AS title, g.`condition` AS `condition`, g.original_price AS originalPrice,"
            + " g.status AS status,"
            + " CASE WHEN g.status = 'SOLD' THEN COALESCE((SELECT o.price FROM trade_order o"
            + "   WHERE o.goods_id = g.id AND o.status = 'COMPLETED' ORDER BY o.complete_time DESC, o.id DESC LIMIT 1), g.price)"
            + " ELSE g.price END AS price"
            + " FROM goods g"
            + " WHERE g.deleted = 0 AND g.status IN ('SOLD', 'ON_SHELF')"
            + " AND g.category_id IN <foreach collection='categoryIds' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
            + " ORDER BY g.update_time DESC, g.id DESC LIMIT #{limit}"
            + "</script>")
    List<PriceSampleRow> selectSamples(@Param("categoryIds") Collection<Long> categoryIds, @Param("limit") int limit);

    /** 分类整体价格（任意状态，排除审核驳回/待审核），用于"无原价且无样本"时的中位数兜底。 */
    @Select("<script>"
            + "SELECT g.price FROM goods g WHERE g.deleted = 0 AND g.status NOT IN ('REJECTED', 'PENDING_AUDIT')"
            + " AND g.category_id IN <foreach collection='categoryIds' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
            + " ORDER BY g.update_time DESC LIMIT #{limit}"
            + "</script>")
    List<BigDecimal> selectCategoryPrices(@Param("categoryIds") Collection<Long> categoryIds, @Param("limit") int limit);
}
