package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.dto.ColdStartRequest;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.service.RecommendService;
import com.movie.ticket.vo.*;
import jakarta.validation.Valid;
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
    public Result<List<MovieRecommendationVO>> getRecommendedMovies(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        Long userId = UserContext.getUserId();
        List<MovieRecommendationVO> movies = recommendService.getRecommendedMovies(userId, limit, latitude, longitude);
        return Result.success(movies);
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public Result<RecommendationProfileVO> getProfile() {
        return Result.success(recommendService.getProfile(UserContext.getUserId()));
    }

    @PostMapping("/cold-start")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> initializeColdStart(@Valid @RequestBody ColdStartRequest request) {
        recommendService.initializeColdStart(UserContext.getUserId(), request);
        return Result.success("偏好初始化成功");
    }

    @PostMapping("/behavior/view/{movieId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> recordView(@PathVariable Long movieId) {
        recommendService.recordView(UserContext.getUserId(), movieId);
        return Result.success();
    }

    @PutMapping("/behavior/favorite/{movieId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> toggleFavorite(@PathVariable Long movieId) {
        return Result.success(recommendService.toggleFavorite(UserContext.getUserId(), movieId));
    }

    @GetMapping("/behavior/favorite/{movieId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> isFavorite(@PathVariable Long movieId) {
        return Result.success(recommendService.isFavorite(UserContext.getUserId(), movieId));
    }

    @GetMapping("/favorites")
    @PreAuthorize("isAuthenticated()")
    public Result<List<FavoriteMovieVO>> getFavorites() {
        return Result.success(recommendService.getFavorites(UserContext.getUserId()));
    }

    @DeleteMapping("/behavior/favorite/{movieId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> removeFavorite(@PathVariable Long movieId) {
        recommendService.removeFavorite(UserContext.getUserId(), movieId);
        return Result.success("已移出想看");
    }

    @GetMapping("/evaluation")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<RecommendationEvaluationVO> evaluate(@RequestParam(defaultValue = "6") int k) {
        return Result.success(recommendService.evaluate(k));
    }

    @GetMapping("/similar/{movieId}")
    public Result<List<MovieVO>> getSimilarMovies(
            @PathVariable Long movieId,
            @RequestParam(defaultValue = "6") Integer limit) {
        List<MovieVO> movies = recommendService.getSimilarMovies(movieId, limit);
        return Result.success(movies);
    }
}
