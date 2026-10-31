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

/** 影厅接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/hall")
@RequiredArgsConstructor
public class HallController {

    private final HallService hallService;

    /** 按筛选条件分页查询影厅，供列表页展示。 */
    @GetMapping("/page")
    public Result<IPage<HallVO>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long cinemaId) {
        IPage<HallVO> pageData = hallService.getPage(page, size, cinemaId);
        return Result.success(pageData);
    }

    /** 读取指定影院下的影厅列表。 */
    @GetMapping("/cinema/{cinemaId}")
    public Result<List<HallVO>> getByCinemaId(@PathVariable Long cinemaId) {
        List<HallVO> halls = hallService.getByCinemaId(cinemaId);
        return Result.success(halls);
    }

    /** 根据 ID 查询影厅详情。 */
    @GetMapping("/{id}")
    public Result<HallVO> getById(@PathVariable Long id) {
        HallVO hall = hallService.getById(id);
        return Result.success(hall);
    }

    /** 新增影厅，由业务层执行参数及关联数据校验。 */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> create(@Valid @RequestBody HallDTO dto) {
        hallService.create(dto);
        return Result.success("创建成功");
    }

    /** 更新影厅，由业务层校验当前操作权限与业务条件。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody HallDTO dto) {
        hallService.update(id, dto);
        return Result.success("更新成功");
    }

    /** 删除影厅，由业务层检查权限及关联数据限制。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        hallService.delete(id);
        return Result.success("删除成功");
    }

    /** 修改影厅状态，具体可用状态和操作限制由业务层校验。 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        hallService.updateStatus(id, status);
        return Result.success("状态更新成功");
    }
}
