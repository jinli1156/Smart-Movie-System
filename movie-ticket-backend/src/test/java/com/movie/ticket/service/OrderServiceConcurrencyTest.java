package com.movie.ticket.service;

import com.movie.ticket.dto.OrderCreateDTO;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.CinemaMapper;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.mapper.UserMapper;
import com.movie.ticket.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceConcurrencyTest {

    @Mock private TicketOrderMapper orderMapper;
    @Mock private ShowtimeMapper showtimeMapper;
    @Mock private MovieMapper movieMapper;
    @Mock private CinemaMapper cinemaMapper;
    @Mock private HallMapper hallMapper;
    @Mock private UserMapper userMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private CouponService couponService;

    @InjectMocks
    private OrderService orderService;

    private final Map<String, String> redisLocks = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        redisLocks.clear();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> redisLocks.putIfAbsent(
                        invocation.getArgument(0), invocation.getArgument(1)) == null);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String key = ((List<String>) invocation.getArgument(1)).get(0);
                    Object scriptArgument = invocation.getArgument(2);
                    String ownerToken = scriptArgument instanceof Object[] arguments
                            ? String.valueOf(arguments[0])
                            : String.valueOf(scriptArgument);
                    return redisLocks.remove(key, ownerToken) ? 1L : 0L;
                });
        when(showtimeMapper.selectById(1L)).thenAnswer(invocation -> saleableShowtime());
        when(orderMapper.countActiveOrdersBySeat(anyLong(), anyString())).thenReturn(0L);
        when(showtimeMapper.reserveAvailableSeats(anyLong(), anyInt())).thenReturn(1);
        when(orderMapper.insert(any())).thenReturn(1);
    }

    @Test
    void onlyOneConcurrentBuyerCanAcquireTheSameRedisSeatLock() throws Exception {
        int buyers = 12;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> results = java.util.stream.IntStream.range(0, buyers)
                    .mapToObj(index -> pool.submit(() -> {
                        authenticate((long) index + 1);
                        ready.countDown();
                        start.await(5, TimeUnit.SECONDS);
                        try {
                            orderService.createOrder(order("1排1座"));
                            return true;
                        } catch (BusinessException expected) {
                            return false;
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    }))
                    .toList();

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            long successfulBuyers = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    successfulBuyers++;
                }
            }

            assertEquals(1, successfulBuyers);
            assertTrue(redisLocks.containsKey("seat:lock:1:1排1座"));
            verify(orderMapper, times(1)).insert(any());
            verify(showtimeMapper, times(1)).reserveAvailableSeats(1L, 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void databaseOccupancyStillBlocksASeatAfterRedisLockExpiry() {
        authenticate(1L);
        when(orderMapper.countActiveOrdersBySeat(1L, "2排3座")).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> orderService.createOrder(order("2排3座")));

        assertEquals("座位 2排3座 已被占用", error.getMessage());
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
        verify(showtimeMapper, never()).reserveAvailableSeats(anyLong(), anyInt());
    }

    @Test
    void partialLockFailureReleasesOnlyLocksAcquiredByTheCurrentRequest() {
        authenticate(1L);
        redisLocks.put("seat:lock:1:3排4座", "another-buyer");

        assertThrows(BusinessException.class,
                () -> orderService.createOrder(order("3排3座", "3排4座")));

        assertEquals(Map.of("seat:lock:1:3排4座", "another-buyer"), redisLocks);
        verify(showtimeMapper, never()).reserveAvailableSeats(anyLong(), anyInt());
    }

    @Test
    void duplicateCancellationRestoresInventoryOnlyOnce() {
        authenticate(1L);
        TicketOrder order = pendingOrder();
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.cancelIfPending(eq(order.getId()), any(LocalDateTime.class)))
                .thenReturn(1, 0);
        when(showtimeMapper.restoreAvailableSeats(order.getShowtimeId(), order.getTicketCount()))
                .thenReturn(1);

        orderService.cancelOrder(order.getOrderNo());
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(order.getOrderNo()));

        assertEquals(409, conflict.getCode());
        verify(showtimeMapper, times(1))
                .restoreAvailableSeats(order.getShowtimeId(), order.getTicketCount());
        verify(redisTemplate, times(1)).execute(
                any(RedisScript.class), eq(List.of("seat:lock:1:4排5座")), any(Object[].class));
    }

    @Test
    void paymentUpdatesPointsWithoutWritingNullableProfileFields() {
        authenticate(1L);
        try {
            TicketOrder order = pendingOrder();
            order.setOriginalPrice(new BigDecimal("100.00"));
            order.setTotalPrice(new BigDecimal("100.00"));
            com.movie.ticket.entity.User user = new com.movie.ticket.entity.User();
            user.setId(1L);
            user.setPoints(new BigDecimal("1280.0"));
            when(orderMapper.selectOne(any())).thenReturn(order);
            when(userMapper.selectForUpdate(1L)).thenReturn(user);
            when(orderMapper.markPaidIfPending(anyLong(), anyLong(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.isNull(), any(), anyString())).thenReturn(1);
            when(userMapper.updatePointsOnly(eq(1L), any())).thenReturn(1);

            orderService.payOrder(order.getOrderNo(), null, BigDecimal.ZERO);

            verify(userMapper).updatePointsOnly(1L, new BigDecimal("1330.0"));
            verify(userMapper, never()).updateById(any(com.movie.ticket.entity.User.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Showtime saleableShowtime() {
        Showtime showtime = new Showtime();
        showtime.setId(1L);
        showtime.setStartTime(LocalDateTime.now().plusHours(2));
        showtime.setBasePrice(new BigDecimal("48.00"));
        showtime.setTotalSeats(100);
        showtime.setAvailableSeats(100);
        showtime.setStatus(0);
        return showtime;
    }

    private OrderCreateDTO order(String... seats) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setShowtimeId(1L);
        dto.setSeats(List.of(seats));
        return dto;
    }

    private TicketOrder pendingOrder() {
        TicketOrder order = new TicketOrder();
        order.setId(101L);
        order.setOrderNo("ORDER-CONCURRENCY-101");
        order.setUserId(1L);
        order.setShowtimeId(1L);
        order.setSeats("4排5座");
        order.setTicketCount(1);
        order.setStatus(0);
        return order;
    }

    private void authenticate(Long userId) {
        UserDetailsImpl principal = new UserDetailsImpl(userId, "buyer-" + userId, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
