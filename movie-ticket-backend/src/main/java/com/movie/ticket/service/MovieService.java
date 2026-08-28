package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.dto.MovieDTO;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.vo.MovieVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieMapper movieMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Map<Integer, String> STATUS_MAP = new HashMap<>();
    private static final String NOW_SHOWING_KEY = "movies:now-showing";
    private static final String COMING_SOON_KEY = "movies:coming-soon";
    private static final long CACHE_EXPIRE_MINUTES = 30;

    static {
        STATUS_MAP.put(0, "下架");
        STATUS_MAP.put(1, "上映中");
        STATUS_MAP.put(2, "即将上映");
    }

    @SuppressWarnings("unchecked")
    public List<MovieVO> getNowShowing() {
        try {
            Object cached = redisTemplate.opsForValue().get(NOW_SHOWING_KEY);
            if (cached instanceof List) {
                log.debug("命中缓存: {}", NOW_SHOWING_KEY);
                return (List<MovieVO>) cached;
            }
        } catch (Exception e) {
            log.warn("读取缓存失败，降级查库: {} - {}", NOW_SHOWING_KEY, e.getMessage());
            safeDelete(NOW_SHOWING_KEY);
        }

        List<Movie> movies = movieMapper.selectList(
                new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 1)
                        .orderByDesc(Movie::getRating)
        );
        List<MovieVO> result = movies.stream().map(this::convertToVO).collect(Collectors.toList());

        try {
            redisTemplate.opsForValue().set(NOW_SHOWING_KEY, result, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("写入缓存: {}, 数量: {}", NOW_SHOWING_KEY, result.size());
        } catch (Exception e) {
            log.warn("写入缓存失败: {} - {}", NOW_SHOWING_KEY, e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<MovieVO> getComingSoon() {
        try {
            Object cached = redisTemplate.opsForValue().get(COMING_SOON_KEY);
            if (cached instanceof List) {
                log.debug("命中缓存: {}", COMING_SOON_KEY);
                return (List<MovieVO>) cached;
            }
        } catch (Exception e) {
            log.warn("读取缓存失败，降级查库: {} - {}", COMING_SOON_KEY, e.getMessage());
            safeDelete(COMING_SOON_KEY);
        }

        List<Movie> movies = movieMapper.selectList(
                new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 2)
                        .orderByAsc(Movie::getReleaseDate)
        );
        List<MovieVO> result = movies.stream().map(this::convertToVO).collect(Collectors.toList());

        try {
            redisTemplate.opsForValue().set(COMING_SOON_KEY, result, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("写入缓存: {}, 数量: {}", COMING_SOON_KEY, result.size());
        } catch (Exception e) {
            log.warn("写入缓存失败: {} - {}", COMING_SOON_KEY, e.getMessage());
        }
        return result;
    }

    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("清除缓存失败: {} - {}", key, ex.getMessage());
        }
    }

    public IPage<MovieVO> getPage(Integer page, Integer size, String keyword, Integer status) {
        Page<Movie> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Movie::getTitle, keyword)
                    .or()
                    .like(Movie::getEnglishTitle, keyword)
                    .or()
                    .like(Movie::getDirector, keyword)
                    .or()
                    .like(Movie::getActors, keyword));
        }

        if (status != null) {
            wrapper.eq(Movie::getStatus, status);
        }

        wrapper.orderByDesc(Movie::getCreateTime);
        IPage<Movie> moviePage = movieMapper.selectPage(pageParam, wrapper);
        return moviePage.convert(this::convertToVO);
    }

    public MovieVO getById(Long id) {
        Movie movie = movieMapper.selectById(id);
        if (movie == null) {
            throw new BusinessException("电影不存在");
        }
        return convertToVO(movie);
    }

    public void create(MovieDTO dto) {
        Movie movie = new Movie();
        BeanUtils.copyProperties(dto, movie);
        if (movie.getStatus() == null) {
            movie.setStatus(1);
        }
        movieMapper.insert(movie);
        clearMovieCache();
    }

    public void update(Long id, MovieDTO dto) {
        Movie movie = movieMapper.selectById(id);
        if (movie == null) {
            throw new BusinessException("电影不存在");
        }
        BeanUtils.copyProperties(dto, movie);
        movie.setId(id);
        movieMapper.updateById(movie);
        clearMovieCache();
    }

    public void delete(Long id) {
        Movie movie = movieMapper.selectById(id);
        if (movie == null) {
            throw new BusinessException("电影不存在");
        }
        movieMapper.deleteById(id);
        clearMovieCache();
    }

    public void updateStatus(Long id, Integer status) {
        Movie movie = movieMapper.selectById(id);
        if (movie == null) {
            throw new BusinessException("电影不存在");
        }
        movie.setStatus(status);
        movieMapper.updateById(movie);
        clearMovieCache();
    }

    private void clearMovieCache() {
        safeDelete(NOW_SHOWING_KEY);
        safeDelete(COMING_SOON_KEY);
        log.debug("清除电影缓存");
    }

    private MovieVO convertToVO(Movie movie) {
        MovieVO vo = new MovieVO();
        BeanUtils.copyProperties(movie, vo);
        vo.setStatusText(STATUS_MAP.getOrDefault(movie.getStatus(), "未知"));
        return vo;
    }
}
