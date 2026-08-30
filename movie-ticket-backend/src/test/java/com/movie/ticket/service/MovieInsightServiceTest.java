package com.movie.ticket.service;

import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.vo.MovieInsightVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieInsightServiceTest {

    @Mock
    private MovieMapper movieMapper;

    @Mock
    private ShowtimeMapper showtimeMapper;

    @Mock
    private TicketOrderMapper orderMapper;

    @InjectMocks
    private MovieInsightService movieInsightService;

    @Test
    void insightUsesPaidOrdersAndTodaysSeatAvailability() {
        Movie movie = new Movie();
        movie.setId(1L);
        when(movieMapper.selectById(1L)).thenReturn(movie);

        LocalDateTime todayAtNoon = LocalDate.now().atTime(12, 0);
        Showtime showtime = new Showtime();
        showtime.setId(10L);
        showtime.setMovieId(1L);
        showtime.setStartTime(todayAtNoon);
        showtime.setTotalSeats(100);
        showtime.setAvailableSeats(35);
        when(showtimeMapper.selectList(any())).thenReturn(List.of(showtime));

        TicketOrder todayOrder = paidOrder(todayAtNoon.minusHours(1), "168.00", 3);
        TicketOrder yesterdayOrder = paidOrder(todayAtNoon.minusDays(1), "56.00", 1);
        when(orderMapper.selectList(any())).thenReturn(List.of(todayOrder, yesterdayOrder));

        MovieInsightVO insight = movieInsightService.getInsight(1L);

        assertEquals(new BigDecimal("168.00"), insight.getTodayBoxOffice());
        assertEquals(3, insight.getTodayViewerCount());
        assertEquals(65.0, insight.getOccupancyRate());
        assertEquals(200, insight.getTrendPercent());
        assertEquals(3L, insight.getTrendPoints().get(6));
        assertEquals(1L, insight.getTrendPoints().get(5));
    }

    private TicketOrder paidOrder(LocalDateTime payTime, String totalPrice, int ticketCount) {
        TicketOrder order = new TicketOrder();
        order.setStatus(1);
        order.setShowtimeId(10L);
        order.setPayTime(payTime);
        order.setTotalPrice(new BigDecimal(totalPrice));
        order.setTicketCount(ticketCount);
        return order;
    }
}
