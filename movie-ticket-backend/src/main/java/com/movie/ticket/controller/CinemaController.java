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

/** 影院接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/cinema")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;

    /** 按筛选条件分页查询影院，供列表页展示。 */
    @GetMapping("/page")
    public Result<IPage<CinemaVO>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        IPage<CinemaVO> pageData = cinemaService.getPage(page, size, keyword);
        return Result.success(pageData);
    }

    /** 读取影院列表，供选择器或公开目录使用。 */
    @GetMapping("/all")
    public Result<List<CinemaVO>> getAll() {
        List<CinemaVO> cinemas = cinemaService.getAll();
        return Result.success(cinemas);
    }

    /** 根据 ID 查询影院详情。 */
    @GetMapping("/{id}")
    public Result<CinemaVO> getById(@PathVariable Long id) {
        CinemaVO cinema = cinemaService.getById(id);
        return Result.success(cinema);
    }

    /** 新增影院，由业务层执行参数及关联数据校验。 */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@Valid @RequestBody CinemaDTO dto) {
        cinemaService.create(dto);
        return Result.success("创建成功");
    }

    /** 更新影院，由业务层校验当前操作权限与业务条件。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CinemaDTO dto) {
        cinemaService.update(id, dto);
        return Result.success("更新成功");
    }

    /** 删除影院，由业务层检查权限及关联数据限制。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        cinemaService.delete(id);
        return Result.success("删除成功");
    }

    /** 修改影院状态，具体可用状态和操作限制由业务层校验。 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        cinemaService.updateStatus(id, status);
        return Result.success("状态更新成功");
    }
}
