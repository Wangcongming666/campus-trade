package com.campus.trade.controller;

import com.campus.trade.common.PageQuery;
import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.dto.ChatSessionCreateDTO;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.service.ChatPresenceService;
import com.campus.trade.service.ChatService;
import com.campus.trade.vo.ChatMessageVO;
import com.campus.trade.vo.ChatSessionVO;
import com.campus.trade.vo.ReadReceiptVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "聊天（HTTP）", description = "实时消息走 STOMP：/ws，SEND /app/chat.send，订阅 /user/queue/messages、/user/queue/read、/user/queue/errors、/user/queue/presence、/user/queue/typing；SEND /app/chat.typing")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatPresenceService presenceService;

    @Operation(summary = "发起/获取会话（同一商品同一对用户唯一）")
    @PostMapping("/sessions")
    public Result<ChatSessionVO> open(@Valid @RequestBody ChatSessionCreateDTO dto) {
        return Result.ok(chatService.openSession(SecurityUtils.requireUserId(), dto.getGoodsId(), dto.getToUserId()));
    }

    @Operation(summary = "我的会话列表（按最近消息时间倒序）")
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> sessions() {
        return Result.ok(chatService.listSessions(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "会话历史消息（pageNum=1 为最新一页，页内按时间升序）")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<PageResult<ChatMessageVO>> messages(@PathVariable Long sessionId, @ParameterObject PageQuery pq) {
        return Result.ok(chatService.messages(SecurityUtils.requireUserId(), sessionId, pq));
    }

    @Operation(summary = "标记会话已读（向对方 /user/queue/read 推送 {sessionId, readerId}）")
    @PatchMapping("/sessions/{sessionId}/read")
    public Result<ReadReceiptVO> read(@PathVariable Long sessionId) {
        return Result.ok(chatService.markRead(SecurityUtils.requireUserId(), sessionId));
    }

    @Operation(summary = "批量查询在线状态 userIds=1,2（最多 100 个），返回 {userId: true/false}")
    @GetMapping("/online")
    public Result<java.util.Map<Long, Boolean>> online(@RequestParam("userIds") List<Long> userIds) {
        return Result.ok(presenceService.online(userIds));
    }
}
