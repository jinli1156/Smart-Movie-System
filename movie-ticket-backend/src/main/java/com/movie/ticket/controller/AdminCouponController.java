package com.movie.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.movie.ticket.common.Result;
import com.movie.ticket.dto.CouponCodeSaveDTO;
import com.movie.ticket.entity.CouponCode;
import com.movie.ticket.service.CouponAdminService;
import com.movie.ticket.vo.CouponRedemptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 优惠券兑换码管理接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/admin/coupon-codes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private final CouponAdminService couponAdminService;

    /** 按筛选条件分页查询优惠券兑换码管理，供列表页展示。 */
    @GetMapping
    public Result<IPage<CouponCode>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(couponAdminService.getPage(page, size, keyword, status));
    }

    /** 分页查询兑换码领取记录，便于核对发放情况。 */
    @GetMapping("/redemptions")
    public Result<IPage<CouponRedemptionVO>> getRedemptions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long couponCodeId,
            @RequestParam(required = false) String keyword) {
        return Result.success(couponAdminService.getRedemptions(
                page, size, couponCodeId, keyword));
    }

    /** 新增优惠券兑换码管理，由业务层执行参数及关联数据校验。 */
    @PostMapping
    public Result<CouponCode> create(@Valid @RequestBody CouponCodeSaveDTO request) {
        return Result.success("兑换码创建成功", couponAdminService.create(request));
    }

    /** 更新优惠券兑换码管理，由业务层校验当前操作权限与业务条件。 */
    @PutMapping("/{id}")
    public Result<CouponCode> update(@PathVariable Long id,
                                     @Valid @RequestBody CouponCodeSaveDTO request) {
        return Result.success("兑换码更新成功", couponAdminService.update(id, request));
    }

    /** 修改优惠券兑换码管理状态，具体可用状态和操作限制由业务层校验。 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        couponAdminService.updateStatus(id, status);
        return Result.success(status == 1 ? "兑换码已启用" : "兑换码已停用");
    }

    /** 删除优惠券兑换码管理，由业务层检查权限及关联数据限制。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        couponAdminService.delete(id);
        return Result.success("兑换码删除成功");
    }
}
