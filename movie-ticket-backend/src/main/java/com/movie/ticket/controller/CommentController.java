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

@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<CommentVO> create(@Valid @RequestBody CommentCreateDTO dto) {
        CommentVO vo = commentService.createComment(dto);
        return Result.success("评价成功", vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<CommentVO> update(
            @PathVariable Long id,
            @Valid @RequestBody CommentUpdateDTO dto) {
        return Result.success("评价修改成功", commentService.updateComment(id, dto));
    }

    @GetMapping("/movie/{movieId}")
    public Result<List<CommentVO>> listByMovie(@PathVariable Long movieId) {
        return Result.success(commentService.listByMovie(movieId));
    }

    @GetMapping("/eligibility")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> checkEligibility(@RequestParam Long movieId) {
        return Result.success(commentService.checkEligibility(movieId));
    }
}
