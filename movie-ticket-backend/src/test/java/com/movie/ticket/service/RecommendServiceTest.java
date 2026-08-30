package com.movie.ticket.service;

import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Comment;
import com.movie.ticket.entity.User;
import com.movie.ticket.entity.UserMovieBehavior;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.mapper.UserMapper;
import com.movie.ticket.mapper.CommentMapper;
import com.movie.ticket.mapper.UserMovieBehaviorMapper;
import com.movie.ticket.mapper.CinemaMapper;
import com.movie.ticket.vo.MovieVO;
import com.movie.ticket.vo.FavoriteMovieVO;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Mock
    private UserMapper userMapper;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private UserMovieBehaviorMapper behaviorMapper;

    @Mock
    private CinemaMapper cinemaMapper;

    @Test
    void hotMoviesFallBackToDatabaseWhenRedisIsUnavailable() {
        RecommendService service = new RecommendService(
                movieMapper, orderMapper, showtimeMapper, redisTemplate,
                userMapper, commentMapper, behaviorMapper, cinemaMapper);

        when(redisTemplate.opsForList())
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        when(movieMapper.selectList(any())).thenReturn(List.of(movie(1L, "电影A", "9.0")));
        when(showtimeMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<MovieVO> result = service.getHotMovies(6);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("电影A", result.get(0).getTitle());
    }

    @Test
    void personalRecommendationUsesExplicitGenreFiveStarDirectorAndDiversity() {
        RecommendService service = new RecommendService(
                movieMapper, orderMapper, showtimeMapper, redisTemplate,
                userMapper, commentMapper, behaviorMapper, cinemaMapper);
        Movie source = movie(1L, "诺兰科幻A", "9.0");
        source.setGenre("科幻");
        source.setDirector("诺兰");
        source.setActors("演员甲");
        source.setLanguage("英语");
        source.setDuration(150);
        Movie sameDirector = movie(2L, "诺兰科幻B", "8.8");
        sameDirector.setGenre("科幻");
        sameDirector.setDirector("诺兰");
        sameDirector.setActors("演员乙");
        sameDirector.setLanguage("英语");
        sameDirector.setDuration(145);
        Movie anotherScifi = movie(3L, "其他科幻", "8.7");
        anotherScifi.setGenre("科幻");
        anotherScifi.setDirector("其他导演");
        anotherScifi.setLanguage("英语");
        anotherScifi.setDuration(140);
        Movie comedy = movie(4L, "喜剧片", "8.6");
        comedy.setGenre("喜剧");
        comedy.setDirector("喜剧导演");
        comedy.setLanguage("中文");
        comedy.setDuration(100);

        User user = new User();
        user.setId(9L);
        user.setFavoriteGenres(List.of("科幻"));
        UserMovieBehavior view = new UserMovieBehavior();
        view.setUserId(9L);
        view.setMovieId(1L);
        view.setBehaviorType("VIEW");
        view.setBehaviorCount(1);
        view.setActive(1);
        view.setLastOccurredAt(LocalDateTime.now());
        Comment fiveStar = new Comment();
        fiveStar.setMovieId(1L);
        fiveStar.setUserId(9L);
        fiveStar.setRating(5);
        fiveStar.setCreateTime(LocalDateTime.now());

        when(movieMapper.selectList(any())).thenReturn(List.of(source, sameDirector, anotherScifi, comedy));
        when(userMapper.selectById(9L)).thenReturn(user);
        when(behaviorMapper.selectList(any())).thenReturn(List.of(view));
        when(orderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(commentMapper.selectList(any())).thenReturn(List.of(fiveStar));
        when(showtimeMapper.selectList(any())).thenReturn(Collections.emptyList());

        var result = service.getRecommendedMovies(9L, 3, null, null);

        assertEquals(3, result.size());
        assertTrue(result.stream().filter(movie -> "科幻".equals(movie.getGenre())).count() <= 2);
        assertTrue(result.stream().filter(movie -> movie.getId().equals(2L)).findFirst()
                .orElseThrow().getRecommendationReason().contains("五星评价"));
    }

    @Test
    void favoritesKeepBehaviorOrderAndFavoriteTime() {
        RecommendService service = new RecommendService(
                movieMapper, orderMapper, showtimeMapper, redisTemplate,
                userMapper, commentMapper, behaviorMapper, cinemaMapper);
        LocalDateTime newestTime = LocalDateTime.now();
        UserMovieBehavior newest = favorite(9L, 2L, newestTime);
        UserMovieBehavior older = favorite(9L, 1L, newestTime.minusDays(2));

        when(behaviorMapper.selectList(any())).thenReturn(List.of(newest, older));
        when(movieMapper.selectBatchIds(any())).thenReturn(List.of(
                movie(1L, "较早加入", "8.5"), movie(2L, "最近加入", "9.1")));

        List<FavoriteMovieVO> result = service.getFavorites(9L);

        assertEquals(List.of(2L, 1L), result.stream().map(FavoriteMovieVO::getId).toList());
        assertEquals(newestTime, result.getFirst().getFavoriteTime());
        assertEquals("正在上映", result.getFirst().getStatusText());
    }

    private UserMovieBehavior favorite(Long userId, Long movieId, LocalDateTime time) {
        UserMovieBehavior behavior = new UserMovieBehavior();
        behavior.setUserId(userId);
        behavior.setMovieId(movieId);
        behavior.setBehaviorType("FAVORITE");
        behavior.setActive(1);
        behavior.setLastOccurredAt(time);
        return behavior;
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
