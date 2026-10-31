package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.TestIdentity;
import com.movie.ticket.controller.MarketingController;
import com.movie.ticket.controller.MarketingPublicController;
import com.movie.ticket.exception.GlobalExceptionHandler;
import com.movie.ticket.service.DeepSeekClient;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.*;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring repository proxies + MVC + transactions. Only the model transport is replaced. */
@SpringJUnitConfig(MarketingSpringMvcTest.Config.class)
@WebAppConfiguration
class MarketingSpringMvcTest {
    @Configuration @EnableWebMvc @EnableMethodSecurity
    @Import({MarketingStore.class,MarketingService.class,MarketingAssets.class,MarketingTextClient.class,
             MarketingWorker.class,MarketingController.class,MarketingPublicController.class,GlobalExceptionHandler.class})
    static class Config {
        @Bean static PersistenceExceptionTranslationPostProcessor repositoryProxies() {
            var processor=new PersistenceExceptionTranslationPostProcessor(); processor.setProxyTargetClass(true); return processor;
        }
        @Bean DataSource dataSource() {
            var ds=new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:marketing_mvc_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
            return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactions(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean MarketingSettings settings() { var settings=new MarketingSettings(); settings.setZhipuApiKey("test-only"); return settings; }
        @Bean DeepSeekClient text() { return mock(DeepSeekClient.class); }
        @Bean MarketingImageClient images() { return mock(MarketingImageClient.class); }
    }
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MarketingStore store;
    @Autowired MarketingSettings settings;
    @Autowired MarketingWorker worker;
    @Autowired MarketingImageClient images;
    @Autowired DeepSeekClient text;
    @TempDir Path storage;
    MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        TestIdentity.admin(); settings.setStoragePath(storage.toString());
        assertThat(AopUtils.isCglibProxy(store)).isTrue();
        jdbc.execute("CREATE TABLE cinema(id BIGINT PRIMARY KEY,name VARCHAR(80),status INT,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE movie(id BIGINT PRIMARY KEY,title VARCHAR(80),genre VARCHAR(80),director VARCHAR(80),actors VARCHAR(100),description VARCHAR(300),duration INT,release_date DATE,status INT,deleted INT DEFAULT 0)");
        String schema=new String(getClass().getResourceAsStream("/db/flyway/V25__marketing_workbench.sql").readAllBytes(),StandardCharsets.UTF_8).replaceAll("ENGINE=InnoDB[^;]*","");
        try(var connection=jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/flyway/V26__marketing_batch_image_count.sql"));
        }
        jdbc.update("INSERT INTO cinema(id,name,status) VALUES(10,'测试影院',1)");
        jdbc.update("INSERT INTO movie(id,title,genre,description,duration,status) VALUES(1,'测试电影','冒险','一次温暖的旅途',110,1)");
        var png=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(160,160,BufferedImage.TYPE_INT_RGB),"png",png);
        when(images.generate(anyString(),anyString())).thenReturn("https://sfile.chatglm.cn/test.png");
        when(images.download(anyString())).thenReturn(png.toByteArray());
        when(text.configured()).thenReturn(true); when(text.model()).thenReturn("test-text");
        when(text.completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any())).thenReturn(json.valueToTree(Map.of("movieId",1,"prompt","暖色光影的抽象影院空间，底部保留暗色留白。")));
        mvc=MockMvcBuilders.webAppContextSetup(context).build();
    }
    @AfterEach void clearIdentity() { TestIdentity.clear(); }

    @Test void posterRequestSavesVersionAndPublishesThroughSpringProxies() throws Exception {
        String campaignResponse=mvc.perform(post("/admin/marketing/campaigns").contentType("application/json")
            .content(json.writeValueAsString(Map.of("cinemaId",10,"name","周末观影","brief","温暖的影院活动","audience","朋友",
                "movieIds",List.of(1),"styles",List.of("简洁"),"ratios",List.of("3:4")))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String campaign=json.readTree(campaignResponse).path("data").path("id").asText();
        mvc.perform(post("/admin/marketing/campaigns/"+campaign+"/batches").contentType("application/json")
            .content("{\"kind\":\"IMAGE\",\"requestKey\":\"missing-copy\",\"imageCount\":1}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/marketing/campaigns/"+campaign+"/batches").contentType("application/json")
            .content(json.writeValueAsString(new MarketingDtos.Batch("IMAGE","blank-copy",1,new MarketingDtos.PosterContent(1L,"标题"," ")))))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aigc_task",Integer.class)).isZero();
        String body=json.writeValueAsString(new MarketingDtos.Batch("IMAGE","spring-mvc-request",1,new MarketingDtos.PosterContent(1L,"周末一起看电影","一起发现银幕上的温暖故事")));
        mvc.perform(post("/admin/marketing/campaigns/"+campaign+"/batches").contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.imageCount").value(1));
        mvc.perform(post("/admin/marketing/campaigns/"+campaign+"/batches").contentType("application/json").content(body))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aigc_task",Integer.class)).isEqualTo(1);
        worker.run(worker.claim());
        assertThat(jdbc.queryForObject("SELECT status FROM aigc_task",String.class)).isEqualTo("SUCCEEDED");
        String asset=jdbc.queryForObject("SELECT id FROM marketing_asset",String.class);
        String versionResponse=mvc.perform(post("/admin/marketing/campaigns/"+campaign+"/versions").contentType("application/json")
            .content(json.writeValueAsString(Map.of("movieId",1,"title","周末一起看电影","copy","一起发现银幕上的温暖故事","assetId",asset))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String version=json.readTree(versionResponse).path("data").path("id").asText();
        mvc.perform(post("/admin/marketing/versions/"+version+"/submit-review")).andExpect(status().isOk());
        mvc.perform(post("/admin/marketing/versions/"+version+"/review").contentType("application/json").content("{\"approve\":true}"))
            .andExpect(status().isOk());
        mvc.perform(post("/admin/marketing/versions/"+version+"/publish").contentType("application/json")
            .content(json.writeValueAsString(Map.of("slot","HOME","endAt",LocalDateTime.now().plusDays(1),"idempotencyKey","spring-mvc-publish"))))
            .andExpect(status().isOk());
        mvc.perform(get("/marketing/placements").param("slot","HOME")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("周末一起看电影"));
        verify(images,times(1)).generate(anyString(),anyString());
    }
}
