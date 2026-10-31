package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import com.movie.ticket.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 影院经营统计：按访问权限汇总看板、票房、收入趋势和场次数据。 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final ShowtimeMapper showtimeMapper;
    private final TicketOrderMapper orderMapper;
    private final UserMapper userMapper;

    /** 汇总当前管理范围的经营看板指标。 */
    public Map<String, Object> getDashboardStats() {
        Long scope = UserContext.scopeCinemaId(null);
        if (scope != null) {
            LocalDateTime start = LocalDate.now().atStartOfDay();
            return orderMapper.selectCinemaDashboard(scope, start, start.plusDays(1));
        }
        Map<String, Object> stats = new HashMap<>();

        long movieCount = movieMapper.selectCount(null);
        stats.put("movieCount", movieCount);

        long cinemaCount = cinemaMapper.selectCount(null);
        stats.put("cinemaCount", cinemaCount);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        Long todayOrderCount = orderMapper.selectCount(
                new LambdaQueryWrapper<TicketOrder>()
                        .ge(TicketOrder::getCreateTime, todayStart)
                        .lt(TicketOrder::getCreateTime, todayEnd)
        );
        stats.put("todayOrderCount", todayOrderCount != null ? todayOrderCount : 0);

        BigDecimal todayRevenue = orderMapper.sumPaidRevenue(todayStart, todayEnd);
        stats.put("todayRevenue", todayRevenue);

        long userCount = userMapper.selectCount(null);
        stats.put("userCount", userCount);

        return stats;
    }

    /** 汇总指定范围的影片票房排行。 */
    public List<Map<String, Object>> getBoxOfficeRanking(Integer limit) {
        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 100));
        Long scope = UserContext.scopeCinemaId(null);
        if (scope != null) return orderMapper.selectCinemaBoxOffice(scope, safeLimit);
        return orderMapper.selectBoxOfficeRanking(safeLimit);
    }

    /** 按已支付订单汇总历史票房排行。 */
    public List<Map<String, Object>> getHistoricalBoxOfficeRanking() {
        return orderMapper.selectHistoricalBoxOfficeRanking();
    }

    /** 按今日已支付订单汇总实时票房排行。 */
    public List<Map<String, Object>> getTodayBoxOfficeRanking(Integer limit) {
        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 20));
        LocalDateTime startTime = LocalDate.now().atStartOfDay();
        return orderMapper.selectBoxOfficeRankingBetween(startTime, startTime.plusDays(1), safeLimit);
    }

    /** 汇总指定范围的场次与上座情况。 */
    public Map<String, Object> getShowtimeStats(Long showtimeId) {
        Showtime showtime = showtimeMapper.selectById(showtimeId);
        if (showtime == null) {
            return Collections.emptyMap();
        }
        UserContext.scopeCinemaId(showtime.getCinemaId());

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSeats", showtime.getTotalSeats());
        stats.put("availableSeats", showtime.getAvailableSeats());
        stats.put("soldSeats", showtime.getTotalSeats() - showtime.getAvailableSeats());

        double occupancyRate = 0;
        if (showtime.getTotalSeats() > 0) {
            occupancyRate = (double) (showtime.getTotalSeats() - showtime.getAvailableSeats())
                    / showtime.getTotalSeats() * 100;
        }
        stats.put("occupancyRate", String.format("%.2f", occupancyRate));

        stats.put("revenue", orderMapper.sumPaidRevenueByShowtime(showtimeId));

        return stats;
    }

    /** 按日期汇总经营收入，供趋势图使用。 */
    public List<Map<String, Object>> getRevenueByDate(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BusinessException(400, "统计结束日期不能早于开始日期");
        }
        if (startDate.plusDays(366).isBefore(endDate)) {
            throw new BusinessException(400, "单次统计范围不能超过366天");
        }

        Long scope = UserContext.scopeCinemaId(null);
        List<Map<String, Object>> aggregated = scope == null ? orderMapper.selectRevenueByDate(
                startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()) : orderMapper.selectCinemaRevenue(
                scope, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        Map<String, Map<String, Object>> valuesByDate = new HashMap<>();
        for (Map<String, Object> row : aggregated) {
            valuesByDate.put(String.valueOf(row.get("date")), row);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            String date = currentDate.toString();
            Map<String, Object> aggregatedDay = valuesByDate.get(date);
            Map<String, Object> item = new HashMap<>();
            item.put("date", date);
            item.put("revenue", aggregatedDay == null
                    ? BigDecimal.ZERO : aggregatedDay.get("revenue"));
            item.put("orderCount", aggregatedDay == null
                    ? 0L : aggregatedDay.get("orderCount"));

            result.add(item);
            currentDate = currentDate.plusDays(1);
        }

        return result;
    }

    /** 汇总各影院的经营指标。 */
    public Map<String, Object> getCinemaStats(Long cinemaId) {
        UserContext.scopeCinemaId(cinemaId);
        Map<String, Object> stats = orderMapper.selectCinemaAggregate(cinemaId);
        if (stats == null || stats.isEmpty()) {
            return Collections.emptyMap();
        }

        long totalSeats = ((Number) stats.get("totalSeats")).longValue();
        long soldSeats = ((Number) stats.get("soldSeats")).longValue();

        double occupancyRate = 0;
        if (totalSeats > 0) {
            occupancyRate = (double) soldSeats / (double) totalSeats * 100;
        }

        stats.put("totalSeats", totalSeats);
        stats.put("soldSeats", soldSeats);
        stats.put("occupancyRate", String.format("%.2f", occupancyRate));

        return stats;
    }
}
