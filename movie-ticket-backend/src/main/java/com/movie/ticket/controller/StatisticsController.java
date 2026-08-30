package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
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

@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final SchedulingIntelligenceService schedulingIntelligenceService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = statisticsService.getDashboardStats();
        return Result.success(stats);
    }

    @GetMapping("/box-office")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<List<Map<String, Object>>> getBoxOfficeRanking(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Map<String, Object>> ranking = statisticsService.getBoxOfficeRanking(limit);
        return Result.success(ranking);
    }

    @GetMapping("/showtime/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> getShowtimeStats(@PathVariable Long id) {
        Map<String, Object> stats = statisticsService.getShowtimeStats(id);
        return Result.success(stats);
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<List<Map<String, Object>>> getRevenueByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> data = statisticsService.getRevenueByDate(startDate, endDate);
        return Result.success(data);
    }

    @GetMapping("/scheduling-insights")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> getSchedulingInsights(@RequestParam(required = false) Long cinemaId) {
        return Result.success(schedulingIntelligenceService.getInsights(cinemaId));
    }

    @PostMapping("/scheduling/predict")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> predictOccupancy(@Valid @RequestBody SchedulingPredictionRequest request) {
        return Result.success(schedulingIntelligenceService.predict(request));
    }

    @PostMapping("/scheduling/simulate")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, Object>> simulateScheduling(@Valid @RequestBody SchedulingSimulationRequest request) {
        return Result.success(schedulingIntelligenceService.simulate(request));
    }
}
