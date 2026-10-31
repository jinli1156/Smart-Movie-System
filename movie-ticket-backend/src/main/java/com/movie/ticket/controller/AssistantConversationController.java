package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.service.AssistantConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 助手会话历史接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/assistant/conversations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AssistantConversationController {
    private final AssistantConversationService conversations;

    /** 分页查询本人的助手会话摘要。 */
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page) {
        return Result.success(conversations.list(UserContext.getUserId(), page));
    }
    /** 读取本人会话及消息，并支持向前加载历史轮次。 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable String id, @RequestParam(required = false) Long before) {
        return Result.success(conversations.detail(id, UserContext.getUserId(), before));
    }
    /** 校验会话归属后删除本人的会话记录。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        conversations.delete(id, UserContext.getUserId()); return Result.success();
    }
}
