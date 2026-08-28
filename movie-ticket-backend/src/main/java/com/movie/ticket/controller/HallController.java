package com.movie.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.movie.ticket.common.Result;
import com.movie.ticket.dto.HallDTO;
import com.movie.ticket.service.HallService;
import com.movie.ticket.vo.HallVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hall")
@RequiredArgsConstructor
public class HallController {

    private final HallService hallService;

    @GetMapping("/page")
    public Result<IPage<HallVO>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long cinemaId) {
        IPage<HallVO> pageData = hallService.getPage(page, size, cinemaId);
        return Result.success(pageData);
    }

    @GetMapping("/cinema/{cinemaId}")
    public Result<List<HallVO>> getByCinemaId(@PathVariable Long cinemaId) {
        List<HallVO> halls = hallService.getByCinemaId(cinemaId);
        return Result.success(halls);
    }

    @GetMapping("/{id}")
    public Result<HallVO> getById(@PathVariable Long id) {
        HallVO hall = hallService.getById(id);
        return Result.success(hall);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> create(@Valid @RequestBody HallDTO dto) {
        hallService.create(dto);
        return Result.success("创建成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody HallDTO dto) {
        hallService.update(id, dto);
        return Result.success("更新成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        hallService.delete(id);
        return Result.success("删除成功");
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        hallService.updateStatus(id, status);
        return Result.success("状态更新成功");
    }
}
