package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.dto.AssistantChatRequest;
import com.movie.ticket.service.MovieAssistantService;
import com.movie.ticket.service.AssistantConversationService;
import com.movie.ticket.vo.AssistantChatVO;
import com.movie.ticket.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 电影助手对话接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final MovieAssistantService movieAssistantService;
    private final AssistantConversationService conversations;
    private final Semaphore slots = new Semaphore(4);
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /** 返回助手模型配置及支持能力，供页面决定可用交互。 */
    @GetMapping("/capabilities")
    public Result<Map<String, Object>> capabilities() { return Result.success(movieAssistantService.capabilities()); }

    /** 执行一轮助手对话，返回回答、工具结果和后续建议。 */
    @PostMapping("/chat")
    public Result<AssistantChatVO> chat(@Valid @RequestBody AssistantChatRequest request) {
        acquire();
        try {
            var prepared = conversations.prepare(request, MovieAssistantService.currentUserId());
            AssistantChatVO result;
            try { result = prepared.cached() != null ? prepared.cached()
                    : movieAssistantService.converse(prepared.request(), prepared.userId(), (name, data) -> {}, () -> false); }
            catch (RuntimeException ex) { result = interrupted(); }
            conversations.finish(prepared, result);
            result.setConversationId(prepared.conversationId());
            return Result.success(result);
        } finally { slots.release(); }
    }

    /** 通过 SSE 推送助手进度、工具结果和回答片段。 */
    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter stream(@Valid @RequestBody AssistantChatRequest request, HttpServletResponse response) {
        acquire();
        Long userId = MovieAssistantService.currentUserId(); // Capture before leaving request thread.
        final AssistantConversationService.Prepared prepared;
        try { prepared = conversations.prepare(request, userId); }
        catch (RuntimeException ex) { slots.release(); throw ex; }
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        var emitter = new SseEmitter(180_000L);
        var cancelled = new AtomicBoolean();
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> { cancelled.set(true); emitter.complete(); });
        emitter.onError(error -> cancelled.set(true));
        try {
            executor.submit(() -> {
                try {
                    if (prepared.conversationId() != null) emitter.send(SseEmitter.event().name("conversation").data(Map.of("id", prepared.conversationId())));
                    emitter.send(SseEmitter.event().name("status").data(Map.of("message", "正在连接 DeepSeek…")));
                    var result = prepared.cached() != null ? prepared.cached() : movieAssistantService.converse(prepared.request(), userId, (name, data) -> {
                        if (cancelled.get()) throw new IllegalStateException("Cancelled");
                        try { emitter.send(SseEmitter.event().name(name).data(data)); }
                        catch (Exception ex) { cancelled.set(true); throw new IllegalStateException("Disconnected"); }
                    }, cancelled::get);
                    conversations.finish(prepared, result);
                    result.setConversationId(prepared.conversationId());
                    if (!cancelled.get()) emitter.send(SseEmitter.event().name("done").data(result));
                    emitter.complete();
                } catch (Exception ex) {
                    try { conversations.finish(prepared, interrupted()); } catch (Exception ignored) { /* Lease recovery handles a failed save. */ }
                    emitter.completeWithError(new IllegalStateException("Assistant stream ended"));
                }
                finally { slots.release(); }
            });
        } catch (RejectedExecutionException ex) {
            try { conversations.finish(prepared, interrupted()); } finally { slots.release(); }
            throw new BusinessException(503, "小助手暂时不可用");
        }
        return emitter;
    }

    private void acquire() {
        if (!slots.tryAcquire()) throw new BusinessException(429, "小助手繁忙，请稍后重试");
    }

    private AssistantChatVO interrupted() {
        return new AssistantChatVO("回答中断，请重新提问。", java.util.List.of(), "unavailable", java.util.List.of());
    }

    /** 释放助手流式响应使用的线程资源。 */
    @PreDestroy
    public void shutdown() { executor.shutdownNow(); }
}
