package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.dto.CommentCreateDTO;
import com.movie.ticket.dto.CommentUpdateDTO;
import com.movie.ticket.service.CommentService;
import com.movie.ticket.vo.CommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 观影评价接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** 新增观影评价，由业务层执行参数及关联数据校验。 */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<CommentVO> create(@Valid @RequestBody CommentCreateDTO dto) {
        CommentVO vo = commentService.createComment(dto);
        return Result.success("评价成功", vo);
    }

    /** 更新观影评价，由业务层校验当前操作权限与业务条件。 */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<CommentVO> update(
            @PathVariable Long id,
            @Valid @RequestBody CommentUpdateDTO dto) {
        return Result.success("评价修改成功", commentService.updateComment(id, dto));
    }

    /** 读取指定影片的用户评价列表。 */
    @GetMapping("/movie/{movieId}")
    public Result<List<CommentVO>> listByMovie(@PathVariable Long movieId) {
        return Result.success(commentService.listByMovie(movieId));
    }

    /** 核验用户是否已完成该影片的观影，以及是否已有可编辑评价。 */
    @GetMapping("/eligibility")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> checkEligibility(@RequestParam Long movieId) {
        return Result.success(commentService.checkEligibility(movieId));
    }
}
