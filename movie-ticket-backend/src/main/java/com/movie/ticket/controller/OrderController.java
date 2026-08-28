package com.movie.ticket.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.movie.ticket.common.Result;
import com.movie.ticket.dto.OrderCreateDTO;
import com.movie.ticket.dto.OrderPayDTO;
import com.movie.ticket.service.OrderService;
import com.movie.ticket.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<String> createOrder(@Valid @RequestBody OrderCreateDTO dto) {
        String orderNo = orderService.createOrder(dto);
        return Result.success("订单创建成功", orderNo);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Result<IPage<OrderVO>> getMyOrders(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        IPage<OrderVO> pageData = orderService.getMyOrders(page, size, status);
        return Result.success(pageData);
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public Result<IPage<OrderVO>> getViewingHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        return Result.success(orderService.getViewingHistory(page, size));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<IPage<OrderVO>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status) {
        IPage<OrderVO> pageData = orderService.getPage(page, size, orderNo, status);
        return Result.success(pageData);
    }

    @GetMapping("/{orderNo}")
    @PreAuthorize("isAuthenticated()")
    public Result<OrderVO> getByOrderNo(@PathVariable String orderNo) {
        OrderVO order = orderService.getByOrderNo(orderNo);
        return Result.success(order);
    }

    @PostMapping("/{orderNo}/pay")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> payOrder(@PathVariable String orderNo,
                                 @RequestBody(required = false) OrderPayDTO dto) {
        orderService.payOrder(orderNo, dto == null ? null : dto.getCouponId());
        return Result.success("支付成功");
    }

    @PostMapping("/{orderNo}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> cancelOrder(@PathVariable String orderNo) {
        orderService.cancelOrder(orderNo);
        return Result.success("订单已取消");
    }

    @PostMapping("/{orderNo}/refund")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> refundOrder(@PathVariable String orderNo) {
        orderService.refundOrder(orderNo);
        return Result.success("退票申请已受理");
    }

    @PostMapping("/pick")
    @PreAuthorize("isAuthenticated()")
    public Result<OrderVO> pickTicket(@RequestParam String ticketCode) {
        OrderVO order = orderService.pickTicket(ticketCode);
        return Result.success("取票成功", order);
    }
}
