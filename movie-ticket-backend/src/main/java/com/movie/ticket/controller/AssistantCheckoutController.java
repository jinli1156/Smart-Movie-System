package com.movie.ticket.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.movie.ticket.common.Result;
import com.movie.ticket.service.AssistantCheckoutService;
import com.movie.ticket.security.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 助手订单确认卡接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/assistant/checkout")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AssistantCheckoutController {
    private final AssistantCheckoutService checkout;
    public record Prepare(@NotNull JsonNode criteria, @Size(max=5) List<@NotBlank String> seats) {}

    /** 生成五分钟有效的订单确认卡；此时不锁座，确认时仍需重新核验。 */
    @PostMapping("/prepare")
    public Result<JsonNode> prepare(@Valid @RequestBody Prepare request) {
        return Result.success(checkout.prepare(UserContext.getUserId(), request.criteria(), request.seats()));
    }

    /** 校验本人确认卡及有效期，重新核验座位后幂等创建待支付订单。 */
    @PostMapping("/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable String id) {
        return Result.success(checkout.confirm(id));
    }
}
