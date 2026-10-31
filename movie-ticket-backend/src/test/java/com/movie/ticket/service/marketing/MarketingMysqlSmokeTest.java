package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.TestIdentity;
import com.movie.ticket.service.DeepSeekClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.file.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses a new, uniquely named database; never migrates or changes the user's business database. */
@EnabledIfEnvironmentVariable(named="MARKETING_MYSQL_TEST",matches="1")
class MarketingMysqlSmokeTest {
    @TempDir Path storage;
    @Test void productionMigrationAndReviewPublicationRunOnRealMysql() throws Exception {
        var properties=new YamlPropertySourceLoader().load("base",new ClassPathResource("application.yml")).getFirst();
        var env=new StandardEnvironment();
        String url=env.resolvePlaceholders(Objects.toString(properties.getProperty("spring.datasource.url")));
        String user=env.resolvePlaceholders(Objects.toString(properties.getProperty("spring.datasource.username")));
        String password=env.resolvePlaceholders(Objects.toString(properties.getProperty("spring.datasource.password")));
        int slash=url.indexOf('/',"jdbc:mysql://".length()); int query=url.indexOf('?');
        String server=url.substring(0,slash+1), options=query<0?"":url.substring(query);
        String name="movie_marketing_test_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(new DriverManagerDataSource(server+options,user,password));
        boolean created=false; MarketingWorker worker=null;
        try {
            admin.execute("CREATE DATABASE "+name+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); created=true;
            var source=new DriverManagerDataSource(server+name+options,user,password); var jdbc=new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE cinema(id BIGINT PRIMARY KEY,name VARCHAR(80),status INT,deleted INT DEFAULT 0)");
            jdbc.execute("CREATE TABLE movie(id BIGINT PRIMARY KEY,title VARCHAR(80),genre VARCHAR(80),director VARCHAR(80),actors VARCHAR(100),description VARCHAR(300),duration INT,release_date DATE,status INT,deleted INT DEFAULT 0)");
            try(var connection=source.getConnection()) {
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/flyway/V25__marketing_workbench.sql"));
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/flyway/V26__marketing_batch_image_count.sql"));
            }
            jdbc.update("INSERT INTO cinema(id,name,status) VALUES(10,'集成测试影院',1)");
            jdbc.update("INSERT INTO movie(id,title,genre,description,duration,status) VALUES(1,'测试电影','冒险','一次温暖的旅途',110,1)");
            var settings=new MarketingSettings(); settings.setStoragePath(storage.toString()); settings.setZhipuApiKey("test-only");
            var json=new ObjectMapper().findAndRegisterModules();
            var db=new MarketingStore(jdbc,json,new DataSourceTransactionManager(source)); var assets=new MarketingAssets(settings);
            var text=mock(DeepSeekClient.class); var images=mock(MarketingImageClient.class);
            when(text.configured()).thenReturn(true); when(text.model()).thenReturn("test-text");
            when(text.completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any())).thenReturn(json.valueToTree(Map.of("movieId",1,"prompt","暖色光影的抽象影院空间，底部保留暗色留白。")));
            when(images.generate(anyString(),anyString())).thenReturn("https://sfile.chatglm.cn/test.png");
            var bytes=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(160,160,BufferedImage.TYPE_INT_RGB),"png",bytes);
            when(images.download(anyString())).thenReturn(bytes.toByteArray());
            var copy=new MarketingTextClient(settings,text,images,json);
            var service=new MarketingService(db,settings,copy,assets); worker=new MarketingWorker(db,service,settings,images,copy,assets);
            TestIdentity.admin();
            String c=(String)service.createCampaign(new MarketingDtos.Campaign(10L,"周末观影","温暖的影院活动","朋友",List.of(1L),List.of("简洁"),List.of("3:4"))).get("id");
            service.createBatch(c,new MarketingDtos.Batch("IMAGE","mysql-image",2,new MarketingDtos.PosterContent(1L,"周末，一起看电影","与朋友发现银幕上的好故事")));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aigc_task WHERE kind='IMAGE'",Integer.class)).isEqualTo(2);
            worker.run(worker.claim());
            var asset=db.one("SELECT * FROM marketing_asset");
            String v=(String)service.saveVersion(c,new MarketingDtos.Version(1L,"周末，一起看电影","与朋友发现银幕上的好故事",MarketingStore.str(asset,"id"))).get("id");
            service.submit(v); service.review(v,new MarketingDtos.Review(true,"已核对"));
            var published=service.publish(v,new MarketingDtos.Publish("HOME",null,LocalDateTime.now().plusDays(3),"mysql-publish"));
            assertThat(service.placements("HOME",null)).hasSize(1);
            service.offline((String)published.get("id"));
            assertThat(service.placements("HOME",null)).isEmpty();
            System.out.println("MARKETING_MYSQL_OK migration=V25 workflow=generate-edit-review-publish-offline");
        } finally {
            TestIdentity.clear(); if(worker!=null) worker.close();
            if(created&&name.matches("movie_marketing_test_[a-f0-9]{32}")) admin.execute("DROP DATABASE "+name);
        }
    }
}
