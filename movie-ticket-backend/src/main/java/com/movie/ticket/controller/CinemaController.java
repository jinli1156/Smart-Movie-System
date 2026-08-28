package com.movie.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.movie.ticket.common.Result;
import com.movie.ticket.dto.CinemaDTO;
import com.movie.ticket.service.CinemaService;
import com.movie.ticket.vo.CinemaVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cinema")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;

    @GetMapping("/page")
    public Result<IPage<CinemaVO>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        IPage<CinemaVO> pageData = cinemaService.getPage(page, size, keyword);
        return Result.success(pageData);
    }

    @GetMapping("/all")
    public Result<List<CinemaVO>> getAll() {
        List<CinemaVO> cinemas = cinemaService.getAll();
        return Result.success(cinemas);
    }

    @GetMapping("/{id}")
    public Result<CinemaVO> getById(@PathVariable Long id) {
        CinemaVO cinema = cinemaService.getById(id);
        return Result.success(cinema);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> create(@Valid @RequestBody CinemaDTO dto) {
        cinemaService.create(dto);
        return Result.success("创建成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CinemaDTO dto) {
        cinemaService.update(id, dto);
        return Result.success("更新成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        cinemaService.delete(id);
        return Result.success("删除成功");
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        cinemaService.updateStatus(id, status);
        return Result.success("状态更新成功");
    }
}
