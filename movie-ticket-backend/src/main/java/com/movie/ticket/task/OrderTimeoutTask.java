package com.movie.ticket.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 定时扫描超过支付时限的待付款订单，逐笔调用独立事务完成取消和库存回补。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final TicketOrderMapper orderMapper;
    private final OrderTimeoutService orderTimeoutService;

    private static final int TIMEOUT_MINUTES = 10;
    private static final int BATCH_SIZE = 200;

    @Scheduled(cron = "0 * * * * ?")
    public void cancelTimeoutOrders() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        LambdaQueryWrapper<TicketOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketOrder::getStatus, 0)
                .lt(TicketOrder::getCreateTime, timeoutThreshold)
                .orderByAsc(TicketOrder::getCreateTime)
                .last("LIMIT " + BATCH_SIZE);

        List<TicketOrder> timeoutOrders = orderMapper.selectList(wrapper);

        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.info("发现 {} 个超时未支付订单，开始自动取消", timeoutOrders.size());

        for (TicketOrder order : timeoutOrders) {
            try {
                if (orderTimeoutService.cancelIfTimedOut(order, timeoutThreshold)) {
                    log.info("订单 {} 已自动取消（超时 {} 分钟）", order.getOrderNo(), TIMEOUT_MINUTES);
                } else {
                    log.debug("订单 {} 已由其他请求处理，跳过超时取消", order.getOrderNo());
                }
            } catch (Exception e) {
                log.error("自动取消订单 {} 失败", order.getOrderNo(), e);
            }
        }
    }
}
