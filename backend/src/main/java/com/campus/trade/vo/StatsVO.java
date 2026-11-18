package com.campus.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "后台统计")
public class StatsVO {
    @Schema(description = "用户总数")
    private long userCount;
    @Schema(description = "商品总数（不含已删除）")
    private long goodsCount;
    @Schema(description = "订单总数")
    private long orderCount;
    @Schema(description = "成交额：COMPLETED 订单 price 之和")
    private BigDecimal gmv;
    @Schema(description = "待审核商品数")
    private long pendingGoodsAudit;
    @Schema(description = "待审核学生认证数")
    private long pendingStudentVerify;
    @Schema(description = "最近 days 天趋势（含今天，升序，无数据补 0）")
    private List<TrendItem> trend;
    @Schema(description = "未删除商品按一级分类分布")
    private List<CategoryShare> categoryShares;
    @Schema(description = "浏览量 Top10（未删除商品）")
    private List<HotGoods> hotGoods;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendItem {
        @Schema(description = "yyyy-MM-dd")
        private String date;
        private long newUsers;
        private long newGoods;
        private long newOrders;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryShare {
        private Long categoryId;
        private String categoryName;
        private long count;
    }

    @Data
    public static class HotGoods {
        private Long id;
        private String title;
        private String coverUrl;
        private BigDecimal price;
        private Integer viewCount;
        private Integer favoriteCount;
    }
}
