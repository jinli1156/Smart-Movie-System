package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.dto.PriceCompareRequest;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.service.AssistantBookingService;
import com.movie.ticket.service.MovieAssistantService;
import com.movie.ticket.vo.PriceComparisonVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 票价与优惠试算接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/pricing")
@RequiredArgsConstructor
public class PricingController {
    private final AssistantBookingService booking;

    /** 比较符合条件的场次与优惠组合，返回实付价格方案。 */
    @GetMapping("/compare")
    public Result<PriceComparisonVO> compare(@Valid @ModelAttribute PriceCompareRequest request,
                                             HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(booking.compare(MovieAssistantService.currentUserId(), request));
    }

    /** 根据当前用户资产试算价格；试算不扣券、不扣积分。 */
    @GetMapping("/orders/{orderNo}/quote")
    public Result<Map<String, Object>> quote(@PathVariable String orderNo,
            @RequestParam(defaultValue = "true") boolean usePoints, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(booking.quote(UserContext.getUserId(), null, null, orderNo, usePoints));
    }
}
