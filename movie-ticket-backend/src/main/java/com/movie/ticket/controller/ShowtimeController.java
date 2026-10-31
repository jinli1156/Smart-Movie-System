package com.movie.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.movie.ticket.common.Result;
import com.movie.ticket.dto.ShowtimeDTO;
import com.movie.ticket.dto.SeatRecommendationRequest;
import com.movie.ticket.service.ShowtimeService;
import com.movie.ticket.service.SeatRecommendationService;
import com.movie.ticket.vo.SeatRecommendationVO;
import com.movie.ticket.vo.SeatRecommendationResultVO;
import com.movie.ticket.vo.ShowtimeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 场次接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/showtime")
@RequiredArgsConstructor
public class ShowtimeController {

    private final ShowtimeService showtimeService;
    private final SeatRecommendationService seatRecommendationService;

    /** 按筛选条件分页查询场次，供列表页展示。 */
    @GetMapping("/page")
    public Result<IPage<ShowtimeVO>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) Integer status) {
        IPage<ShowtimeVO> pageData = showtimeService.getPage(page, size, movieId, cinemaId, date, status);
        return Result.success(pageData);
    }

    /** 查询指定影片在影院和日期范围内的可选场次。 */
    @GetMapping("/list")
    public Result<List<ShowtimeVO>> getByMovieAndCinema(
            @RequestParam Long movieId,
            @RequestParam Long cinemaId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        List<ShowtimeVO> showtimes = showtimeService.getByMovieAndCinema(movieId, cinemaId, date);
        return Result.success(showtimes);
    }

    /** 根据 ID 查询场次详情。 */
    @GetMapping("/{id}")
    public Result<ShowtimeVO> getById(@PathVariable Long id) {
        ShowtimeVO showtime = showtimeService.getById(id);
        return Result.success(showtime);
    }

    /** 新增场次，由业务层执行参数及关联数据校验。 */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> create(@Valid @RequestBody ShowtimeDTO dto) {
        showtimeService.create(dto);
        return Result.success("创建成功");
    }

    /** 更新场次，由业务层校验当前操作权限与业务条件。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ShowtimeDTO dto) {
        showtimeService.update(id, dto);
        return Result.success("更新成功");
    }

    /** 删除场次，由业务层检查权限及关联数据限制。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        showtimeService.delete(id);
        return Result.success("删除成功");
    }

    /** 修改场次状态，具体可用状态和操作限制由业务层校验。 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        showtimeService.updateStatus(id, status);
        return Result.success("状态更新成功");
    }

    /** 合并座位布局、有效订单与临时占用，返回当前座位状态。 */
    @GetMapping("/{id}/seats")
    public Result<Map<String, Object>> getSeatStatus(@PathVariable Long id) {
        Map<String, Object> seatStatus = showtimeService.getSeatStatus(id);
        return Result.success(seatStatus);
    }

    /** 按人数与结构化偏好生成可用座位组合。 */
    @GetMapping("/{id}/seat-recommendation")
    public Result<List<SeatRecommendationVO>> getSeatRecommendation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "2") int people,
            @RequestParam(defaultValue = "CENTER") String preference,
            @RequestParam(defaultValue = "3") int limit) {
        return Result.success(seatRecommendationService.recommend(id, people, preference, limit));
    }

    /** 结合自然语言偏好推荐座位，并返回评分依据及分析来源。 */
    @PostMapping("/{id}/seat-recommendation")
    public Result<SeatRecommendationResultVO> getSmartSeatRecommendation(
            @PathVariable Long id,
            @Valid @RequestBody SeatRecommendationRequest request) {
        return Result.success(seatRecommendationService.recommendSmart(id, request));
    }
}
