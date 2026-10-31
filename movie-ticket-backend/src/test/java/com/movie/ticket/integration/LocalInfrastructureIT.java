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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "LOCAL_INFRA_TEST", matches = "1")
class LocalInfrastructureIT {

    @org.springframework.boot.test.web.server.LocalServerPort
    private int httpPort;

    // Always creates a new database and an isolated Redis process.
    private static final String DATABASE = "movie_ticket_test_" + java.util.UUID.randomUUID().toString().replace("-", "");
    private static String username, password, serverUrl;
    private static redis.embedded.RedisServer privateRedis;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) throws Exception {
        var yaml = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        yaml.setResources(new org.springframework.core.io.ClassPathResource("application.yml"));
        var props = yaml.getObject();
        var env = new org.springframework.core.env.StandardEnvironment();
        username = env.resolvePlaceholders(props.getProperty("spring.datasource.username"));
        password = env.resolvePlaceholders(props.getProperty("spring.datasource.password"));
        serverUrl = "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (var c = java.sql.DriverManager.getConnection(serverUrl, username, password); var s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { if (privateRedis != null) privateRedis.stop(); } catch (Exception ignored) { }
            try (var c = java.sql.DriverManager.getConnection(serverUrl, username, password); var s = c.createStatement()) {
                s.executeUpdate("DROP DATABASE `" + DATABASE + "`");
            } catch (Exception e) { System.err.println("Temporary test database cleanup failed: " + DATABASE); }
        }));
        int port;
        try (var socket = new java.net.ServerSocket(0)) { port = socket.getLocalPort(); }
        var executable = java.nio.file.Files.createTempFile("ticket-test-redis-", ".exe");
        try (var stream = LocalInfrastructureIT.class.getClassLoader().getResourceAsStream("redis-server-5.0.14.1-windows-amd64.exe")) {
            java.nio.file.Files.copy(java.util.Objects.requireNonNull(stream), executable, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        executable.toFile().deleteOnExit();
        privateRedis = new redis.embedded.RedisServer(port, executable.toFile());
        privateRedis.start();
        registry.add("spring.datasource.url", () -> serverUrl.replace("/?", "/" + DATABASE + "?"));
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> port);
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.database", () -> 0);
        registry.add("logging.level.com.movie.ticket", () -> "WARN");
        registry.add("logging.level.org.springframework.security", () -> "WARN");
        registry.add("mybatis-plus.configuration.log-impl", () -> "org.apache.ibatis.logging.nologging.NoLoggingImpl");
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
    @Autowired private com.movie.ticket.service.AssistantCheckoutService assistantCheckout;
    @Autowired private com.movie.ticket.utils.JwtUtil loadJwt;

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
        jdbcTemplate.update("DELETE FROM assistant_checkout");
        jdbcTemplate.update("DELETE FROM ticket_order");
        jdbcTemplate.update("DELETE FROM showtime");
        jdbcTemplate.update("DELETE FROM movie");
        jdbcTemplate.update("DELETE FROM hall");
        jdbcTemplate.update("UPDATE `user` SET cinema_id=NULL WHERE cinema_id IS NOT NULL");
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
    void assistantCheckoutConfirmsAndRetriesWithRealMysqlDatetime() throws Exception {
        authenticate(buyer);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var criteria = mapper.createObjectNode().put("showtimeId", showtime.getId()).put("people", 2)
                .put("usePoints", false).put("useCoupons", false);
        var draft = assistantCheckout.prepare(buyer.getId(), criteria, List.of("1排1座", "1排2座"));
        String id = draft.path("draftId").asText();
        org.junit.jupiter.api.Assertions.assertInstanceOf(LocalDateTime.class,
                jdbcTemplate.queryForMap("SELECT expires_at FROM assistant_checkout WHERE id=?", id).get("expires_at"));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_order", Long.class));
        var first = assistantCheckout.confirm(id);
        var second = assistantCheckout.confirm(id);
        assertEquals(first.get("orderNo"), second.get("orderNo"));
        assertEquals(0, first.get("status"));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_order", Long.class));
        assertEquals(23, showtimeMapper.selectById(showtime.getId()).getAvailableSeats());
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey("1排1座"))));
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey("1排2座"))));
        assertEquals(new BigDecimal("100.00"), findOrder(first.get("orderNo").toString()).getTotalPrice());

        var expired = assistantCheckout.prepare(buyer.getId(), criteria, List.of("2排1座", "2排2座"));
        jdbcTemplate.update("UPDATE assistant_checkout SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=?", expired.path("draftId").asText());
        var error = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> assistantCheckout.confirm(expired.path("draftId").asText()));
        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("过期"));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_order", Long.class));
        assertEquals(23, showtimeMapper.selectById(showtime.getId()).getAvailableSeats());
    }

    @org.junit.jupiter.api.RepeatedTest(5)
    void concurrentRequestsCannotOversellTheSameSeat() throws Exception {
        int buyers = 50;
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

    @org.junit.jupiter.api.RepeatedTest(5)
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

    @org.junit.jupiter.api.RepeatedTest(5)
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

    @org.junit.jupiter.api.RepeatedTest(3)
    void concurrentBuyersFillAllSeatsWithoutNegativeInventory() throws Exception {
        long winners = race(50, index -> {
            int seat = index % 25;
            orderService.createOrder(order((seat / 5 + 1) + "排" + (seat % 5 + 1) + "座"));
        });
        assertEquals(25, winners);
        assertEquals(0, showtimeMapper.selectById(showtime.getId()).getAvailableSeats());
        assertEquals(25L, orderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getShowtimeId, showtime.getId()).eq(TicketOrder::getStatus, 0)));
        for (int seat = 0; seat < 25; seat++) {
            assertEquals(1, orderMapper.countActiveOrdersBySeat(showtime.getId(),
                    (seat / 5 + 1) + "排" + (seat % 5 + 1) + "座"));
        }
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void simultaneousCancellationsRestoreInventoryExactlyOnce() throws Exception {
        authenticate(buyer);
        String orderNo = orderService.createOrder(order("4排4座"));
        long winners = race(20, index -> orderService.cancelOrder(orderNo));
        assertEquals(1, winners);
        assertEquals(2, findOrder(orderNo).getStatus());
        assertEquals(25, showtimeMapper.selectById(showtime.getId()).getAvailableSeats());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey("4排4座"))));
    }

    private long race(int requests, java.util.function.IntConsumer action) throws Exception {
        var pool = Executors.newFixedThreadPool(requests);
        var ready = new CountDownLatch(requests);
        var start = new CountDownLatch(1);
        var durations = new java.util.concurrent.ConcurrentLinkedQueue<Long>();
        try {
            var results = java.util.stream.IntStream.range(0, requests).mapToObj(index -> pool.submit(() -> {
                authenticate(buyer);
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                long began = System.nanoTime();
                try { action.accept(index); return true; }
                catch (BusinessException expected) { return false; }
                finally {
                    durations.add(System.nanoTime() - began);
                    SecurityContextHolder.clearContext();
                }
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            long began = System.nanoTime();
            start.countDown();
            long winners = 0;
            for (var result : results) if (result.get(30, TimeUnit.SECONDS)) winners++;
            double seconds = (System.nanoTime() - began) / 1_000_000_000.0;
            var sorted = durations.stream().sorted().toList();
            System.out.printf(java.util.Locale.ROOT,
                    "CONCURRENCY requests=%d accepted=%d rejected=%d elapsedMs=%.2f p95Ms=%.2f operationsPerSecond=%.2f%n",
                    requests, winners, requests - winners, seconds * 1000,
                    sorted.get((int) Math.ceil(requests * 0.95) - 1) / 1_000_000.0, requests / seconds);
            return winners;
        } finally { pool.shutdownNow(); }
    }

    @Test
    void publicMovieHttpEndpointHandlesConcurrentReads() throws Exception {
        try (var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()) {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                    "http://127.0.0.1:" + httpPort + "/api/movie/page?page=1&size=10"))
                    .timeout(java.time.Duration.ofSeconds(10)).GET().build();
            assertEquals(200, client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            for (int concurrency : new int[]{10, 25, 50}) {
                var pool = Executors.newFixedThreadPool(concurrency);
                var durations = new java.util.concurrent.ConcurrentLinkedQueue<Long>();
                var start = new CountDownLatch(1);
                int count = 200;
                try {
                    var tasks = java.util.stream.IntStream.range(0, count).mapToObj(index -> pool.submit(() -> {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        long began = System.nanoTime();
                        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                        durations.add(System.nanoTime() - began);
                        assertEquals(200, response.statusCode());
                        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
                        assertEquals(200, json.path("code").asInt());
                        assertTrue(json.path("data").path("records").isArray());
                        return true;
                    })).toList();
                    long began = System.nanoTime(); start.countDown();
                    for (var task : tasks) assertTrue(task.get(30, TimeUnit.SECONDS));
                    double seconds = (System.nanoTime() - began) / 1_000_000_000.0;
                    var sorted = durations.stream().sorted().toList();
                    System.out.printf(java.util.Locale.ROOT,
                            "HTTP_LOAD concurrency=%d requests=%d errors=0 elapsedMs=%.2f p95Ms=%.2f requestsPerSecond=%.2f%n",
                            concurrency, count, seconds * 1000, sorted.get(189) / 1_000_000.0, count / seconds);
                } finally { pool.shutdownNow(); }
            }
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "LOAD_200", matches = "1")
    void twoHundredUsersForFiveMinutes() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        // Validate report queries before spending five minutes generating traffic.
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM showtime s WHERE s.available_seats <> s.total_seats - (SELECT COALESCE(SUM(o.seat_count),0) FROM ticket_order o WHERE o.showtime_id=s.id AND o.status IN (0,1))", Long.class);
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (SELECT showtime_id,seats FROM ticket_order WHERE status IN (0,1) GROUP BY showtime_id,seats HAVING COUNT(*)>1) x", Long.class);
        jdbcTemplate.queryForList("SELECT status,COUNT(*) AS orders FROM ticket_order GROUP BY status");
        var metrics = new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentLinkedQueue<Long>>();
        var errors = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var samples = new java.util.ArrayList<java.util.Map<String, Object>>();
        var count = new java.util.concurrent.atomic.AtomicLong();
        var active = new java.util.concurrent.atomic.AtomicInteger();
        var peak = new java.util.concurrent.atomic.AtomicInteger();
        var finished = new CountDownLatch(200);
        var ready = new CountDownLatch(200);
        var start = new CountDownLatch(1);
        var deadline = new java.util.concurrent.atomic.AtomicLong();
        var fixtures = new java.util.ArrayList<Showtime>();
        fixtures.add(showtime);
        for (int i = 1; i < 8; i++) fixtures.add(createShowtimeFixture());
        var tokens = new java.util.ArrayList<String>();
        for (int i = 0; i < 200; i++) {
            var user = new User(); user.setUsername("load200_" + i); user.setPassword(buyer.getPassword());
            user.setNickname("Load user " + i); user.setRole("USER"); user.setStatus(1); user.setPoints(BigDecimal.ZERO);
            userMapper.insert(user); tokens.add(loadJwt.generateToken(user.getId(), user.getUsername(), "USER"));
        }
        // Turn off verbose SQL/security output for this bounded load test only.
        var context = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        for (var logger : context.getLoggerList()) logger.setLevel(ch.qos.logback.classic.Level.WARN);
        var pool = Executors.newFixedThreadPool(200);
        var os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        var runtime = Runtime.getRuntime();
        try (var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()) {
            for (int i = 0; i < 200; i++) {
                final int index = i;
                pool.submit(() -> {
                    ready.countDown();
                    String orderNo = null;
                    int step = 0, cycle = 0;
                    var fixture = fixtures.get(index / 25);
                    String seat = ((index % 25) / 5 + 1) + "排" + (index % 5 + 1) + "座";
                    try {
                        start.await();
                        Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextInt(1000));
                        while (System.nanoTime() < deadline.get()) {
                            String label, path, body = null;
                            switch (step) {
                                case 0 -> { label = "movies"; path = "/movie/page?page=1&size=10"; }
                                case 1 -> { label = "movieDetail"; path = "/movie/" + fixture.getMovieId(); }
                                case 2 -> { label = "cinemas"; path = "/cinema/page?page=1&size=10"; }
                                case 3 -> { label = "showtimes"; path = "/showtime/page?movieId=" + fixture.getMovieId(); }
                                case 4 -> { label = "seats"; path = "/showtime/" + fixture.getId() + "/seats"; }
                                case 5 -> { label = "myOrders"; path = "/order/my?page=1&size=10"; }
                                case 6 -> { label = "createOrder"; path = "/order"; body = mapper.writeValueAsString(java.util.Map.of("showtimeId", fixture.getId(), "seats", List.of(seat))); }
                                case 7 -> { label = "orderDetail"; path = "/order/" + orderNo; }
                                case 8 -> { label = cycle % 2 == 0 ? "pay" : "cancel"; path = "/order/" + orderNo + "/" + label; body = "{\"points\":0}"; }
                                default -> { label = cycle % 2 == 0 ? "refund" : "myOrders"; path = cycle % 2 == 0 ? "/order/" + orderNo + "/refund" : "/order/my"; if (cycle % 2 == 0) body = "{}"; }
                            }
                            var builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + httpPort + "/api" + path))
                                    .timeout(java.time.Duration.ofSeconds(10)).header("Authorization", "Bearer " + tokens.get(index))
                                    .header("Content-Type", "application/json");
                            var request = body == null ? builder.GET().build() : builder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
                            long began = System.nanoTime();
                            int inFlight = active.incrementAndGet(); peak.accumulateAndGet(inFlight, Math::max);
                            try {
                                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                                var json = mapper.readTree(response.body());
                                if (response.statusCode() != 200 || json.path("code").asInt() != 200) {
                                    errors.add(label + " HTTP=" + response.statusCode() + " code=" + json.path("code") + " message=" + json.path("message").asText());
                                    // Stop this user on unexpected failure so later requests do not hide the initial error.
                                    break;
                                }
                                if (step == 6) orderNo = json.path("data").asText();
                            } catch (Exception e) { errors.add(label + " " + e.getClass().getSimpleName() + ": " + e.getMessage()); break; }
                            finally {
                                active.decrementAndGet(); count.incrementAndGet();
                                metrics.computeIfAbsent(label, k -> new java.util.concurrent.ConcurrentLinkedQueue<>()).add(System.nanoTime() - began);
                            }
                            step++;
                            if (step == 10) { step = 0; cycle++; orderNo = null; }
                            Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextInt(500, 1501));
                        }
                    } catch (Exception e) { errors.add("user " + index + ": " + e); }
                    finally { finished.countDown(); }
                });
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS));
            long began = System.nanoTime(); deadline.set(began + TimeUnit.SECONDS.toNanos(300)); start.countDown();
            System.out.println("LOAD200 START users=200 durationSeconds=300 thinkTimeMs=500..1500");
            while (System.nanoTime() < deadline.get()) {
                Thread.sleep(10000);
                var sample = new java.util.LinkedHashMap<String, Object>();
                sample.put("elapsedSeconds", (System.nanoTime() - began) / 1_000_000_000L);
                sample.put("requests", count.get()); sample.put("errors", errors.size());
                sample.put("virtualUsersRemaining", finished.getCount()); sample.put("inFlight", active.get());
                sample.put("jvmHeapMB", (runtime.totalMemory() - runtime.freeMemory()) / 1048576);
                sample.put("processCpuPercent", os.getProcessCpuLoad() * 100); sample.put("systemCpuPercent", os.getCpuLoad() * 100);
                sample.put("freePhysicalMB", os.getFreeMemorySize() / 1048576);
                samples.add(sample); System.out.println("LOAD200 SAMPLE " + mapper.writeValueAsString(sample));
            }
            assertTrue(finished.await(20, TimeUnit.SECONDS));
            var endpointStats = new java.util.TreeMap<String, Object>();
            var allTimes = new java.util.ArrayList<Long>();
            metrics.forEach((name, times) -> { allTimes.addAll(times); endpointStats.put(name, loadPercentiles(times)); });
            var rawOutput = java.nio.file.Path.of("../output/load-200-users-5min");
            java.nio.file.Files.createDirectories(rawOutput);
            mapper.writerWithDefaultPrettyPrinter().writeValue(rawOutput.resolve("traffic.json").toFile(),
                    java.util.Map.of("requests", count.get(), "errors", errors.size(), "latency", loadPercentiles(allTimes), "endpoints", endpointStats, "samples", samples));
            var invariants = new java.util.LinkedHashMap<String, Object>();
            long inventoryMismatch = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM showtime s WHERE s.available_seats <> s.total_seats - (SELECT COALESCE(SUM(o.seat_count),0) FROM ticket_order o WHERE o.showtime_id=s.id AND o.status IN (0,1))", Long.class);
            long duplicateSeats = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (SELECT showtime_id,seats FROM ticket_order WHERE status IN (0,1) GROUP BY showtime_id,seats HAVING COUNT(*)>1) x", Long.class);
            long negativeStock = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM showtime WHERE available_seats<0 OR available_seats>total_seats", Long.class);
            invariants.put("inventoryMismatch", inventoryMismatch); invariants.put("duplicateActiveSeats", duplicateSeats); invariants.put("invalidStock", negativeStock);
            invariants.put("ordersByStatus", jdbcTemplate.queryForList("SELECT status,COUNT(*) AS orders FROM ticket_order GROUP BY status"));
            var report = new java.util.LinkedHashMap<String, Object>();
            report.put("users", 200); report.put("durationSeconds", 300); report.put("thinkTimeMs", "500..1500");
            report.put("requests", count.get()); report.put("requestsPerSecond", count.get() / 300.0); report.put("peakInFlight", peak.get());
            report.put("errors", errors.size()); report.put("errorExamples", errors.stream().limit(20).toList());
            report.put("latency", loadPercentiles(allTimes)); report.put("endpoints", endpointStats); report.put("samples", samples); report.put("invariants", invariants);
            var output = java.nio.file.Path.of("../output/load-200-users-5min"); java.nio.file.Files.createDirectories(output);
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("results.json").toFile(), report);
            System.out.println("LOAD200 COMPLETE requests=" + count.get() + " errors=" + errors.size());
            assertEquals(0, errors.size()); assertEquals(0, inventoryMismatch); assertEquals(0, duplicateSeats); assertEquals(0, negativeStock);
        } finally { pool.shutdownNow(); }
    }

    @Test void cinemaPermissionsAreEnforcedThroughRealHttpSqlAndCurrentDatabaseAssignment() throws Exception {
        Showtime first = showtime;
        Showtime second = createShowtimeFixture();
        authenticate(buyer);
        String ownOrder = orderService.createOrder(order("1排1座"));
        orderService.payOrder(ownOrder, null);
        String ownCode = findOrder(ownOrder).getTicketCode();
        showtime = second;
        String foreignOrder = orderService.createOrder(order("1排1座"));
        orderService.payOrder(foreignOrder, null);
        String foreignCode = findOrder(foreignOrder).getTicketCode();
        showtime = first;

        User manager = scopedAccount("CINEMA_ADMIN", first.getCinemaId());
        User platform = scopedAccount("ADMIN", null);
        User otherUser = scopedAccount("USER", null);
        User unassigned = scopedAccount("CINEMA_ADMIN", null);
        String token = loadJwt.generateToken(manager.getId(), manager.getUsername(), "CINEMA_ADMIN");
        String platformToken = loadJwt.generateToken(platform.getId(), platform.getUsername(), "ADMIN");
        String otherToken = loadJwt.generateToken(otherUser.getId(), otherUser.getUsername(), "USER");
        String unassignedToken = loadJwt.generateToken(unassigned.getId(), unassigned.getUsername(), "CINEMA_ADMIN");
        String buyerToken = loadJwt.generateToken(buyer.getId(), buyer.getUsername(), "USER");
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        try (var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()) {
            var page = scopedHttp(client, token, "GET", "/order/page?size=1&userId=" + buyer.getId(), null);
            assertEquals(200, page.statusCode());
            var data = json.readTree(page.body()).path("data");
            assertEquals(1, data.path("total").asInt());
            assertEquals(ownOrder, data.path("records").get(0).path("orderNo").asText());
            assertEquals(0, json.readTree(scopedHttp(client, token, "GET", "/order/page?orderNo=" + foreignOrder, null).body()).path("data").path("total").asInt());
            assertEquals(200, scopedHttp(client, token, "GET", "/order/" + ownOrder, null).statusCode());
            assertEquals(403, scopedHttp(client, token, "GET", "/order/" + foreignOrder, null).statusCode());
            assertEquals(403, scopedHttp(client, otherToken, "GET", "/order/" + ownOrder, null).statusCode());
            assertEquals(403, scopedHttp(client, otherToken, "POST", "/order/pick?ticketCode=" + ownCode, null).statusCode());
            assertEquals(403, scopedHttp(client, token, "POST", "/order/pick?ticketCode=" + foreignCode, null).statusCode());
            assertEquals(403, scopedHttp(client, token, "POST", "/order/" + foreignOrder + "/refund", null).statusCode());
            assertEquals(0, findOrder(foreignOrder).getIsPicked());
            assertEquals(1, findOrder(foreignOrder).getStatus());

            for (String path : List.of("/order/page", "/cinema/all", "/hall/page", "/showtime/page", "/statistics/dashboard", "/statistics/scheduling-insights")) {
                assertEquals(403, scopedHttp(client, unassignedToken, "GET", path, null).statusCode(), path);
            }
            var cinemaPage = json.readTree(scopedHttp(client, token, "GET", "/cinema/page?keyword=Testcontainers", null).body()).path("data");
            assertEquals(1, cinemaPage.path("total").asInt());
            assertEquals(first.getCinemaId().longValue(), cinemaPage.path("records").get(0).path("id").asLong());
            var hallPage = json.readTree(scopedHttp(client, token, "GET", "/hall/page", null).body()).path("data");
            assertEquals(1, hallPage.path("total").asInt());
            var showtimePage = json.readTree(scopedHttp(client, token, "GET", "/showtime/page", null).body()).path("data");
            assertEquals(1, showtimePage.path("total").asInt());
            for (String path : List.of("/hall/page?cinemaId=" + second.getCinemaId(), "/showtime/page?cinemaId=" + second.getCinemaId(),
                    "/statistics/showtime/" + second.getId(), "/statistics/scheduling-insights?cinemaId=" + second.getCinemaId())) {
                assertEquals(403, scopedHttp(client, token, "GET", path, null).statusCode(), path);
            }
            for (String path : List.of("/hall/" + second.getHallId() + "/status?status=0", "/showtime/" + second.getId() + "/status?status=2",
                    "/cinema/" + second.getCinemaId() + "/status?status=0", "/movie/" + second.getMovieId() + "/status?status=0")) {
                assertEquals(403, scopedHttp(client, token, "PUT", path, null).statusCode(), path);
            }
            assertEquals(403, scopedHttp(client, token, "GET", "/recommend/evaluation?k=6", null).statusCode());
            assertEquals(200, scopedHttp(client, platformToken, "GET", "/recommend/evaluation?k=6", null).statusCode());
            assertEquals(403, scopedHttp(client, token, "GET", "/statistics/scheduling/workspace?cinemaId=" + second.getCinemaId() + "&targetDate=" + java.time.LocalDate.now().plusDays(2), null).statusCode());
            String foreignDraft = "{\"cinemaId\":" + second.getCinemaId() + ",\"targetDate\":\"" + java.time.LocalDate.now().plusDays(2) + "\",\"version\":\"old\",\"cleaningMinutes\":15,\"items\":[{\"movieId\":" + second.getMovieId() + ",\"hallId\":" + second.getHallId() + ",\"startTime\":\"" + second.getStartTime() + "\",\"endTime\":\"" + second.getEndTime() + "\",\"basePrice\":50}]}";
            assertEquals(403, scopedHttp(client, token, "POST", "/statistics/scheduling/apply", foreignDraft).statusCode());

            var dashboard = json.readTree(scopedHttp(client, token, "GET", "/statistics/dashboard", null).body()).path("data");
            assertEquals(1, dashboard.path("todayOrderCount").asInt());
            assertEquals(50, dashboard.path("todayRevenue").asInt());
            var ranking = json.readTree(scopedHttp(client, token, "GET", "/statistics/box-office", null).body()).path("data");
            assertEquals(1, ranking.size()); assertEquals(first.getMovieId().longValue(), ranking.get(0).path("movieId").asLong());
            var revenue = json.readTree(scopedHttp(client, token, "GET", "/statistics/revenue?startDate=" + LocalDate.now() + "&endDate=" + LocalDate.now(), null).body()).path("data");
            assertEquals(50, revenue.get(0).path("revenue").asInt());
            assertEquals(2, json.readTree(scopedHttp(client, platformToken, "GET", "/order/page", null).body()).path("data").path("total").asInt());

            assertEquals(200, scopedHttp(client, token, "POST", "/order/pick?ticketCode=" + ownCode, null).statusCode());
            assertEquals(200, scopedHttp(client, buyerToken, "POST", "/order/pick?ticketCode=" + foreignCode, null).statusCode());
            assertEquals(1, findOrder(ownOrder).getIsPicked());
            assertEquals(1, findOrder(foreignOrder).getIsPicked());

            // Reassignment changes the SQL scope immediately, even when the exact same JWT is reused.
            assertEquals(200, scopedHttp(client, platformToken, "PUT", "/user/" + manager.getId(),
                    "{\"role\":\"CINEMA_ADMIN\",\"cinemaId\":" + second.getCinemaId() + "}").statusCode());
            assertEquals(403, scopedHttp(client, token, "GET", "/order/" + ownOrder, null).statusCode());
            assertEquals(200, scopedHttp(client, token, "GET", "/order/" + foreignOrder, null).statusCode());
            assertEquals(200, scopedHttp(client, platformToken, "PUT", "/user/" + manager.getId(), "{\"role\":\"USER\"}").statusCode());
            assertEquals(403, scopedHttp(client, token, "GET", "/order/page", null).statusCode());
            assertEquals(null, userMapper.selectById(manager.getId()).getCinemaId());
            assertEquals(200, scopedHttp(client, platformToken, "PUT", "/user/" + manager.getId() + "/status?status=0", null).statusCode());
            assertEquals(401, scopedHttp(client, token, "GET", "/order/my", null).statusCode());
        } finally { SecurityContextHolder.clearContext(); }
    }

    private User scopedAccount(String role, Long cinemaId) {
        User user = new User(); user.setUsername("scope_" + java.util.UUID.randomUUID().toString().substring(0, 12));
        user.setPassword(buyer.getPassword()); user.setRole(role); user.setStatus(1); user.setCinemaId(cinemaId);
        userMapper.insert(user); return user;
    }

    private java.net.http.HttpResponse<String> scopedHttp(java.net.http.HttpClient client, String token,
            String method, String path, String body) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + httpPort + "/api" + path))
                .timeout(java.time.Duration.ofSeconds(20)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody() : java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private static java.util.Map<String, Object> loadPercentiles(java.util.Collection<Long> values) {
        var sorted = values.stream().sorted().toList();
        if (sorted.isEmpty()) return java.util.Map.of("count", 0);
        return java.util.Map.of("count", sorted.size(), "p50Ms", sorted.get((int)Math.ceil(sorted.size()*.50)-1)/1_000_000.0,
                "p95Ms", sorted.get((int)Math.ceil(sorted.size()*.95)-1)/1_000_000.0,
                "p99Ms", sorted.get((int)Math.ceil(sorted.size()*.99)-1)/1_000_000.0,
                "maxMs", sorted.get(sorted.size()-1)/1_000_000.0);
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
        UserDetailsImpl principal = new UserDetailsImpl(user.getId(), user.getUsername(), user.getRole(), user.getCinemaId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
