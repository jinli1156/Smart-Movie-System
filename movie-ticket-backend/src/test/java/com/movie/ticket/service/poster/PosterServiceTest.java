package com.movie.ticket.service.poster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.TestIdentity;
import com.movie.ticket.controller.PosterController;
import com.movie.ticket.exception.*;
import com.movie.ticket.service.marketing.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(PosterServiceTest.Config.class) @WebAppConfiguration
class PosterServiceTest {
    @Configuration @EnableWebMvc @EnableMethodSecurity @EnableTransactionManagement
    @Import({PosterService.class, PosterFiles.class, PosterController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean DataSource dataSource() {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:poster_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"); return source;
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactions(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean MarketingImageClient images() { return mock(MarketingImageClient.class); }
        @Bean MarketingSettings imageSettings() {
            var s = new MarketingSettings(); s.setImageProvider("openai-images"); s.setImageApiKey("test-only"); return s;
        }
        @Bean PosterSettings settings() { return new PosterSettings(); }
    }
    @Autowired JdbcTemplate db;
    @Autowired PosterService service;
    @Autowired PosterFiles files;
    @Autowired PosterSettings settings;
    @Autowired MarketingImageClient images;
    @Autowired MarketingSettings imageSettings;
    @Autowired ObjectMapper json;
    @Autowired WebApplicationContext context;
    @TempDir Path storage;
    MockMvc mvc;
    byte[] png;
    @BeforeEach void setup() throws Exception {
        reset(images); TestIdentity.login(7L, "USER", null);
        settings.setStoragePath(storage.toString()); settings.setWorkerEnabled(true); settings.setDailyLimit(6);
        imageSettings.setImageApiKey("test-only"); imageSettings.setImageProvider("openai-images");
        db.execute("DROP ALL OBJECTS"); db.execute("CREATE TABLE `user`(id BIGINT PRIMARY KEY)");
        db.update("INSERT INTO `user` VALUES(7),(8)");
        try (var connection = db.getDataSource().getConnection()) {
            String schema = new String(getClass().getResourceAsStream("/db/flyway/V30__personal_movie_posters.sql").readAllBytes(), StandardCharsets.UTF_8).replaceAll("ENGINE=InnoDB[^;]*", "");
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
        }
        var buffer = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(128, 160, BufferedImage.TYPE_INT_RGB), "png", buffer);
        png = buffer.toByteArray();
        when(images.supportsReferencePhotos()).thenReturn(true);
        when(images.generate(anyString(), anyString())).thenReturn("local-image:fixture");
        when(images.generateWithReference(anyString(), anyString(), any())).thenReturn("local-image:fixture");
        when(images.download(anyString())).thenReturn(png);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }
    @AfterEach void clear() { TestIdentity.clear(); }
    private PosterService.Create input(String requestId) { return new PosterService.Create(requestId, "哪吒", "CYBERPUNK", "我是主角，风火轮变成悬浮装置", "3:4"); }
    private PosterService.Poster create() throws IOException { return service.create(input(UUID.randomUUID().toString()), null); }
    private MockMultipartFile portrait() { return new MockMultipartFile("photo", "me.png", "image/png", png); }

    @Test void concurrentReplaysReserveOneTaskAndOnePaidCall() throws Exception {
        String request = UUID.randomUUID().toString();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<String> call = () -> { TestIdentity.login(7L, "USER", null); try { start.await(); return service.create(input(request), portrait()).id(); } finally { TestIdentity.clear(); } };
            var first = executor.submit(call); var second = executor.submit(call); start.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(second.get(5, TimeUnit.SECONDS));
        }
        String id = service.list().getFirst().id(); service.generate(id); service.generate(id);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM personal_movie_poster", Integer.class)).isEqualTo(1);
        verify(images, times(1)).generateWithReference(anyString(), anyString(), any());
    }
    @Test void uploadedPortraitIsActuallySentToModelAndRemovedAfterSuccess() throws Exception {
        var result = service.create(input(UUID.randomUUID().toString()), portrait());
        String key = db.queryForObject("SELECT portrait_key FROM personal_movie_poster WHERE id=?", String.class, result.id());
        assertThat(Files.exists(files.path(key))).isTrue();
        service.generate(result.id());
        verify(images).generateWithReference(argThat(p -> p.contains("哪吒") && p.contains("赛博朋克") && p.contains("保留其面部特征") && p.contains("悬浮装置")), eq("3:4"), argThat(b -> b[0] == (byte) 0xff && b[1] == (byte) 0xd8));
        verify(images, never()).generate(anyString(), anyString());
        assertThat(service.get(result.id()).status()).isEqualTo("SUCCEEDED");
        assertThat(service.get(result.id()).hasPortrait()).isTrue();
        assertThat(Files.exists(files.path(key))).isFalse();
        assertThat(ImageIO.read(service.file(result.id()).toFile()).getWidth()).isEqualTo(128);
        mvc.perform(get("/posters/" + result.id() + "/file")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().contentType("image/png"));
    }
    @Test void textOnlyGenerationDoesNotPretendToKnowTheUsersFace() throws Exception {
        var result = create(); service.generate(result.id());
        verify(images).generate(argThat(p -> p.contains("虚构电影主角") && p.contains("不宣称还原用户本人相貌")), eq("3:4"));
        assertThat(service.get(result.id()).status()).isEqualTo("SUCCEEDED");
    }
    @Test void failedPersistenceReusesSavedProviderResultWithoutRegeneration() throws Exception {
        when(images.download(anyString())).thenThrow(new IOException("download unavailable")).thenReturn(png);
        var result = create(); service.generate(result.id());
        assertThat(service.get(result.id()).retryable()).isTrue();
        service.retry(result.id()); service.generate(result.id());
        assertThat(service.get(result.id()).status()).isEqualTo("SUCCEEDED");
        verify(images, times(1)).generate(anyString(), anyString());
        verify(images, times(2)).download("local-image:fixture");
    }
    @Test void uncertainSubmissionsAreNeverAutomaticallyOrManuallyReplayed() throws Exception {
        when(images.generate(anyString(), anyString())).thenThrow(new IOException("connection reset"));
        var result = create(); service.generate(result.id()); service.generate(result.id());
        assertThat(service.get(result.id()).retryable()).isFalse();
        assertThatThrownBy(() -> service.retry(result.id())).isInstanceOf(BusinessException.class);
        verify(images, times(1)).generate(anyString(), anyString());
    }
    @Test void anotherUsersPosterAndFileCannotBeReadOrRetriedEvenByAdmin() throws Exception {
        var result = create(); service.generate(result.id()); TestIdentity.login(8L, "ADMIN", null);
        assertThat(service.list()).isEmpty();
        mvc.perform(get("/posters/" + result.id())).andExpect(status().isNotFound());
        mvc.perform(get("/posters/" + result.id() + "/file")).andExpect(status().isNotFound());
        mvc.perform(post("/posters/" + result.id() + "/retry")).andExpect(status().isNotFound());
    }
    @Test void invalidPhotosDoNotCreateTasksOrConsumeQuota() {
        assertThatThrownBy(() -> service.create(input(UUID.randomUUID().toString()), new MockMultipartFile("photo", "me.png", "image/png", "not an image".getBytes())))
            .isInstanceOf(BusinessException.class);
        assertThat(service.list()).isEmpty(); verifyNoInteractions(images);
    }
    @Test void changedContentCannotReuseRequestIdAndDailyLimitIsEnforced() throws Exception {
        String request = UUID.randomUUID().toString(); settings.setDailyLimit(1);
        var result = service.create(input(request), null);
        assertThat(service.create(input(request), null).id()).isEqualTo(result.id());
        assertThatThrownBy(() -> service.create(new PosterService.Create(request, "星际穿越", "SCIFI", "", "3:4"), null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(this::create).isInstanceOf(BusinessException.class).hasMessageContaining("次数已用完");
        assertThat(service.capabilities().get("remaining")).isEqualTo(0L);
    }
    @Test void unavailableServiceStillServesExistingWorksAndRejectsNewRequests() throws Exception {
        var result = create(); service.generate(result.id()); imageSettings.setImageApiKey("");
        assertThat(service.capabilities().get("available")).isEqualTo(false);
        assertThatThrownBy(this::create).isInstanceOf(BusinessException.class);
        assertThat(Files.isRegularFile(service.file(result.id()))).isTrue();
    }
    @Test void multipartControllerAcceptsOptionalPhotoAndValidatesStyle() throws Exception {
        mvc.perform(multipart("/posters").file(new MockMultipartFile("data", "", "application/json", json.writeValueAsBytes(input(UUID.randomUUID().toString())))).file(portrait()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasPortrait").value(true)).andExpect(jsonPath("$.data.status").value("QUEUED"));
        var invalid = new PosterService.Create(UUID.randomUUID().toString(), "哪吒", "INVALID", "", "3:4");
        mvc.perform(multipart("/posters").file(new MockMultipartFile("data", "", "application/json", json.writeValueAsBytes(invalid))))
            .andExpect(status().isBadRequest());
    }
    @Test void unsupportedPhotoProviderAndStaleTasksCannotCausePaidReplays() throws Exception {
        when(images.supportsReferencePhotos()).thenReturn(false);
        assertThatThrownBy(() -> service.create(input(UUID.randomUUID().toString()), portrait())).isInstanceOf(BusinessException.class);
        var result = create();
        db.update("UPDATE personal_movie_poster SET status='RUNNING',updated_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP) WHERE id=?", result.id());
        service.poll();
        assertThat(service.get(result.id()).status()).isEqualTo("FAILED");
        assertThat(service.get(result.id()).retryable()).isFalse();
        verify(images, never()).generate(anyString(), anyString());
    }
}
