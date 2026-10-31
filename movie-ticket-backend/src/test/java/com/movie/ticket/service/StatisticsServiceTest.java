package com.movie.ticket.service;

import com.movie.ticket.mapper.CinemaMapper;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private MovieMapper movieMapper;
    @Mock private CinemaMapper cinemaMapper;
    @Mock private ShowtimeMapper showtimeMapper;
    @Mock private TicketOrderMapper orderMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    void boxOfficeRankingDelegatesAggregationAndLimitToSql() {
        List<Map<String, Object>> expected = List.of(
                ranking(1L, "电影A", "100.00", 2L),
                ranking(2L, "电影B", "30.00", 1L));
        when(orderMapper.selectBoxOfficeRanking(10)).thenReturn(expected);

        List<Map<String, Object>> ranking = statisticsService.getBoxOfficeRanking(10);

        assertEquals(expected, ranking);
        verify(orderMapper, times(1)).selectBoxOfficeRanking(10);
    }

    @Test
    void todayBoxOfficeRankingUsesPaidOrderTimeWindow() {
        List<Map<String, Object>> expected = List.of(
                ranking(1L, "电影A", "168.00", 3L));
        when(orderMapper.selectBoxOfficeRankingBetween(any(), any(), eq(3))).thenReturn(expected);

        List<Map<String, Object>> ranking = statisticsService.getTodayBoxOfficeRanking(3);

        assertEquals(expected, ranking);
        verify(orderMapper, times(1)).selectBoxOfficeRankingBetween(any(), any(), eq(3));
    }

    @Test
    void revenueTrendUsesOneGroupedQueryAndFillsMissingDates() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 3);
        Map<String, Object> paidDay = new HashMap<>();
        paidDay.put("date", start);
        paidDay.put("revenue", new BigDecimal("88.00"));
        paidDay.put("orderCount", 2L);
        when(orderMapper.selectRevenueByDate(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(paidDay));

        List<Map<String, Object>> trend = statisticsService.getRevenueByDate(start, end);

        assertEquals(3, trend.size());
        assertEquals(new BigDecimal("88.00"), trend.get(0).get("revenue"));
        assertEquals(BigDecimal.ZERO, trend.get(1).get("revenue"));
        assertEquals(0L, trend.get(2).get("orderCount"));
        verify(orderMapper, times(1)).selectRevenueByDate(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    private Map<String, Object> ranking(Long movieId,
                                        String movieTitle,
                                        String boxOffice,
                                        Long ticketCount) {
        Map<String, Object> item = new HashMap<>();
        item.put("movieId", movieId);
        item.put("movieTitle", movieTitle);
        item.put("boxOffice", new BigDecimal(boxOffice));
        item.put("ticketCount", ticketCount);
        return item;
    }
}
