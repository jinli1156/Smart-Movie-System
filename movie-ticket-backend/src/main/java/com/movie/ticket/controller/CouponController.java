package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.service.CouponService;
import com.movie.ticket.vo.CouponVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Result<List<CouponVO>> getMyCoupons() {
        return Result.success(couponService.getMyCoupons());
    }
}
