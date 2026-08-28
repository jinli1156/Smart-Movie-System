package com.movie.ticket.service;

import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private MovieMapper movieMapper;

    @Mock
    private CinemaMapper cinemaMapper;

    @Mock
    private ShowtimeMapper showtimeMapper;

    @Mock
    private TicketOrderMapper orderMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    void boxOfficeRankingAggregatesPaidOrdersByMovie() {
        Movie movieA = movie(1L, "电影A");
        Movie movieB = movie(2L, "电影B");
        when(movieMapper.selectList(any())).thenReturn(List.of(movieA, movieB));

        TicketOrder movieAOrder = paidOrder(101L, "100.00", 2);
        TicketOrder movieBOrder = paidOrder(102L, "30.00", 1);
        when(orderMapper.selectList(any())).thenReturn(List.of(movieAOrder, movieBOrder));

        Showtime movieAShowtime = showtime(101L, 1L);
        Showtime movieBShowtime = showtime(102L, 2L);
        when(showtimeMapper.selectBatchIds(any())).thenReturn(List.of(movieAShowtime, movieBShowtime));

        List<Map<String, Object>> ranking = statisticsService.getBoxOfficeRanking(10);

        assertEquals(2, ranking.size());
        assertEquals("电影A", ranking.get(0).get("movieTitle"));
        assertEquals(new BigDecimal("100.00"), ranking.get(0).get("boxOffice"));
        assertEquals(2L, ranking.get(0).get("ticketCount"));
        assertEquals("电影B", ranking.get(1).get("movieTitle"));
        assertEquals(new BigDecimal("30.00"), ranking.get(1).get("boxOffice"));
        assertEquals(1L, ranking.get(1).get("ticketCount"));

        verify(orderMapper, times(1)).selectList(any());
        verify(showtimeMapper, times(1)).selectBatchIds(any());
    }

    private Movie movie(Long id, String title) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle(title);
        movie.setStatus(1);
        return movie;
    }

    private TicketOrder paidOrder(Long showtimeId, String totalPrice, int ticketCount) {
        TicketOrder order = new TicketOrder();
        order.setShowtimeId(showtimeId);
        order.setStatus(1);
        order.setTotalPrice(new BigDecimal(totalPrice));
        order.setTicketCount(ticketCount);
        return order;
    }

    private Showtime showtime(Long id, Long movieId) {
        Showtime showtime = new Showtime();
        showtime.setId(id);
        showtime.setMovieId(movieId);
        return showtime;
    }
}
