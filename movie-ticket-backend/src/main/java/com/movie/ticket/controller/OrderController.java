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

/** 订单接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 校验场次与座位，获取座位锁并扣减余票，创建待支付订单。 */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<String> createOrder(@Valid @RequestBody OrderCreateDTO dto) {
        String orderNo = orderService.createOrder(dto);
        return Result.success("订单创建成功", orderNo);
    }

    /** 仅分页查询当前登录用户的订单。 */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Result<IPage<OrderVO>> getMyOrders(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        IPage<OrderVO> pageData = orderService.getMyOrders(page, size, status);
        return Result.success(pageData);
    }

    /** 查询本人已完成观影的购票记录。 */
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public Result<IPage<OrderVO>> getViewingHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        return Result.success(orderService.getViewingHistory(page, size));
    }

    /** 按筛选条件分页查询订单，供列表页展示。 */
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

    /** 按订单号读取详情，并校验本人或管理员的数据访问范围。 */
    @GetMapping("/{orderNo}")
    @PreAuthorize("isAuthenticated()")
    public Result<OrderVO> getByOrderNo(@PathVariable String orderNo) {
        OrderVO order = orderService.getByOrderNo(orderNo);
        return Result.success(order);
    }

    /** 校验订单状态、优惠券和积分，完成支付并记录取票凭证及积分变化。 */
    @PostMapping("/{orderNo}/pay")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> payOrder(@PathVariable String orderNo,
                                 @RequestBody(required = false) OrderPayDTO dto) {
        if (dto == null || dto.getPoints() == null) {
            // 保留旧客户端调用方式，兼容只传优惠券的支付请求。
            orderService.payOrder(orderNo, dto == null ? null : dto.getCouponId());
        } else {
            orderService.payOrder(orderNo, dto.getCouponId(), dto.getPoints());
        }
        return Result.success("支付成功");
    }

    /** 取消符合条件的待支付订单，并回补余票及释放座位锁。 */
    @PostMapping("/{orderNo}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> cancelOrder(@PathVariable String orderNo) {
        orderService.cancelOrder(orderNo);
        return Result.success("订单已取消");
    }

    /** 按退票规则更新退款状态，返还优惠券和积分，并恢复座位库存。 */
    @PostMapping("/{orderNo}/refund")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> refundOrder(@PathVariable String orderNo) {
        orderService.refundOrder(orderNo);
        return Result.success("退票申请已受理");
    }

    /** 通过取票码核验已支付订单，并记录取票状态。 */
    @PostMapping("/pick")
    @PreAuthorize("isAuthenticated()")
    public Result<OrderVO> pickTicket(@RequestParam String ticketCode) {
        OrderVO order = orderService.pickTicket(ticketCode);
        return Result.success("取票成功", order);
    }
}
