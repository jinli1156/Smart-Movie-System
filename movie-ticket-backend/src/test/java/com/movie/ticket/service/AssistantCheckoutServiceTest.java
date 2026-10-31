package com.movie.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.movie.ticket.entity.*;
import com.movie.ticket.mapper.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.security.UserDetailsImpl;
import com.movie.ticket.vo.OrderVO;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real order service and JDBC transactions, controlled inventory/prices and Redis adapter. */
class AssistantCheckoutServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AssistantOutingService outings = mock(AssistantOutingService.class);
    private final ShowtimeMapper showtimes = mock(ShowtimeMapper.class);
    private final HallMapper halls = mock(HallMapper.class);
    private final TicketOrderMapper tickets = mock(TicketOrderMapper.class);
    private final CouponService coupons = mock(CouponService.class);
    private final UserMapper users = mock(UserMapper.class);
    private final Map<String,String> locks = new ConcurrentHashMap<>();
    private JdbcTemplate jdbc;
    private AssistantCheckoutService service;
    private ObjectNode offer;
    private Hall hall;

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(ds) {
            @Override protected org.springframework.jdbc.core.RowMapper<Map<String, Object>> getColumnMapRowMapper() {
                return new org.springframework.jdbc.core.ColumnMapRowMapper() {
                    @Override protected Object getColumnValue(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
                        Object value = super.getColumnValue(rs, index);
                        // Reproduce Connector/J's untyped DATETIME mapping in the fast regression suite.
                        return value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : value;
                    }
                };
            }
        };
        jdbc.execute("CREATE TABLE `user` (id BIGINT PRIMARY KEY)"); jdbc.update("INSERT INTO `user` VALUES(7),(8)");
        jdbc.execute("CREATE TABLE showtime(id BIGINT PRIMARY KEY,deleted INT,available INT)"); jdbc.update("INSERT INTO showtime VALUES(1,0,20)");
        jdbc.execute("CREATE TABLE test_orders(order_no VARCHAR(64) PRIMARY KEY,user_id BIGINT,seats VARCHAR(128),status INT,price DECIMAL(10,2))");
        String migration = new ClassPathResource("db/flyway/V19__assistant_checkout.sql").getContentAsString(StandardCharsets.UTF_8)
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
        for (String sql : migration.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        var redis = mock(StringRedisTemplate.class); var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenAnswer(i -> locks.putIfAbsent(i.getArgument(0), i.getArgument(1)) == null);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(i -> {
            Object arg = i.getArgument(2); String owner = arg instanceof Object[] a ? a[0].toString() : arg.toString();
            return locks.remove(((List<String>) i.getArgument(1)).getFirst(), owner) ? 1L : 0L;
        });
        var showtime = new Showtime(); showtime.setId(1L); showtime.setHallId(1L); showtime.setStatus(0);
        showtime.setStartTime(LocalDateTime.now().plusDays(1)); showtime.setBasePrice(new BigDecimal("40.00"));
        when(showtimes.selectById(1L)).thenReturn(showtime);
        when(showtimes.reserveAvailableSeats(eq(1L), anyInt())).thenAnswer(i -> jdbc.update("UPDATE showtime SET available=available-? WHERE id=1 AND available>=?", (Integer)i.getArgument(1), (Integer)i.getArgument(1)));
        hall = new Hall(); hall.setId(1L); hall.setStatus(1); hall.setTotalRows(5); hall.setTotalCols(10);
        when(halls.selectById(1L)).thenReturn(hall);
        when(tickets.countActiveOrdersBySeat(eq(1L), anyString())).thenAnswer(i -> jdbc.queryForObject("SELECT COUNT(*) FROM test_orders WHERE status IN (0,1) AND seats LIKE ?", Long.class, "%"+ i.getArgument(1)+"%"));
        when(tickets.insert(any(TicketOrder.class))).thenAnswer(i -> {
            TicketOrder o = i.getArgument(0);
            return jdbc.update("INSERT INTO test_orders VALUES(?,?,?,?,?)", o.getOrderNo(),o.getUserId(),o.getSeats(),o.getStatus(),o.getTotalPrice());
        });
        when(tickets.selectOrderViewByOrderNo(anyString())).thenAnswer(i -> jdbc.queryForObject("SELECT * FROM test_orders WHERE order_no=?", (rs,n) -> {
            var o = new OrderVO(); o.setOrderNo(rs.getString("order_no")); o.setUserId(rs.getLong("user_id")); o.setStatus(rs.getInt("status")); return o;
        }, (String)i.getArgument(0)));
        offer = (ObjectNode)json.readTree("""
            {"showtimeId":1,"movieId":2,"movieTitle":"测试电影","cinemaName":"测试影院","hallName":"1号厅",
             "startTime":"2030-01-01 19:00:00","endTime":"2030-01-01 21:00:00","people":2,
             "seats":["3排3座","3排4座"],"originalPrice":80.00,
             "bestPlan":{"totalPrice":69.00,"couponId":9,"couponName":"测试券","couponDiscount":10,"pointsUsed":100,"pointsDiscount":1}}
            """);
        when(outings.plan(any(), anyLong())).thenAnswer(i -> Map.of("plans",List.of(offer.deepCopy())));
        var manager = new DataSourceTransactionManager(ds);
        var orderService = new OrderService(tickets,showtimes,mock(MovieMapper.class),mock(CinemaMapper.class),halls,users,redis,coupons);
        var factory = new ProxyFactory(new AssistantCheckoutService(jdbc,json,outings,showtimes,halls,tickets,orderService));
        factory.setProxyTargetClass(true); factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        service = (AssistantCheckoutService)factory.getProxy(); authenticate(7L);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private void authenticate(long id) {
        var principal = new UserDetailsImpl(id,"buyer","USER");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    private ObjectNode criteria() { return json.createObjectNode().put("showtimeId",1).put("people",2).put("usePoints",true).put("useCoupons",true); }
    private String prepare() { return service.prepare(7L,criteria(),null).path("draftId").asText(); }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM test_orders",Integer.class); }

    @Test void confirmationWorksWhenUntypedDatetimeMappingReturnsLocalDateTime() {
        String id = prepare();
        assertInstanceOf(LocalDateTime.class, jdbc.queryForMap("SELECT expires_at FROM assistant_checkout WHERE id=?", id).get("expires_at"));
        assertEquals(0, service.confirm(id).get("status"));
        assertEquals(1, count());
    }

    @Test void prepareHasNoBookingSideEffectsAndConfirmationCreatesOnlyOnePendingOrder() {
        String id=prepare(); assertEquals(0,count()); assertTrue(locks.isEmpty());
        var first=service.confirm(id); var second=service.confirm(id);
        assertEquals(first.get("orderNo"),second.get("orderNo")); assertEquals(0,first.get("status"));
        assertEquals(1,count()); assertEquals(18,jdbc.queryForObject("SELECT available FROM showtime",Integer.class));
        assertEquals(2,locks.size()); verifyNoInteractions(coupons,users);
        jdbc.update("UPDATE test_orders SET status=1");
        assertEquals(1,service.confirm(id).get("status")); assertEquals(1,count());
    }
    @Test void concurrentConfirmationsReturnTheSameOrder() throws Exception {
        String id=prepare(); var pool=Executors.newFixedThreadPool(6); var start=new CountDownLatch(1);
        try {
            var tasks=new ArrayList<Future<String>>();
            for(int i=0;i<6;i++) tasks.add(pool.submit(() -> { authenticate(7L); start.await(); try { return service.confirm(id).get("orderNo").toString(); } finally { SecurityContextHolder.clearContext(); } }));
            start.countDown(); var ids=new HashSet<String>(); for(var task:tasks) ids.add(task.get(10,TimeUnit.SECONDS));
            assertEquals(1,ids.size()); assertEquals(1,count());
        } finally { pool.shutdownNow(); }
    }
    @Test void ownershipExpiryAndMissingSelectionNeverCreateAnOrder() {
        String id=prepare(); authenticate(8L); assertEquals(404,assertThrows(BusinessException.class,()->service.confirm(id)).getCode());
        authenticate(7L); jdbc.update("UPDATE assistant_checkout SET expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP)");
        assertEquals(409,assertThrows(BusinessException.class,()->service.confirm(id)).getCode());
        assertThrows(BusinessException.class,()->service.prepare(null,criteria(),null));
        var incomplete=criteria(); incomplete.remove("showtimeId"); assertThrows(BusinessException.class,()->service.prepare(7L,incomplete,null));
        var forged=criteria().put("userId",8); assertThrows(BusinessException.class,()->service.prepare(7L,forged,null)); assertEquals(0,count());
    }
    @Test void stalePricesSeatsLayoutAndShowtimeCannotBeSilentlySubstituted() {
        String id=prepare(); offer.withObject("/bestPlan").put("totalPrice",70);
        assertThrows(BusinessException.class,()->service.confirm(id)); assertEquals(0,count());
        offer.withObject("/bestPlan").put("totalPrice",69.00);
        when(tickets.countActiveOrdersBySeat(1L,"3排3座")).thenReturn(1L);
        assertThrows(BusinessException.class,()->service.confirm(id)); assertEquals(0,count());
        when(tickets.countActiveOrdersBySeat(1L,"3排3座")).thenReturn(0L);
        hall.setStatus(0); assertThrows(BusinessException.class,()->service.confirm(id)); hall.setStatus(1);
        offer.put("startTime","2030-01-01 20:00:00"); assertThrows(BusinessException.class,()->service.confirm(id));
        assertEquals(0,count()); assertTrue(locks.isEmpty());
    }
    @Test void forgedDuplicateDisabledAndAcrossAisleSeatsAreRejected() {
        for(var seats:List.of(List.of("0排1座","3排4座"),List.of("3排3座","3排3座"),List.of("3排5座","3排6座"),List.of("30排1座","30排2座")))
            assertThrows(BusinessException.class,()->service.prepare(7L,criteria(),seats));
        assertEquals(0,count()); assertTrue(locks.isEmpty());
    }
    @Test void failureAfterOrderInsertRollsBackOrderInventoryReceiptAndRedisLocks() {
        String id=prepare(); doThrow(new IllegalStateException("simulate receipt failure")).when(tickets).selectOrderViewByOrderNo(anyString());
        assertThrows(IllegalStateException.class,()->service.confirm(id));
        assertEquals(0,count()); assertEquals(20,jdbc.queryForObject("SELECT available FROM showtime",Integer.class));
        assertNull(jdbc.queryForMap("SELECT order_no FROM assistant_checkout WHERE id=?",id).get("order_no"));
        assertTrue(locks.isEmpty());
    }
}
