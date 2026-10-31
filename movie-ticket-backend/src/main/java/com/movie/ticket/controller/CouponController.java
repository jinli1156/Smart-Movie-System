package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.dto.CouponRedeemDTO;
import com.movie.ticket.service.CouponService;
import com.movie.ticket.vo.CouponVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/** 用户优惠券接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** 查询本人优惠券，返回使用状态与有效期。 */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Result<List<CouponVO>> getMyCoupons() {
        return Result.success(couponService.getMyCoupons());
    }

    /** 校验兑换码的有效期、发放限额和重复领取限制，为本人发券。 */
    @PostMapping("/redeem")
    @PreAuthorize("isAuthenticated()")
    public Result<CouponVO> redeem(@Valid @RequestBody CouponRedeemDTO request) {
        return Result.success("兑换成功", couponService.redeem(request.getCode()));
    }
}
