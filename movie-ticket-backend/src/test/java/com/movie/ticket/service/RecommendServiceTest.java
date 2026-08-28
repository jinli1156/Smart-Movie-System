package com.movie.ticket.service;

import com.movie.ticket.entity.Movie;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.vo.MovieVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendServiceTest {

    @Mock
    private MovieMapper movieMapper;

    @Mock
    private TicketOrderMapper orderMapper;

    @Mock
    private ShowtimeMapper showtimeMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void hotMoviesFallBackToDatabaseWhenRedisIsUnavailable() {
        RecommendService service = new RecommendService(
                movieMapper, orderMapper, showtimeMapper, redisTemplate);

        when(redisTemplate.opsForList())
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        when(movieMapper.selectList(any())).thenReturn(List.of(movie(1L, "电影A", "9.0")));
        when(showtimeMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<MovieVO> result = service.getHotMovies(6);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("电影A", result.get(0).getTitle());
    }

    private Movie movie(Long id, String title, String rating) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle(title);
        movie.setRating(new BigDecimal(rating));
        movie.setReleaseDate(LocalDate.now().minusDays(1));
        movie.setStatus(1);
        return movie;
    }
}
