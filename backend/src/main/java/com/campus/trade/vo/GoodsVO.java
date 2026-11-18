package com.campus.trade.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品 VO（规范第 11 节）。列表接口不返回 description、images；auditReason 仅本人/管理员可见。
 * 推荐模块等可直接复用，用 {@code GoodsService#toListVOs} 批量组装。
 */
@Data
@Schema(description = "商品")
public class GoodsVO {
    private Long id;
    private String title;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String condition;
    private String tradeMethod;
    @Schema(description = "交易地点，如「东区食堂门口」；未填写为 null")
    private String tradeLocation;
    private String status;
    private Long categoryId;
    private String categoryName;
    private String coverUrl;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> images;
    private Integer viewCount;
    private Integer favoriteCount;
    private boolean favorited;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String auditReason;
    private SellerVO seller;
    /** 推荐理由文案，仅推荐接口（/recommend/home、/recommend/similar）返回，其余接口不输出 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "推荐理由，如「你收藏过类似的 iPad」「你关注了数码电子」「本周热门」；仅推荐接口返回")
    private String reason;
    /** 推荐理由类型：CF / INTEREST / HOT，仅推荐接口返回 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "推荐理由类型：CF 浏览或收藏过的相似商品，INTEREST 匹配兴趣分类，HOT 热度推荐；仅推荐接口返回",
            allowableValues = {"CF", "INTEREST", "HOT"})
    private String reasonType;
    private LocalDateTime createTime;
}
