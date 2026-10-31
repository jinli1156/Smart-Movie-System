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

/** 电影推荐接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    /** 按热度查询热门影片，供首页或推荐兜底使用。 */
    @GetMapping("/hot")
    public Result<List<MovieVO>> getHotMovies(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<MovieVO> movies = recommendService.getHotMovies(limit);
        return Result.success(movies);
    }

    /** 融合用户偏好与行为生成个性化影片及推荐理由。 */
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

    /** 生成用户观影偏好画像与解释。 */
    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public Result<RecommendationProfileVO> getProfile() {
        return Result.success(recommendService.getProfile(UserContext.getUserId()));
    }

    /** 返回用于偏好雷达图的维度分数及依据。 */
    @GetMapping("/profile/radar")
    @PreAuthorize("isAuthenticated()")
    public Result<RecommendationRadarVO> getRadar() {
        return Result.success(recommendService.getRadar(UserContext.getUserId()));
    }

    /** 保存新用户选择的影片及类型偏好，初始化推荐画像。 */
    @PostMapping("/cold-start")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> initializeColdStart(@Valid @RequestBody ColdStartRequest request) {
        recommendService.initializeColdStart(UserContext.getUserId(), request);
        return Result.success("偏好初始化成功");
    }

    /** 记录影片浏览行为，作为后续推荐的隐式反馈。 */
    @PostMapping("/behavior/view/{movieId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> recordView(@PathVariable Long movieId) {
        recommendService.recordView(UserContext.getUserId(), movieId);
        return Result.success();
    }

    /** 切换当前用户对影片的想看状态，并返回最新状态。 */
    @PutMapping("/behavior/favorite/{movieId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> toggleFavorite(@PathVariable Long movieId) {
        return Result.success(recommendService.toggleFavorite(UserContext.getUserId(), movieId));
    }

    /** 检查当前用户是否已将影片加入想看。 */
    @GetMapping("/behavior/favorite/{movieId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> isFavorite(@PathVariable Long movieId) {
        return Result.success(recommendService.isFavorite(UserContext.getUserId(), movieId));
    }

    /** 读取当前用户的想看影片及收藏信息。 */
    @GetMapping("/favorites")
    @PreAuthorize("isAuthenticated()")
    public Result<List<FavoriteMovieVO>> getFavorites() {
        return Result.success(recommendService.getFavorites(UserContext.getUserId()));
    }

    /** 移除本人想看记录。 */
    @DeleteMapping("/behavior/favorite/{movieId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> removeFavorite(@PathVariable Long movieId) {
        recommendService.removeFavorite(UserContext.getUserId(), movieId);
        return Result.success("已移出想看");
    }

    /** 按时间划分进行离线推荐评估，比较模型、基线和消融指标。 */
    @GetMapping("/evaluation")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<RecommendationEvaluationVO> evaluate(@RequestParam(defaultValue = "6") int k) {
        return Result.success(recommendService.evaluate(k));
    }

    /** 依据影片内容特征返回相似影片。 */
    @GetMapping("/similar/{movieId}")
    public Result<List<MovieVO>> getSimilarMovies(
            @PathVariable Long movieId,
            @RequestParam(defaultValue = "6") Integer limit) {
        List<MovieVO> movies = recommendService.getSimilarMovies(movieId, limit);
        return Result.success(movies);
    }
}
