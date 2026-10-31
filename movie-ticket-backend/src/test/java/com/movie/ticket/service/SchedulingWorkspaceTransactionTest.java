package com.movie.ticket.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.movie.ticket.dto.SchedulingDraftRequest;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 独立 H2 数据库，真实 MyBatis SQL 与 Spring 事务，不写入本地影院数据。 */
@SpringJUnitConfig(SchedulingWorkspaceTransactionTest.Config.class)
class SchedulingWorkspaceTransactionTest {
    @org.junit.jupiter.api.BeforeEach void loginManager() { com.movie.ticket.TestIdentity.admin(); }
    @org.junit.jupiter.api.AfterEach void clearManager() { com.movie.ticket.TestIdentity.clear(); }
    @Configuration @EnableTransactionManagement @MapperScan("com.movie.ticket.mapper")
    static class Config {
        @Bean DataSource dataSource() { JdbcDataSource ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:scheduling;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER;LOCK_TIMEOUT=5000"); return ds; }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception { var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); return factory.getObject(); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SchedulingIntelligenceService intelligence() { return mock(SchedulingIntelligenceService.class); }
        @Bean SchedulingWorkspaceService service(ShowtimeMapper s, HallMapper h, MovieMapper m, CinemaMapper c, TicketOrderMapper o, SchedulingIntelligenceService i) { return new SchedulingWorkspaceService(s, h, m, c, o, i); }
    }
    @Autowired SchedulingWorkspaceService service;
    @Autowired SchedulingIntelligenceService intelligence;
    @Autowired ShowtimeMapper showtimes;
    @Autowired MovieMapper movies;
    @Autowired HallMapper halls;
    @Autowired CinemaMapper cinemas;
    @Autowired TicketOrderMapper orders;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    LocalDate day;
    Showtime original;
    JdbcTemplate jdbc;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(dataSource); jdbc.execute("DROP ALL OBJECTS");
        for (Class<?> entity : List.of(Cinema.class, Movie.class, Hall.class, Showtime.class, TicketOrder.class)) createTable(entity);
        reset(intelligence); when(intelligence.evaluatePlan(any(), any())).thenReturn(Map.of("expectedRevenue", 100));
        Cinema cinema = new Cinema(); cinema.setId(1L); cinema.setName("影院"); cinema.setStatus(1); cinemas.insert(cinema);
        Hall hall = new Hall(); hall.setId(1L); hall.setCinemaId(1L); hall.setName("一号厅"); hall.setStatus(1); hall.setTotalSeats(100); hall.setTotalRows(10); hall.setTotalCols(10); halls.insert(hall);
        Movie movie = new Movie(); movie.setId(1L); movie.setTitle("测试电影"); movie.setDuration(120); movie.setStatus(1); movies.insert(movie);
        day = LocalDate.now().plusDays(2); original = showtime(day.atTime(10, 0)); showtimes.insert(original);
    }

    @Test void checkDoesNotWriteAndApplyReplacesOnlyChangedRows() {
        var draft = request(); draft.getItems().getFirst().setStartTime(day.atTime(13, 0)); draft.getItems().getFirst().setEndTime(day.atTime(15, 0));
        var check = service.check(draft);
        assertThat(check.get("valid")).isEqualTo(true);
        assertThat(showtimes.selectById(original.getId()).getStartTime()).isEqualTo(day.atTime(10, 0));
        service.apply(draft);
        assertThat(showtimes.selectById(original.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM showtime WHERE id=?", Integer.class, original.getId())).isEqualTo(2);
        assertThat(showtimes.selectList(null)).singleElement().satisfies(s -> assertThat(s.getStartTime()).isEqualTo(day.atTime(13, 0)));
        assertThat(showtimes.reserveAvailableSeats(original.getId(), 1)).isZero();
        assertThatThrownBy(() -> service.apply(draft)).isInstanceOf(BusinessException.class).hasMessageContaining("已变化");
    }

    @Test void unchangedSoldShowtimeKeepsItsIdentityAndSeats() {
        showtimes.reserveAvailableSeats(original.getId(), 2);
        var draft = request(); draft.getItems().add(item(null, day.atTime(14, 0)));
        service.apply(draft);
        assertThat(showtimes.selectById(original.getId()).getAvailableSeats()).isEqualTo(98);
        assertThat(showtimes.selectList(null)).hasSize(2);
    }

    @Test void pendingOrderLocksEvenIfSeatCounterIsInconsistent() {
        TicketOrder order = new TicketOrder(); order.setShowtimeId(original.getId()); order.setStatus(0); order.setTicketCount(1); orders.insert(order);
        var draft = request(); draft.getItems().clear(); draft.getItems().add(item(null, day.atTime(14, 0)));
        assertThatThrownBy(() -> service.apply(draft)).hasMessageContaining("必须保留");
        assertThat(showtimes.selectById(original.getId())).isNotNull();
    }

    @Test void changedSaleStatusInvalidatesSnapshot() {
        var draft = request(); showtimes.reserveAvailableSeats(original.getId(), 1);
        assertThatThrownBy(() -> service.apply(draft)).hasMessageContaining("已变化");
        assertThat(showtimes.selectById(original.getId()).getAvailableSeats()).isEqualTo(99);
    }

    @Test void soldShowtimeCannotBeEdited() {
        showtimes.reserveAvailableSeats(original.getId(), 1);
        var draft = request(); draft.getItems().getFirst().setBasePrice(new BigDecimal("50"));
        assertThatThrownBy(() -> service.apply(draft)).hasMessageContaining("不可修改");
    }

    @Test void cleanupAndCrossMidnightConflictsAreRejected() {
        var draft = request(); draft.getItems().add(item(null, day.atTime(12, 10)));
        assertThatThrownBy(() -> service.check(draft)).hasMessageContaining("清场");
        Showtime followingDay = showtime(day.plusDays(1).atTime(0, 30)); showtimes.insert(followingDay);
        var crossDay = request(); crossDay.getItems().clear(); crossDay.getItems().add(item(null, day.atTime(23, 0)));
        assertThatThrownBy(() -> service.apply(crossDay)).hasMessageContaining("跨日");
    }

    @Test void previousDayLateScreeningBlocksNewMidnightShowtime() {
        showtimes.insert(showtime(day.minusDays(1).atTime(23, 0)));
        var draft = request(); draft.getItems().add(item(null, day.atTime(0, 30)));
        assertThatThrownBy(() -> service.check(draft)).hasMessageContaining("跨日");
    }

    @Test void durationAndForeignSourceAreRejectedBeforeWriting() {
        var draft = request(); draft.getItems().add(item(null, day.atTime(14, 0))); draft.getItems().getLast().setEndTime(day.atTime(14, 30));
        assertThatThrownBy(() -> service.apply(draft)).hasMessageContaining("时长");
        draft.getItems().getLast().setEndTime(day.atTime(16, 0)); draft.getItems().getLast().setSourceShowtimeId(999L);
        assertThatThrownBy(() -> service.apply(draft)).hasMessageContaining("其他日期");
    }

    @Test void failureMidPublishRollsBackRemovalsAndEarlierInserts() {
        jdbc.execute("ALTER TABLE showtime ADD CONSTRAINT test_price_limit CHECK (base_price < 100)");
        var draft = request(); draft.getItems().clear(); draft.getItems().add(item(null, day.atTime(13, 0))); draft.getItems().add(item(null, day.atTime(17, 0))); draft.getItems().getLast().setBasePrice(new BigDecimal("200"));
        assertThatThrownBy(() -> service.apply(draft)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(showtimes.selectList(null)).singleElement().satisfies(s -> { assertThat(s.getId()).isEqualTo(original.getId()); assertThat(s.getStatus()).isZero(); });
    }

    @Test void inFlightReservationWinsAndPublishingRechecksAfterWaiting() throws Exception {
        var draft = request(); draft.getItems().clear(); draft.getItems().add(item(null, day.atTime(14, 0)));
        CountDownLatch reserved = new CountDownLatch(1), release = new CountDownLatch(1), publishing = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> buyer = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                showtimes.reserveAvailableSeats(original.getId(), 1); reserved.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); } catch (InterruptedException e) { throw new RuntimeException(e); }
            }));
            assertThat(reserved.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> publisher = executor.submit(() -> {
                com.movie.ticket.TestIdentity.admin();
                try { publishing.countDown(); return service.apply(draft); }
                finally { com.movie.ticket.TestIdentity.clear(); }
            });
            assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue(); release.countDown(); buyer.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> publisher.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
            assertThat(showtimes.selectById(original.getId()).getAvailableSeats()).isEqualTo(99);
        }
    }

    private Showtime showtime(LocalDateTime start) { Showtime s = new Showtime(); s.setCinemaId(1L); s.setHallId(1L); s.setMovieId(1L); s.setStartTime(start); s.setEndTime(start.plusHours(2)); s.setBasePrice(new BigDecimal("45.00")); s.setTotalSeats(100); s.setAvailableSeats(100); s.setStatus(0); return s; }
    private SchedulingDraftRequest.Item item(Long id, LocalDateTime start) { var i = new SchedulingDraftRequest.Item(); i.setSourceShowtimeId(id); i.setHallId(1L); i.setMovieId(1L); i.setStartTime(start); i.setEndTime(start.plusHours(2)); i.setBasePrice(new BigDecimal("45.00")); return i; }
    private SchedulingDraftRequest request() { var r = new SchedulingDraftRequest(); r.setCinemaId(1L); r.setTargetDate(day); r.setVersion((String) service.workspace(1L, day).get("version")); r.setItems(new ArrayList<>(List.of(item(original.getId(), original.getStartTime())))); return r; }
    private void createTable(Class<?> entity) {
        String name = entity.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value();
        List<String> columns = new ArrayList<>();
        for (var field : entity.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            var annotation = field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
            if (annotation != null && !annotation.exist()) continue;
            String column = field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
            if (annotation != null && !annotation.value().isBlank()) column = annotation.value();
            String type = field.getType() == Long.class ? "BIGINT" : field.getType() == Integer.class ? "INT" : field.getType() == BigDecimal.class ? "DECIMAL(12,2)" : field.getType() == LocalDateTime.class ? "TIMESTAMP" : field.getType() == LocalDate.class ? "DATE" : "VARCHAR(10000)";
            columns.add(column + " " + (column.equals("id") ? "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY" : type + (column.equals("deleted") ? " DEFAULT 0" : "")));
        }
        jdbc.execute("CREATE TABLE " + name + " (" + String.join(",", columns) + ")");
    }
}
