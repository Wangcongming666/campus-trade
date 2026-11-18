package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("goods_image")
public class GoodsImage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long goodsId;
    private String url;
    private String name;
    private Long size;
    private Integer sortNo;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
