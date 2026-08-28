package com.movie.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.movie.ticket.common.Result;
import com.movie.ticket.dto.ShowtimeDTO;
import com.movie.ticket.service.ShowtimeService;
import com.movie.ticket.vo.ShowtimeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/showtime")
@RequiredArgsConstructor
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    @GetMapping("/page")
    public Result<IPage<ShowtimeVO>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) Integer status) {
        IPage<ShowtimeVO> pageData = showtimeService.getPage(page, size, movieId, cinemaId, date, status);
        return Result.success(pageData);
    }

    @GetMapping("/list")
    public Result<List<ShowtimeVO>> getByMovieAndCinema(
            @RequestParam Long movieId,
            @RequestParam Long cinemaId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        List<ShowtimeVO> showtimes = showtimeService.getByMovieAndCinema(movieId, cinemaId, date);
        return Result.success(showtimes);
    }

    @GetMapping("/{id}")
    public Result<ShowtimeVO> getById(@PathVariable Long id) {
        ShowtimeVO showtime = showtimeService.getById(id);
        return Result.success(showtime);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> create(@Valid @RequestBody ShowtimeDTO dto) {
        showtimeService.create(dto);
        return Result.success("创建成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ShowtimeDTO dto) {
        showtimeService.update(id, dto);
        return Result.success("更新成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        showtimeService.delete(id);
        return Result.success("删除成功");
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        showtimeService.updateStatus(id, status);
        return Result.success("状态更新成功");
    }

    @GetMapping("/{id}/seats")
    public Result<Map<String, Object>> getSeatStatus(@PathVariable Long id) {
        Map<String, Object> seatStatus = showtimeService.getSeatStatus(id);
        return Result.success(seatStatus);
    }
}
