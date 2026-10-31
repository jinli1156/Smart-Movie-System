package com.movie.ticket.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.dto.LoginRequest;
import com.movie.ticket.dto.OrderCreateDTO;
import com.movie.ticket.dto.UserProfileUpdateDTO;
import com.movie.ticket.entity.Cinema;
import com.movie.ticket.entity.Hall;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.entity.User;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.CinemaMapper;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.mapper.UserMapper;
import com.movie.ticket.security.UserDetailsImpl;
import com.movie.ticket.service.AuthService;
import com.movie.ticket.service.OrderService;
import com.movie.ticket.service.OrderTimeoutService;
import com.movie.ticket.service.UserProfileService;
import com.movie.ticket.vo.LoginResponse;
import com.movie.ticket.vo.OrderVO;
import com.movie.ticket.vo.UserProfileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
class RealInfrastructureIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("movie_ticket")
            .withUsername("root")
            .withPassword("movie_test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.database", () -> 0);
    }

    @Autowired private AuthService authService;
    @Autowired private OrderService orderService;
    @Autowired private OrderTimeoutService orderTimeoutService;
    @Autowired private UserProfileService userProfileService;
    @Autowired private UserMapper userMapper;
    @Autowired private CinemaMapper cinemaMapper;
    @Autowired private HallMapper hallMapper;
    @Autowired private MovieMapper movieMapper;
    @Autowired private ShowtimeMapper showtimeMapper;
    @Autowired private TicketOrderMapper orderMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User buyer;
    private Showtime showtime;

    @BeforeEach
    void setUpFixture() {
        SecurityContextHolder.clearContext();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        jdbcTemplate.update("DELETE FROM comment");
        jdbcTemplate.update("DELETE FROM user_movie_behavior");
        jdbcTemplate.update("DELETE FROM user_coupon");
        jdbcTemplate.update("DELETE FROM ticket_order");
        jdbcTemplate.update("DELETE FROM showtime");
        jdbcTemplate.update("DELETE FROM movie");
        jdbcTemplate.update("DELETE FROM hall");
        jdbcTemplate.update("DELETE FROM cinema");

        buyer = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "user1"));
        assertNotNull(buyer);
        showtime = createShowtimeFixture();
    }

    @Test
    void loginOrderPayPickAndRefundUseRealMySqlAndRedis() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("user1");
        loginRequest.setPassword("admin123");
        LoginResponse login = authService.login(loginRequest);
        assertTrue(login.getToken().length() > 40);

        authenticate(buyer);
        String pickedOrderNo = orderService.createOrder(order("1排1座"));
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey("1排1座"))));

        orderService.payOrder(pickedOrderNo, null);
        TicketOrder paid = findOrder(pickedOrderNo);
        assertEquals(1, paid.getStatus());
        assertNotNull(paid.getTicketCode());

        OrderVO picked = orderService.pickTicket(paid.getTicketCode());
        assertEquals(1, picked.getIsPicked());
        assertNotNull(picked.getPickTime());

        String refundedOrderNo = orderService.createOrder(order("1排2座"));
        orderService.payOrder(refundedOrderNo, null);
        orderService.refundOrder(refundedOrderNo);

        TicketOrder refunded = findOrder(refundedOrderNo);
        assertEquals(3, refunded.getStatus());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey("1排2座"))));
        assertEquals(showtime.getTotalSeats() - 1,
                showtimeMapper.selectById(showtime.getId()).getAvailableSeats());
    }

    @Test
    void concurrentRequestsCannotOversellTheSameSeat() throws Exception {
        int buyers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> results = java.util.stream.IntStream.range(0, buyers)
                    .mapToObj(index -> pool.submit(() -> {
                        authenticate(buyer);
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        try {
                            orderService.createOrder(order("2排2座"));
                            return true;
                        } catch (BusinessException expected) {
                            return false;
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    }))
                    .toList();

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            long successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(20, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            assertEquals(1, successCount);
            assertEquals(1, orderMapper.countActiveOrdersBySeat(showtime.getId(), "2排2座"));
            assertEquals(showtime.getTotalSeats() - 1,
                    showtimeMapper.selectById(showtime.getId()).getAvailableSeats());
            assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey("2排2座"))));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void paymentAndTimeoutCancellationHaveExactlyOneWinner() throws Exception {
        authenticate(buyer);
        String orderNo = orderService.createOrder(order("3排3座"));
        TicketOrder pending = findOrder(orderNo);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> payment = pool.submit(() -> {
                authenticate(buyer);
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                    orderService.payOrder(orderNo, null);
                    return true;
                } catch (BusinessException expected) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            Future<Boolean> timeout = pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return orderTimeoutService.cancelIfTimedOut(
                        pending, LocalDateTime.now().plusMinutes(1));
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            boolean paid = payment.get(20, TimeUnit.SECONDS);
            boolean cancelled = timeout.get(20, TimeUnit.SECONDS);

            assertTrue(paid ^ cancelled);
            TicketOrder finalOrder = findOrder(orderNo);
            assertEquals(paid ? 1 : 2, finalOrder.getStatus());
            assertEquals(paid ? showtime.getTotalSeats() - 1 : showtime.getTotalSeats(),
                    showtimeMapper.selectById(showtime.getId()).getAvailableSeats());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void pickupAndRefundHaveExactlyOneWinner() throws Exception {
        authenticate(buyer);
        String orderNo = orderService.createOrder(order("3排4座"));
        orderService.payOrder(orderNo, null);
        String ticketCode = findOrder(orderNo).getTicketCode();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> pickup = pool.submit(() -> {
                authenticate(buyer);
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                    orderService.pickTicket(ticketCode);
                    return true;
                } catch (BusinessException expected) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            Future<Boolean> refund = pool.submit(() -> {
                authenticate(buyer);
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                    orderService.refundOrder(orderNo);
                    return true;
                } catch (BusinessException expected) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            boolean picked = pickup.get(20, TimeUnit.SECONDS);
            boolean refunded = refund.get(20, TimeUnit.SECONDS);

            assertTrue(picked ^ refunded);
            TicketOrder finalOrder = findOrder(orderNo);
            assertEquals(refunded ? 3 : 1, finalOrder.getStatus());
            assertEquals(picked ? 1 : 0, finalOrder.getIsPicked());
            assertEquals(refunded ? showtime.getTotalSeats() : showtime.getTotalSeats() - 1,
                    showtimeMapper.selectById(showtime.getId()).getAvailableSeats());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void profileFieldsRoundTripThroughTheRealDatabase() {
        authenticate(buyer);
        UserProfileUpdateDTO profile = new UserProfileUpdateDTO();
        profile.setNickname("资料持久化测试用户");
        profile.setGender(2);
        profile.setBirthday(LocalDate.of(1998, 6, 18));
        profile.setCity("南宁");
        profile.setAddress("青秀区测试路 18 号");
        profile.setFavoriteGenres(List.of("剧情", "科幻"));
        profile.setSignature("每一场电影都是一次旅行");

        UserProfileVO saved = userProfileService.updateCurrentProfile(profile);
        User persisted = userMapper.selectById(buyer.getId());

        assertEquals(profile.getNickname(), saved.getNickname());
        assertEquals(profile.getGender(), persisted.getGender());
        assertEquals(profile.getBirthday(), persisted.getBirthday());
        assertEquals(profile.getCity(), persisted.getCity());
        assertEquals(profile.getAddress(), persisted.getAddress());
        assertEquals(profile.getFavoriteGenres(), persisted.getFavoriteGenres());
        assertEquals(profile.getSignature(), persisted.getSignature());
    }

    private Showtime createShowtimeFixture() {
        Cinema cinema = new Cinema();
        cinema.setName("Testcontainers 影院");
        cinema.setAddress("集成测试路 1 号");
        cinema.setStatus(1);
        cinemaMapper.insert(cinema);

        Hall hall = new Hall();
        hall.setCinemaId(cinema.getId());
        hall.setName("真实依赖测试厅");
        hall.setTotalRows(5);
        hall.setTotalCols(5);
        hall.setTotalSeats(25);
        hall.setSeatLayout(null);
        hall.setStatus(1);
        hallMapper.insert(hall);

        Movie movie = new Movie();
        movie.setTitle("真实集成测试电影");
        movie.setDuration(120);
        movie.setReleaseDate(LocalDate.now());
        movie.setStatus(1);
        movieMapper.insert(movie);

        Showtime fixture = new Showtime();
        fixture.setMovieId(movie.getId());
        fixture.setCinemaId(cinema.getId());
        fixture.setHallId(hall.getId());
        fixture.setStartTime(LocalDateTime.now().plusDays(2));
        fixture.setEndTime(LocalDateTime.now().plusDays(2).plusHours(2));
        fixture.setBasePrice(new BigDecimal("50.00"));
        fixture.setTotalSeats(25);
        fixture.setAvailableSeats(25);
        fixture.setStatus(0);
        showtimeMapper.insert(fixture);
        return fixture;
    }

    private OrderCreateDTO order(String seat) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setShowtimeId(showtime.getId());
        dto.setSeats(List.of(seat));
        return dto;
    }

    private TicketOrder findOrder(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getOrderNo, orderNo));
    }

    private String lockKey(String seat) {
        return "seat:lock:" + showtime.getId() + ":" + seat;
    }

    private void authenticate(User user) {
        UserDetailsImpl principal = new UserDetailsImpl(user.getId(), user.getUsername(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
