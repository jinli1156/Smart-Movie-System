package com.movie.ticket.service;

import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

/** 超时订单回收：条件更新订单状态，回补余票，并在事务提交后释放该订单持有的座位锁。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutService {

    private static final String SEAT_LOCK_PREFIX = "seat:lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final TicketOrderMapper orderMapper;
    private final ShowtimeMapper showtimeMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 每笔超时订单使用独立事务。多个调度实例可以读取到同一候选订单，
     * 但只有一个实例能完成 status=0 -> status=2 的条件更新。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean cancelIfTimedOut(TicketOrder order, LocalDateTime timeoutThreshold) {
        LocalDateTime cancelTime = LocalDateTime.now();
        if (orderMapper.cancelIfTimedOut(order.getId(), timeoutThreshold, cancelTime) != 1) {
            return false;
        }

        if (showtimeMapper.restoreAvailableSeats(order.getShowtimeId(), order.getTicketCount()) != 1) {
            throw new IllegalStateException("超时订单余票回补失败，订单号：" + order.getOrderNo());
        }

        unlockAfterCommit(order);
        return true;
    }

    private void unlockAfterCommit(TicketOrder order) {
        Runnable unlockAction = () -> Arrays.stream(order.getSeats().split(","))
                .forEach(seat -> {
                    try {
                        String key = SEAT_LOCK_PREFIX + order.getShowtimeId() + ":" + seat;
                        redisTemplate.execute(
                                UNLOCK_SCRIPT, Collections.singletonList(key), order.getOrderNo());
                    } catch (RuntimeException exception) {
                        // Redis 锁有 TTL；数据库订单与余票已经是最终事实。
                        log.warn("删除超时订单座位锁失败，orderNo={}, seat={}",
                                order.getOrderNo(), seat, exception);
                    }
                });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    unlockAction.run();
                }
            });
        } else {
            unlockAction.run();
        }
    }
}
