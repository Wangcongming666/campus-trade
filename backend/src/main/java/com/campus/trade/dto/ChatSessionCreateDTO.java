package com.campus.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatSessionCreateDTO {
    @NotNull(message = "商品 ID 不能为空")
    private Long goodsId;
    @NotNull(message = "对方用户 ID 不能为空")
    private Long toUserId;
}
