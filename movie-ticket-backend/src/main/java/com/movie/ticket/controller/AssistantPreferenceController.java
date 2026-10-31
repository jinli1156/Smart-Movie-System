package com.movie.ticket.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.movie.ticket.common.Result;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.service.AssistantConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 助手长期排除偏好接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/assistant/preferences")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AssistantPreferenceController {
    private final AssistantConversationService conversations;
    /** 读取本人长期保存的影片与类型排除偏好。 */
    /** 校验并保存本人的排除偏好。 */
    @GetMapping public Result<JsonNode> get() { return Result.success(conversations.preferences(UserContext.getUserId())); }
    @PutMapping public Result<JsonNode> save(@RequestBody JsonNode preferences) {
        return Result.success(conversations.savePreferences(UserContext.getUserId(), preferences));
    }
}
