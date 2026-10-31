package com.movie.ticket.service.souvenir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.TestIdentity;
import com.movie.ticket.controller.SouvenirController;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.exception.*;
import com.movie.ticket.mapper.*;
import com.movie.ticket.service.marketing.*;
import com.movie.ticket.vo.OrderVO;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(SouvenirServiceTest.Config.class)
@WebAppConfiguration
class SouvenirServiceTest {
    @Configuration @EnableWebMvc @EnableMethodSecurity @EnableTransactionManagement
    @Import({SouvenirService.class, SouvenirRenderer.class, SouvenirController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean DataSource dataSource() {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:souvenir_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"); return source;
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactions(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean TicketOrderMapper orders() { return mock(TicketOrderMapper.class); }
        @Bean MovieMapper movies() { return mock(MovieMapper.class); }
        @Bean MarketingTextClient text() { return mock(MarketingTextClient.class); }
        @Bean MarketingImageClient images() { return mock(MarketingImageClient.class); }
        @Bean MarketingSettings imageSettings() { var s = new MarketingSettings(); s.setZhipuApiKey("test-only"); return s; }
        @Bean SouvenirSettings settings() { return new SouvenirSettings(); }
    }
    @Autowired JdbcTemplate db;
    @Autowired SouvenirService service;
    @Autowired MarketingTextClient text;
    @Autowired MarketingImageClient images;
    @Autowired TicketOrderMapper orders;
    @Autowired MovieMapper movies;
    @Autowired SouvenirSettings settings;
    @Autowired ObjectMapper json;
    @Autowired WebApplicationContext context;
    @TempDir Path storage;
    MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        reset(text, images, orders, movies); TestIdentity.login(7L, "USER", null);
        settings.setStoragePath(storage.toString()); settings.setDailyLimit(6); settings.setWorkerEnabled(true);
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE TABLE `user`(id BIGINT PRIMARY KEY)");
        db.execute("CREATE TABLE ticket_order(id BIGINT PRIMARY KEY,user_id BIGINT,order_no VARCHAR(80),status INT)");
        try (var connection = db.getDataSource().getConnection()) {
            String schema = new String(getClass().getResourceAsStream("/db/flyway/V27__souvenir_tickets.sql").readAllBytes(), StandardCharsets.UTF_8).replaceAll("ENGINE=InnoDB[^;]*", "");
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
        }
        db.update("INSERT INTO `user` VALUES(7),(8)");
        db.update("INSERT INTO ticket_order VALUES(1,7,'REAL-ORDER-123456',1),(2,8,'OTHER',1),(3,7,'UNPAID',0),(4,7,'REFUNDED',3),(5,7,'CANCELLED',2)");
        var detail = new OrderVO(); detail.setId(1L); detail.setUserId(7L); detail.setMovieId(11L);
        detail.setMovieTitle("星际穿越"); detail.setCinemaName("响指星空影城"); detail.setHallName("IMAX 厅");
        detail.setSeats("5排6座,5排7座"); detail.setShowtime(LocalDateTime.of(2026, 10, 30, 19, 30));
        when(orders.selectOrderViewByOrderNo("REAL-ORDER-123456")).thenReturn(detail);
        var movie = new Movie(); movie.setGenre("科幻"); movie.setDescription("穿越星际的旅途"); when(movies.selectById(11L)).thenReturn(movie);
        when(text.configured()).thenReturn(true);
        when(text.completeJson(anyString(), any())).thenReturn(json.readTree("{\"headline\":\"将光影寄往星海\",\"caption\":\"把银幕上的片刻，收藏进无垠宇宙。\",\"imagePrompt\":\"深蓝紫色星云，微光粒子与细长光轨，无文字\"}"));
        when(images.generate(anyString(), eq("3:4"))).thenReturn("local-image:fixture");
        var png = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(160, 200, BufferedImage.TYPE_INT_RGB), "png", png);
        when(images.download(anyString())).thenReturn(png.toByteArray());
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }
    @AfterEach void clear() { TestIdentity.clear(); }
    private SouvenirService.Ticket create() { return service.create("REAL-ORDER-123456", new SouvenirService.Create("与朋友收藏这一刻", false)); }

    @Test void concurrentSubmissionsReserveOneTaskAndOneImage() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<String> call = () -> { TestIdentity.login(7L, "USER", null); try { start.await(); return create().id(); } finally { TestIdentity.clear(); } };
            var first = executor.submit(call); var second = executor.submit(call); start.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(second.get(5, TimeUnit.SECONDS));
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM souvenir_ticket", Integer.class)).isEqualTo(1);
        String id = create().id(); service.generate(id); service.generate(id);
        verify(images, times(1)).generate(anyString(), anyString());
        assertThat(service.list("REAL-ORDER-123456").getFirst().status()).isEqualTo("SUCCEEDED");
    }

    @Test void generationRendersExactOrderFactsAndDoesNotSendCredentialsToModels() throws Exception {
        var ticket = create(); service.generate(ticket.id());
        var saved = service.list("REAL-ORDER-123456").getFirst();
        assertThat(saved.facts().path("seats").asText()).isEqualTo("5排6座,5排7座");
        assertThat(saved.facts().path("movieTitle").asText()).isEqualTo("星际穿越");
        var image = ImageIO.read(service.file(ticket.id()).toFile());
        assertThat(image.getWidth()).isEqualTo(1080); assertThat(image.getHeight()).isEqualTo(1560);
        String preview = System.getProperty("souvenir.preview-path");
        if (preview != null) Files.copy(service.file(ticket.id()), Path.of(preview), StandardCopyOption.REPLACE_EXISTING);
        verify(text).completeJson(contains("固定星空科幻主题"), argThat(input -> !input.toString().contains("REAL-ORDER") && !input.toString().contains("5排6座")));
        verify(images).generate(argThat(prompt -> prompt.contains("深蓝、紫蓝星空") && prompt.contains("禁止一切文字") && !prompt.contains("REAL-ORDER")), eq("3:4"));
        mvc.perform(get("/souvenirs/" + ticket.id() + "/file")).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control", "no-store"));
        TestIdentity.login(8L, "USER", null);
        mvc.perform(get("/souvenirs/" + ticket.id() + "/file")).andExpect(status().isNotFound());
    }

    @Test void otherUsersAndAdministratorsCannotCreateOrReadAnotherPersonsSouvenir() throws Exception {
        for (String role : List.of("USER", "ADMIN", "CINEMA_ADMIN")) {
            TestIdentity.login(8L, role, 1L);
            mvc.perform(get("/souvenirs/orders/REAL-ORDER-123456")).andExpect(status().isNotFound());
            mvc.perform(post("/souvenirs/orders/REAL-ORDER-123456").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        }
        verifyNoInteractions(images);
    }

    @Test void unpaidCancelledRefundedAndUnknownOrdersCannotGenerate() throws Exception {
        for (String order : List.of("UNPAID", "CANCELLED", "REFUNDED"))
            mvc.perform(post("/souvenirs/orders/" + order).contentType("application/json").content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/souvenirs/orders/MISSING").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(images);
    }

    @Test void queuedTaskRechecksRefundAndFailsWithoutCallingModels() throws Exception {
        var task = create(); db.update("UPDATE ticket_order SET status=3 WHERE id=1"); service.generate(task.id());
        assertThat(service.list("REAL-ORDER-123456").getFirst().status()).isEqualTo("FAILED");
        verify(text, never()).completeJson(anyString(), any()); verifyNoInteractions(images);
    }

    @Test void retryAfterDownloadFailureReusesPaidImageResult() throws Exception {
        var task = create(); when(images.download(anyString())).thenThrow(new IOException("temporary")); service.generate(task.id());
        assertThat(service.list("REAL-ORDER-123456").getFirst().retryable()).isTrue();
        reset(images);
        var png = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(160, 200, BufferedImage.TYPE_INT_RGB), "png", png);
        when(images.download("local-image:fixture")).thenReturn(png.toByteArray());
        service.create("REAL-ORDER-123456", new SouvenirService.Create("", true)); service.generate(task.id());
        verify(images, never()).generate(anyString(), anyString());
        assertThat(service.list("REAL-ORDER-123456").getFirst().status()).isEqualTo("SUCCEEDED");
    }

    @Test void uncertainImageSubmissionCannotBeAutomaticallyOrManuallyReplayed() throws Exception {
        var error = mock(MarketingImageClient.GenerationException.class);
        when(error.isUncertain()).thenReturn(true); when(error.getMessage()).thenReturn("结果尚未确定");
        when(images.generate(anyString(), anyString())).thenThrow(error);
        var task = create(); service.generate(task.id());
        assertThat(service.list("REAL-ORDER-123456").getFirst().retryable()).isFalse();
        assertThatThrownBy(() -> service.create("REAL-ORDER-123456", new SouvenirService.Create("", true))).isInstanceOf(BusinessException.class);
        verify(images, times(1)).generate(anyString(), anyString());
    }

    @Test void validationQuotaAndUnavailableServiceDoNotReserveTasks() throws Exception {
        mvc.perform(post("/souvenirs/orders/REAL-ORDER-123456").contentType("application/json").content(json.writeValueAsString(Map.of("note", "a".repeat(121)))))
            .andExpect(status().isBadRequest());
        settings.setDailyLimit(0);
        mvc.perform(post("/souvenirs/orders/REAL-ORDER-123456").contentType("application/json").content("{}"))
            .andExpect(status().isTooManyRequests());
        settings.setDailyLimit(6); when(text.configured()).thenReturn(false);
        mvc.perform(post("/souvenirs/orders/REAL-ORDER-123456").contentType("application/json").content("{}"))
            .andExpect(status().isServiceUnavailable());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM souvenir_ticket", Integer.class)).isZero();
    }
}
