package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.vo.MovieInsightVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 根据影片场次与已支付订单汇总今日票房、观影人数、上座率及近期趋势。 */
@Service
@RequiredArgsConstructor
public class MovieInsightService {

    private static final int TREND_DAYS = 7;

    private final MovieMapper movieMapper;
    private final ShowtimeMapper showtimeMapper;
    private final TicketOrderMapper orderMapper;

    /** 获取影片票房、观影人数和场次经营指标。 */
    public MovieInsightVO getInsight(Long movieId) {
        Movie movie = movieMapper.selectById(movieId);
        if (movie == null) {
            throw new BusinessException("电影不存在");
        }

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        LocalDateTime trendStart = todayStart.minusDays(TREND_DAYS - 1L);

        List<Showtime> showtimes = showtimeMapper.selectList(
                new LambdaQueryWrapper<Showtime>().eq(Showtime::getMovieId, movieId));
        List<Showtime> todayShowtimes = showtimes.stream()
                .filter(showtime -> showtime.getStartTime() != null)
                .filter(showtime -> !showtime.getStartTime().isBefore(todayStart)
                        && showtime.getStartTime().isBefore(tomorrowStart))
                .toList();

        long totalSeats = todayShowtimes.stream()
                .map(Showtime::getTotalSeats)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        long soldSeats = todayShowtimes.stream()
                .filter(showtime -> showtime.getTotalSeats() != null && showtime.getAvailableSeats() != null)
                .mapToLong(showtime -> Math.max(0, showtime.getTotalSeats() - showtime.getAvailableSeats()))
                .sum();
        double occupancyRate = totalSeats == 0
                ? 0
                : Math.max(0, Math.min(100, soldSeats * 100.0 / totalSeats));

        List<Long> showtimeIds = showtimes.stream().map(Showtime::getId).filter(Objects::nonNull).toList();
        List<TicketOrder> recentOrders = showtimeIds.isEmpty()
                ? List.of()
                : orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getStatus, 1)
                        .in(TicketOrder::getShowtimeId, showtimeIds)
                        .ge(TicketOrder::getPayTime, trendStart)
                        .lt(TicketOrder::getPayTime, tomorrowStart));

        BigDecimal todayBoxOffice = BigDecimal.ZERO;
        long todayViewerCount = 0;
        List<Long> trendPoints = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) trendPoints.add(0L);

        for (TicketOrder order : recentOrders) {
            LocalDateTime paidAt = order.getPayTime();
            if (paidAt == null) continue;
            int dayIndex = (int) java.time.temporal.ChronoUnit.DAYS.between(trendStart.toLocalDate(), paidAt.toLocalDate());
            if (dayIndex < 0 || dayIndex >= TREND_DAYS) continue;
            long tickets = order.getTicketCount() == null ? 0 : Math.max(0, order.getTicketCount());
            trendPoints.set(dayIndex, trendPoints.get(dayIndex) + tickets);
            if (!paidAt.isBefore(todayStart)) {
                todayViewerCount += tickets;
                if (order.getTotalPrice() != null) todayBoxOffice = todayBoxOffice.add(order.getTotalPrice());
            }
        }

        long yesterdayCount = TREND_DAYS > 1 ? trendPoints.get(TREND_DAYS - 2) : 0;
        int trendPercent = percentageChange(todayViewerCount, yesterdayCount);

        return MovieInsightVO.builder()
                .movieId(movieId)
                .todayBoxOffice(todayBoxOffice.setScale(2, RoundingMode.HALF_UP))
                .todayViewerCount(todayViewerCount)
                .occupancyRate(Math.round(occupancyRate * 10.0) / 10.0)
                .trendPercent(trendPercent)
                .trendPoints(trendPoints)
                .generatedAt(LocalDateTime.now().withNano(0))
                .build();
    }

    private int percentageChange(long current, long previous) {
        if (previous == 0) return current == 0 ? 0 : 100;
        double change = (current - previous) * 100.0 / previous;
        return (int) Math.max(-999, Math.min(999, Math.round(change)));
    }
}
