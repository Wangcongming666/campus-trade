package com.campus.trade.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** STOMP /app/chat.send 消息体 */
@Data
public class ChatSendDTO {
    @NotNull(message = "toUserId 不能为空")
    private Long toUserId;
    @NotNull(message = "goodsId 不能为空")
    private Long goodsId;
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息不超过 2000 字")
    private String content;
    /** 1 文本 2 图片 3 商品卡片，默认 1 */
    @Min(value = 1, message = "type 取值为 1/2/3")
    @Max(value = 3, message = "type 取值为 1/2/3")
    private Integer type;
}
