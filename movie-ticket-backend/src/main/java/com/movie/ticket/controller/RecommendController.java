package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.service.RecommendService;
import com.movie.ticket.vo.MovieVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    @GetMapping("/hot")
    public Result<List<MovieVO>> getHotMovies(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<MovieVO> movies = recommendService.getHotMovies(limit);
        return Result.success(movies);
    }

    @GetMapping("/personal")
    @PreAuthorize("isAuthenticated()")
    public Result<List<MovieVO>> getRecommendedMovies(
            @RequestParam(defaultValue = "10") Integer limit) {
        Long userId = UserContext.getUserId();
        List<MovieVO> movies = recommendService.getRecommendedMovies(userId, limit);
        return Result.success(movies);
    }

    @GetMapping("/similar/{movieId}")
    public Result<List<MovieVO>> getSimilarMovies(
            @PathVariable Long movieId,
            @RequestParam(defaultValue = "6") Integer limit) {
        List<MovieVO> movies = recommendService.getSimilarMovies(movieId, limit);
        return Result.success(movies);
    }
}
