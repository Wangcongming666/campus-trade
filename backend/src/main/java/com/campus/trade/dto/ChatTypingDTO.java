package com.campus.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** STOMP SEND /app/chat.typing 载荷：{sessionId}。 */
@Data
public class ChatTypingDTO {
    @NotNull(message = "sessionId 不能为空")
    private Long sessionId;
}
