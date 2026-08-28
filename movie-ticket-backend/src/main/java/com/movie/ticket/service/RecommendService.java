package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.vo.MovieVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendService {

    private final MovieMapper movieMapper;
    private final TicketOrderMapper orderMapper;
    private final ShowtimeMapper showtimeMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String HOT_MOVIES_KEY = "hot:movies";
    private static final String MOVIE_SCORE_KEY = "movie:score:";
    private static final int CACHE_EXPIRE_HOURS = 1;

    public List<MovieVO> getHotMovies(Integer limit) {
        String cacheKey = HOT_MOVIES_KEY + ":" + limit;

        List<String> cachedIds = Collections.emptyList();
        try {
            cachedIds = redisTemplate.opsForList().range(cacheKey, 0, -1);
        } catch (RuntimeException e) {
            log.warn("读取热门电影缓存失败，降级查库: {} - {}", cacheKey, e.getMessage());
        }
        if (cachedIds != null && !cachedIds.isEmpty()) {
            return cachedIds.stream()
                    .map(id -> movieMapper.selectById(Long.parseLong(id)))
                    .filter(Objects::nonNull)
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
        }

        List<Movie> movies = movieMapper.selectList(
                new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 1)
                        .orderByDesc(Movie::getRating)
        );

        Map<Long, Double> movieScores = new HashMap<>();
        for (Movie movie : movies) {
            double score = calculateHotScore(movie);
            movieScores.put(movie.getId(), score);
        }

        List<Movie> hotMovies = movies.stream()
                .sorted((m1, m2) -> Double.compare(
                        movieScores.getOrDefault(m2.getId(), 0.0),
                        movieScores.getOrDefault(m1.getId(), 0.0)
                ))
                .limit(limit != null ? limit : 10)
                .collect(Collectors.toList());

        List<String> movieIds = hotMovies.stream()
                .map(m -> String.valueOf(m.getId()))
                .collect(Collectors.toList());

        if (!movieIds.isEmpty()) {
            try {
                redisTemplate.opsForList().rightPushAll(cacheKey, movieIds);
                redisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            } catch (RuntimeException e) {
                log.warn("写入热门电影缓存失败，继续返回数据库结果: {} - {}", cacheKey, e.getMessage());
            }
        }

        return hotMovies.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    private double calculateHotScore(Movie movie) {
        double ratingScore = movie.getRating() != null ? movie.getRating().doubleValue() * 10 : 0;

        List<Long> showtimeIds = showtimeMapper.selectList(
                new LambdaQueryWrapper<Showtime>()
                        .eq(Showtime::getMovieId, movie.getId())
        ).stream()
                .map(Showtime::getId)
                .collect(Collectors.toList());

        double orderScore = 0;
        if (!showtimeIds.isEmpty()) {
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            Long orderCount = orderMapper.selectCount(
                    new LambdaQueryWrapper<TicketOrder>()
                            .eq(TicketOrder::getStatus, 1)
                            .in(TicketOrder::getShowtimeId, showtimeIds)
                            .ge(TicketOrder::getCreateTime, sevenDaysAgo)
            );
            orderScore = orderCount != null ? orderCount * 2 : 0;
        }

        long daysSinceRelease = 0;
        if (movie.getReleaseDate() != null) {
            daysSinceRelease = java.time.Duration.between(
                    movie.getReleaseDate().atStartOfDay(),
                    LocalDateTime.now()
            ).toDays();
        }
        double timeScore = Math.max(0, 100 - daysSinceRelease);

        return ratingScore + orderScore + timeScore;
    }

    public List<MovieVO> getRecommendedMovies(Long userId, Integer limit) {
        List<TicketOrder> userOrders = orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getUserId, userId)
                        .eq(TicketOrder::getStatus, 1)
        );

        if (userOrders.isEmpty()) {
            return getHotMovies(limit);
        }

        List<Long> orderedShowtimeIds = userOrders.stream()
                .map(TicketOrder::getShowtimeId)
                .distinct()
                .collect(Collectors.toList());

        Set<Long> watchedMovieIds = showtimeMapper.selectBatchIds(orderedShowtimeIds).stream()
                .map(Showtime::getMovieId)
                .collect(Collectors.toSet());

        List<Movie> allMovies = movieMapper.selectList(
                new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 1)
        );

        List<Movie> recommendedMovies = allMovies.stream()
                .filter(movie -> !watchedMovieIds.contains(movie.getId()))
                .sorted((m1, m2) -> Double.compare(
                        m2.getRating() != null ? m2.getRating().doubleValue() : 0,
                        m1.getRating() != null ? m1.getRating().doubleValue() : 0
                ))
                .limit(limit != null ? limit : 10)
                .collect(Collectors.toList());

        return recommendedMovies.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    public List<MovieVO> getSimilarMovies(Long movieId, Integer limit) {
        Movie movie = movieMapper.selectById(movieId);
        if (movie == null) {
            return Collections.emptyList();
        }

        String[] genres = movie.getGenre() != null ? movie.getGenre().split(",") : new String[0];
        Set<String> genreSet = new HashSet<>(Arrays.asList(genres));

        List<Movie> allMovies = movieMapper.selectList(
                new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 1)
                        .ne(Movie::getId, movieId)
        );

        List<Movie> similarMovies = allMovies.stream()
                .filter(m -> {
                    if (m.getGenre() == null) return false;
                    String[] mGenres = m.getGenre().split(",");
                    for (String genre : mGenres) {
                        if (genreSet.contains(genre.trim())) {
                            return true;
                        }
                    }
                    return false;
                })
                .sorted((m1, m2) -> Double.compare(
                        m2.getRating() != null ? m2.getRating().doubleValue() : 0,
                        m1.getRating() != null ? m1.getRating().doubleValue() : 0
                ))
                .limit(limit != null ? limit : 6)
                .collect(Collectors.toList());

        return similarMovies.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    private MovieVO convertToVO(Movie movie) {
        MovieVO vo = new MovieVO();
        BeanUtils.copyProperties(movie, vo);

        Map<Integer, String> statusMap = new HashMap<>();
        statusMap.put(0, "未上映");
        statusMap.put(1, "正在上映");
        statusMap.put(2, "即将上映");
        statusMap.put(3, "已下架");

        vo.setStatusText(statusMap.getOrDefault(movie.getStatus(), "未知"));
        return vo;
    }
}
