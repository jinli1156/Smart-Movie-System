package com.movie.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.movie.ticket.common.Result;
import com.movie.ticket.dto.MovieDTO;
import com.movie.ticket.service.MovieInsightService;
import com.movie.ticket.service.MovieService;
import com.movie.ticket.vo.MovieInsightVO;
import com.movie.ticket.vo.MovieVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/movie")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final MovieInsightService movieInsightService;

    @GetMapping("/page")
    public Result<IPage<MovieVO>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        IPage<MovieVO> pageData = movieService.getPage(page, size, keyword, status);
        return Result.success(pageData);
    }

    @GetMapping("/now-showing")
    public Result<List<MovieVO>> getNowShowing() {
        List<MovieVO> movies = movieService.getNowShowing();
        return Result.success(movies);
    }

    @GetMapping("/coming-soon")
    public Result<List<MovieVO>> getComingSoon() {
        List<MovieVO> movies = movieService.getComingSoon();
        return Result.success(movies);
    }

    @GetMapping("/{id}")
    public Result<MovieVO> getById(@PathVariable Long id) {
        MovieVO movie = movieService.getById(id);
        return Result.success(movie);
    }

    @GetMapping("/{id}/insight")
    public Result<MovieInsightVO> getInsight(@PathVariable Long id) {
        return Result.success(movieInsightService.getInsight(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> create(@Valid @RequestBody MovieDTO dto) {
        movieService.create(dto);
        return Result.success("创建成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MovieDTO dto) {
        movieService.update(id, dto);
        return Result.success("更新成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        movieService.delete(id);
        return Result.success("删除成功");
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        movieService.updateStatus(id, status);
        return Result.success("状态更新成功");
    }
}
