package com.campus.trade.controller;

import com.campus.trade.common.BizException;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.common.Result;
import com.campus.trade.dto.ChatSendDTO;
import com.campus.trade.security.StompPrincipal;
import com.campus.trade.service.ChatService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Set;

/**
 * STOMP 消息入口：SEND /app/chat.send {toUserId, goodsId, content, type}。
 * 成功后消息推送到收发双方 /user/queue/messages；失败时错误以 {code,msg} 推送到发送方 /user/queue/errors。
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;
    private final Validator validator;

    @MessageMapping("/chat.send")
    public void send(@Payload ChatSendDTO dto, Principal principal) {
        Set<ConstraintViolation<ChatSendDTO>> v = validator.validate(dto);
        if (!v.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, v.iterator().next().getMessage());
        }
        chatService.send(userId(principal), dto);
    }

    @MessageExceptionHandler(BizException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Result<Void> onBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Result<Void> onError(Exception e) {
        log.warn("stomp message failed", e);
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "消息格式错误");
    }

    private static Long userId(Principal p) {
        if (p instanceof StompPrincipal sp) {
            return sp.loginUser().userId();
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }
}
