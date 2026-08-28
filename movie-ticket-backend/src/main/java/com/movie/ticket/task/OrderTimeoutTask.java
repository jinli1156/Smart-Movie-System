package com.movie.ticket.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final TicketOrderMapper orderMapper;
    private final ShowtimeMapper showtimeMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String SEAT_LOCK_PREFIX = "seat:lock:";
    private static final int TIMEOUT_MINUTES = 10;

    @Scheduled(cron = "0 * * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void cancelTimeoutOrders() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        LambdaQueryWrapper<TicketOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketOrder::getStatus, 0)
                .lt(TicketOrder::getCreateTime, timeoutThreshold);

        List<TicketOrder> timeoutOrders = orderMapper.selectList(wrapper);

        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.info("发现 {} 个超时未支付订单，开始自动取消", timeoutOrders.size());

        for (TicketOrder order : timeoutOrders) {
            try {
                order.setStatus(2);
                order.setCancelTime(LocalDateTime.now());
                orderMapper.updateById(order);

                Showtime showtime = showtimeMapper.selectById(order.getShowtimeId());
                if (showtime != null) {
                    showtime.setAvailableSeats(showtime.getAvailableSeats() + order.getTicketCount());
                    showtimeMapper.updateById(showtime);
                }

                String[] seats = order.getSeats().split(",");
                for (String seat : seats) {
                    String key = SEAT_LOCK_PREFIX + order.getShowtimeId() + ":" + seat;
                    redisTemplate.delete(key);
                }

                log.info("订单 {} 已自动取消（超时 {} 分钟）", order.getOrderNo(), TIMEOUT_MINUTES);
            } catch (Exception e) {
                log.error("自动取消订单 {} 失败", order.getOrderNo(), e);
            }
        }
    }
}
