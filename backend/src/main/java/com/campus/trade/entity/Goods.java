package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("goods")
public class Goods {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long sellerId;
    private Long categoryId;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    /** condition 为 MySQL 保留字，需反引号 */
    @TableField("`condition`")
    private String condition;
    private String tradeMethod;
    /** 交易地点（选填），如「东区食堂门口」；编辑时允许清空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tradeLocation;
    private String status;
    private String coverUrl;
    private Integer viewCount;
    private Integer favoriteCount;
    /** 允许更新为 null（重新提交审核时清空审核意见） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String auditReason;
    private Long auditorId;
    private LocalDateTime auditTime;
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
