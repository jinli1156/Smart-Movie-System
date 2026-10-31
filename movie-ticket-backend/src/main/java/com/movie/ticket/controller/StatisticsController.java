package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.dto.SchedulingOptimizationRequest;
import com.movie.ticket.dto.SchedulingPredictionRequest;
import com.movie.ticket.dto.SchedulingSimulationRequest;
import com.movie.ticket.service.SchedulingIntelligenceService;
import com.movie.ticket.service.StatisticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 经营统计接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final SchedulingIntelligenceService schedulingIntelligenceService;
    private final com.movie.ticket.service.SchedulingWorkspaceService schedulingWorkspaceService;

    /** 读取指定影院、日期的排片与快照，供时间轴编辑。 */
    @GetMapping("/scheduling/workspace")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> schedulingWorkspace(@RequestParam Long cinemaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        return Result.success(schedulingWorkspaceService.workspace(cinemaId, targetDate));
    }

    /** 检查整日草案的快照、时间冲突和业务约束，返回比较结果。 */
    @PostMapping("/scheduling/check")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> checkDraft(@Valid @RequestBody com.movie.ticket.dto.SchedulingDraftRequest request) {
        return Result.success(schedulingWorkspaceService.check(request));
    }

    /** 重新检查草案并通过事务发布排片变化。 */
    @PostMapping("/scheduling/apply")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> applyDraft(@Valid @RequestBody com.movie.ticket.dto.SchedulingDraftRequest request) {
        return Result.success(schedulingWorkspaceService.apply(request));
    }

    /** 汇总当前管理范围的经营看板指标。 */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = statisticsService.getDashboardStats();
        return Result.success(stats);
    }

    /** 汇总指定范围的影片票房排行。 */
    @GetMapping("/box-office")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<List<Map<String, Object>>> getBoxOfficeRanking(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Map<String, Object>> ranking = statisticsService.getBoxOfficeRanking(limit);
        return Result.success(ranking);
    }

    /** 汇总指定范围的场次与上座情况。 */
    @GetMapping("/showtime/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> getShowtimeStats(@PathVariable Long id) {
        Map<String, Object> stats = statisticsService.getShowtimeStats(id);
        return Result.success(stats);
    }

    /** 按日期汇总经营收入，供趋势图使用。 */
    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<List<Map<String, Object>>> getRevenueByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> data = statisticsService.getRevenueByDate(startDate, endDate);
        return Result.success(data);
    }

    /** 读取历史排片规律、热力图及预测评估数据。 */
    @GetMapping("/scheduling-insights")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> getSchedulingInsights(@RequestParam(required = false) Long cinemaId) {
        return Result.success(schedulingIntelligenceService.getInsights(cinemaId));
    }

    /** 根据影片、影院和时段估算单场经营表现。 */
    @PostMapping("/scheduling/predict")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> predictOccupancy(@Valid @RequestBody SchedulingPredictionRequest request) {
        return Result.success(schedulingIntelligenceService.predict(request));
    }

    /** 模拟排片调整并与原方案比较预计经营表现。 */
    @PostMapping("/scheduling/simulate")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> simulateScheduling(@Valid @RequestBody SchedulingSimulationRequest request) {
        return Result.success(schedulingIntelligenceService.simulate(request));
    }

    /** 按目标与业务约束求解候选排片方案。 */
    @PostMapping("/scheduling/optimize")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> optimizeScheduling(@Valid @RequestBody SchedulingOptimizationRequest request) {
        return Result.success(schedulingIntelligenceService.optimize(request));
    }
}
