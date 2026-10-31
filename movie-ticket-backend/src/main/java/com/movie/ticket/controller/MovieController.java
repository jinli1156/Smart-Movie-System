package com.movie.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.movie.ticket.common.Result;
import com.movie.ticket.dto.MovieDTO;
import com.movie.ticket.service.MovieInsightService;
import com.movie.ticket.service.MovieService;
import com.movie.ticket.service.StatisticsService;
import com.movie.ticket.vo.MovieInsightVO;
import com.movie.ticket.vo.MovieVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 电影接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/movie")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final MovieInsightService movieInsightService;
    private final StatisticsService statisticsService;

    /** 按筛选条件分页查询电影，供列表页展示。 */
    @GetMapping("/page")
    public Result<IPage<MovieVO>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        IPage<MovieVO> pageData = movieService.getPage(page, size, keyword, status);
        return Result.success(pageData);
    }

    /** 查询当前热映影片列表。 */
    @GetMapping("/now-showing")
    public Result<List<MovieVO>> getNowShowing() {
        List<MovieVO> movies = movieService.getNowShowing();
        return Result.success(movies);
    }

    /** 查询即将上映影片列表。 */
    @GetMapping("/coming-soon")
    public Result<List<MovieVO>> getComingSoon() {
        List<MovieVO> movies = movieService.getComingSoon();
        return Result.success(movies);
    }

    /** 按已支付订单汇总历史票房排行。 */
    @GetMapping("/box-office/history")
    public Result<List<Map<String, Object>>> getHistoricalBoxOfficeRanking() {
        return Result.success(statisticsService.getHistoricalBoxOfficeRanking());
    }

    /** 按今日已支付订单汇总实时票房排行。 */
    @GetMapping("/box-office/today")
    public Result<List<Map<String, Object>>> getTodayBoxOfficeRanking(
            @RequestParam(defaultValue = "3") Integer limit) {
        return Result.success(statisticsService.getTodayBoxOfficeRanking(limit));
    }

    /** 根据 ID 查询电影详情。 */
    @GetMapping("/{id}")
    public Result<MovieVO> getById(@PathVariable Long id) {
        MovieVO movie = movieService.getById(id);
        return Result.success(movie);
    }

    /** 获取影片票房、观影人数和场次经营指标。 */
    @GetMapping("/{id}/insight")
    public Result<MovieInsightVO> getInsight(@PathVariable Long id) {
        return Result.success(movieInsightService.getInsight(id));
    }

    /** 新增电影，由业务层执行参数及关联数据校验。 */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@Valid @RequestBody MovieDTO dto) {
        movieService.create(dto);
        return Result.success("创建成功");
    }

    /** 更新电影，由业务层校验当前操作权限与业务条件。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MovieDTO dto) {
        movieService.update(id, dto);
        return Result.success("更新成功");
    }

    /** 删除电影，由业务层检查权限及关联数据限制。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        movieService.delete(id);
        return Result.success("删除成功");
    }

    /** 修改电影状态，具体可用状态和操作限制由业务层校验。 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        movieService.updateStatus(id, status);
        return Result.success("状态更新成功");
    }
}
