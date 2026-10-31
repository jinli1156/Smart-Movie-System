package com.movie.ticket.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.movie.ticket.common.Result;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.service.SchedulingSavedDraftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

/** 排片草稿暂存接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/statistics/scheduling/saved-drafts")
@PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
@RequiredArgsConstructor
public class SchedulingSavedDraftController {
    private final SchedulingSavedDraftService service;
    public record SaveRequest(@NotNull Long cinemaId, @NotNull LocalDate targetDate, @NotNull JsonNode content) {}

    /** 读取本人可访问的排片草稿摘要。 */
    @GetMapping
    public Result<List<SchedulingSavedDraftService.Summary>> list() {
        return Result.success(service.list(UserContext.getUserId()));
    }
    /** 校验本人归属及影院权限后读取草稿内容。 */
    @GetMapping("/{id}")
    public Result<SchedulingSavedDraftService.Saved> get(@PathVariable String id) {
        return Result.success(service.get(UserContext.getUserId(), id));
    }
    /** 暂存完整排片草稿及快照，供稍后恢复；不会发布排片。 */
    @PutMapping
    public Result<SchedulingSavedDraftService.Saved> save(@Valid @RequestBody SaveRequest request) {
        return Result.success(service.save(UserContext.getUserId(), request.cinemaId(), request.targetDate(), request.content()));
    }
    /** 校验本人归属与影院权限后删除暂存草稿。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(UserContext.getUserId(), id);
        return Result.success();
    }
}
