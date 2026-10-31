package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.TestIdentity;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.service.DeepSeekClient;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.movie.ticket.service.marketing.MarketingStore.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketingWorkflowTest {
    @TempDir Path storage;
    MarketingStore db; MarketingSettings settings; MarketingService service; MarketingAssets assets;
    JdbcTemplate jdbc;
    MarketingWorker worker; DeepSeekClient text; MarketingImageClient images;
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    @BeforeEach void setup() throws Exception {
        TestIdentity.admin();
        var source=new JdbcDataSource(); source.setURL("jdbc:h2:mem:marketing_"+id()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE cinema(id BIGINT PRIMARY KEY,name VARCHAR(80),status INT,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE movie(id BIGINT PRIMARY KEY,title VARCHAR(80),genre VARCHAR(80),director VARCHAR(80),actors VARCHAR(100),description VARCHAR(300),duration INT,release_date DATE,status INT,deleted INT DEFAULT 0)");
        String schema=new String(getClass().getResourceAsStream("/db/flyway/V25__marketing_workbench.sql").readAllBytes(),StandardCharsets.UTF_8).replaceAll("ENGINE=InnoDB[^;]*","");
        try(var connection=source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
            ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("db/flyway/V26__marketing_batch_image_count.sql"));
        }
        jdbc.update("INSERT INTO cinema(id,name,status) VALUES(10,'测试影院',1),(20,'另一影院',1)");
        jdbc.update("INSERT INTO movie(id,title,genre,director,actors,description,duration,status) VALUES(1,'测试电影','冒险','导演','演员','一次温暖的旅途',110,1),(2,'另一电影','动画','导演','演员','朋友的故事',90,1)");
        settings=new MarketingSettings(); settings.setStoragePath(storage.toString()); settings.setZhipuApiKey("test-only"); settings.setDailyImageLimit(20);
        db=new MarketingStore(jdbc,json,new DataSourceTransactionManager(source)); assets=new MarketingAssets(settings);
        text=mock(DeepSeekClient.class); when(text.configured()).thenReturn(true); when(text.model()).thenReturn("test-text");
        when(text.completeJson(anyString(),any())).thenReturn(json.readTree("{\"movieId\":1,\"titles\":[\"周末一起看电影\",\"把时间留给银幕\",\"在影院遇见好故事\"],\"shortCopy\":\"和朋友一起走进影院，发现银幕上的温暖旅途。\"}"));
        when(text.completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any())).thenAnswer(call -> {
            var context=(Map<?,?>)call.getArgument(1);
            return json.valueToTree(Map.of("movieId",((com.fasterxml.jackson.databind.JsonNode)context.get("movie")).path("id").asLong(),
                "prompt","暖色光线照亮抽象影院空间，画面上方以层叠光影表现冒险氛围，底部保留暗色留白。"));
        });
        images=mock(MarketingImageClient.class); when(images.generate(anyString(),anyString())).thenReturn("https://sfile.chatglm.cn/test.png");
        var output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(160,160,BufferedImage.TYPE_INT_RGB),"png",output);
        when(images.download(anyString())).thenReturn(output.toByteArray());
        var copy=new MarketingTextClient(settings,text,images,json);
        service=new MarketingService(db,settings,copy,assets); worker=new MarketingWorker(db,service,settings,images,copy,assets);
    }
    @AfterEach void cleanup() { worker.close(); TestIdentity.clear(); }
    String campaign(long cinema) { return (String)service.createCampaign(new MarketingDtos.Campaign(cinema,"周末观影","温暖的周末观影活动","结伴观影的朋友",List.of(1L),List.of("简洁"),List.of("3:4"))).get("id"); }
    MarketingDtos.PosterContent posterContent(long movieId) { return new MarketingDtos.PosterContent(movieId,"把时间留给银幕","和朋友一起走进影院，发现银幕上的温暖旅途。"); }
    MarketingDtos.Batch batch(String kind,String key) { return batch(kind,key,null); }
    MarketingDtos.Batch batch(String kind,String key,Integer count) { return new MarketingDtos.Batch(kind,key,count,kind.equals("COPY")?null:posterContent(1)); }
    void runAll() { Map<String,Object> task; while((task=worker.claim())!=null) worker.run(task); }
    MarketingDtos.Publish publication(String key) { return new MarketingDtos.Publish("CINEMA",null,LocalDateTime.now().plusDays(3).withNano(0),key); }
    String assetId(String c) { return str(db.one("SELECT * FROM marketing_asset WHERE campaign_id=?",c),"id"); }
    String version(String c) {
        return (String)service.saveVersion(c,new MarketingDtos.Version(1L,"周末一起看电影","和朋友一起发现银幕上的好故事。",assetId(c))).get("id");
    }
    void approve(String v) { service.submit(v); service.review(v,new MarketingDtos.Review(true,"图文已核对")); }

    @Test void generatesEditsReviewsPublishesAndWithdrawsImmutableVersions() throws Exception {
        String c=campaign(10); var batch=batch("ALL","same-request");
        assertThat(service.createBatch(c,batch).get("id")).isEqualTo(service.createBatch(c,batch).get("id"));
        assertThat(db.rows("SELECT * FROM aigc_task")).hasSize(2); runAll();
        assertThat(db.rows("SELECT * FROM aigc_task WHERE status='SUCCEEDED'")).hasSize(2);
        String original=assetId(c),v=version(c);
        assertThatThrownBy(()->service.publicAsset(original)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.publish(v,publication("early"))).hasMessageContaining("审核通过");
        approve(v); var request=publication("publish-once");
        var published=service.publish(v,request);
        assertThat(service.publish(v,request).get("id")).isEqualTo(published.get("id"));
        assertThat(service.placements("CINEMA",10L)).hasSize(1);
        String publicId=db.parse(str(db.one("SELECT * FROM marketing_content_version WHERE id=?",v),"payload")).path("assetId").asText();
        assertThat(service.publicAsset(publicId)).exists();
        assertThat(publicId).isEqualTo(original);
        var edited=service.saveVersion(c,new MarketingDtos.Version(1L,"另一个新标题","人工修改后的文案",original));
        assertThat(edited.get("status")).isEqualTo("DRAFT");
        assertThat(service.placements("CINEMA",10L).getFirst().get("title")).isEqualTo("周末一起看电影");
        assertThatThrownBy(()->service.publish((String)edited.get("id"),publication("unreviewed"))).hasMessageContaining("审核通过");
        service.offline((String)published.get("id"));
        assertThat(service.placements("CINEMA",10L)).isEmpty();
        assertThatThrownBy(()->service.publicAsset(publicId)).hasMessageContaining("未公开");
        assertThatThrownBy(()->service.publish(v,request)).hasMessageContaining("已撤下");
        verify(images,times(1)).generate(anyString(),anyString());
    }
    @Test void cinemaScopeIsEnforcedForCampaignsTasksAssetsAndReview() {
        String own=campaign(10),foreign=campaign(20);
        service.createBatch(foreign,batch("IMAGE","foreign")); runAll();
        String foreignTask=str(db.one("SELECT * FROM aigc_task WHERE campaign_id=?",foreign),"id");
        TestIdentity.login(3L,"CINEMA_ADMIN",10L);
        assertThat(service.campaigns()).hasSize(1);
        assertThat(service.detail(own).get("cinemaId")).isEqualTo(10L);
        assertThatThrownBy(()->service.detail(foreign)).hasMessageContaining("其他影院");
        assertThatThrownBy(()->service.privateAsset(assetId(foreign))).hasMessageContaining("其他影院");
        assertThatThrownBy(()->service.retry(foreignTask,new MarketingDtos.Retry(false))).hasMessageContaining("其他影院");
        assertThatThrownBy(()->service.review("missing",new MarketingDtos.Review(true,""))).hasMessageContaining("平台管理员");
        TestIdentity.login(4L,"CINEMA_ADMIN",null);
        assertThatThrownBy(service::campaigns).hasMessageContaining("尚未分配");
        TestIdentity.login(5L,"USER",null);
        assertThatThrownBy(service::capabilities).hasMessageContaining("管理权限");
    }
    @Test void generatedPosterAndSavedVersionKeepTheOriginalImageWithoutTemplateOverlays() throws Exception {
        String c=campaign(10); service.createBatch(c,batch("IMAGE","image")); runAll();
        String original=assetId(c);
        byte[] pixelsBefore=Files.readAllBytes(service.privateAsset(original));
        String v=version(c); var saved=db.one("SELECT * FROM marketing_content_version WHERE id=?",v);
        String editedAsset=db.parse(str(saved,"payload")).path("assetId").asText();
        var asset=db.one("SELECT * FROM marketing_asset WHERE id=?",editedAsset);
        assertThat(str(asset,"source_type")).isEqualTo("MODEL_POSTER");
        assertThat(str(asset,"storage_key")).isEqualTo(str(asset,"background_key"));
        assertThat(editedAsset).isEqualTo(original);
        assertThat(Files.readAllBytes(service.privateAsset(editedAsset))).isEqualTo(pixelsBefore);
        assertThat(ImageIO.read(service.privateAsset(editedAsset).toFile()).getHeight()).isEqualTo(160);
        verify(images,times(1)).generate(anyString(),anyString());
    }
    @Test void retryingAnOlderCachedPromptReplansWithTheCurrentInstructions() throws Exception {
        String c=campaign(10); service.createBatch(c,batch("IMAGE","old-prompt"));
        var task=db.one("SELECT * FROM aigc_task");
        var result=db.parse(str(task,"result"));
        var old=new LinkedHashMap<String,Object>();
        old.put("posterContent",result.path("posterContent")); old.put("movieId",1);
        old.put("prompt","旧的抽象背景指令"); old.put("promptVersion",1);
        db.update("UPDATE aigc_task SET result=? WHERE id=?",db.stringify(old),str(task,"id"));
        runAll();
        verify(text,times(1)).completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any());
        verify(images).generate(argThat(prompt->!prompt.contains("旧的抽象背景指令")&&prompt.contains("把时间留给银幕")),eq("3:4"));
        assertThat(db.parse(str(db.one("SELECT * FROM aigc_task"),"result")).path("promptVersion").asInt()).isEqualTo(MarketingPrompts.IMAGE_PROMPT_VERSION);
    }
    @Test void downloadRetryReusesProviderResultAndDoesNotPayForGenerationAgain() throws Exception {
        String c=campaign(10); when(images.download(anyString())).thenThrow(new IOException("temporary")).thenReturn(png());
        service.createBatch(c,batch("IMAGE","image")); runAll();
        var task=db.one("SELECT * FROM aigc_task");
        assertThat(str(task,"status")).isEqualTo("FAILED"); assertThat(str(task,"remote_url")).isNotBlank();
        assertThat(db.stringify(service.detail(c))).doesNotContain("remoteUrl","test.png","storageKey","backgroundKey");
        service.retry(str(task,"id"),new MarketingDtos.Retry(false)); runAll();
        assertThat(str(db.one("SELECT * FROM aigc_task"),"status")).isEqualTo("SUCCEEDED");
        verify(images,times(1)).generate(anyString(),anyString());
        verify(text,times(1)).completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any());
        assertThat(num(db.one("SELECT * FROM aigc_task"),"provider_calls")).isEqualTo(1);
    }
    @Test void languageModelReceivesOnlyTheMovieAndMarketingTitleBeforeImageSubmission() throws Exception {
        String prompt="为该电影制作一张呼应营销标题的宣传海报。";
        when(text.completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any())).thenAnswer(call -> {
            var task=db.one("SELECT * FROM aigc_task");
            assertThat(str(task,"status")).isEqualTo("PLANNING");
            assertThat(num(task,"provider_calls")).isZero();
            var context=(Map<?,?>)call.getArgument(1);
            assertThat(context).hasSize(2);
            assertThat(context.get("marketingTitle")).isEqualTo("把时间留给银幕");
            assertThat(((com.fasterxml.jackson.databind.JsonNode)context.get("movie")).path("title").asText()).isEqualTo("测试电影");
            return json.valueToTree(Map.of("movieId",1,"prompt",prompt));
        });
        when(images.generate(anyString(),eq("3:4"))).thenAnswer(call -> {
            var task=db.one("SELECT * FROM aigc_task");
            assertThat(str(task,"status")).isEqualTo("SUBMITTING");
            assertThat(db.parse(str(task,"result")).path("prompt").asText()).isEqualTo(prompt);
            assertThat((String)call.getArgument(0)).startsWith(prompt).contains("测试电影","把时间留给银幕","一次温暖的旅途");
            assertThat(db.parse(str(task,"result")).path("submittedPrompt").asText()).isEqualTo((String)call.getArgument(0));
            return "https://sfile.chatglm.cn/test.png";
        });
        String c=campaign(10); service.createBatch(c,batch("IMAGE","planned")); runAll();
        var task=db.one("SELECT * FROM aigc_task");
        assertThat(str(task,"status")).isEqualTo("SUCCEEDED");
        assertThat(db.parse(str(task,"result")).path("promptModel").asText()).isEqualTo("test-text");
        assertThat(db.parse(str(task,"result")).path("prompt").asText()).isEqualTo(prompt);
        assertThat(db.parse(str(task,"result")).path("posterContent").path("title").asText()).isEqualTo("把时间留给银幕");
        var order=inOrder(text,images);
        order.verify(text).completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any());
        order.verify(images).generate(startsWith(prompt),eq("3:4"));
        order.verify(images).download(anyString());
    }
    @Test void invalidDrawingInstructionsNeverReachTheImageProvider() throws Exception {
        String c=campaign(10); int index=0;
        for(String invalid:List.of("null","[]","{}","{\"movieId\":2,\"prompt\":\"错误影片\"}",
            "{\"movieId\":1,\"prompt\":123}","{\"movieId\":1,\"prompt\":\"  \"}",
            json.writeValueAsString(Map.of("movieId",1,"prompt","画".repeat(1201))))) {
            when(text.completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any())).thenReturn(json.readTree(invalid));
            service.createBatch(c,batch("IMAGE","bad-prompt-"+index++)); runAll();
        }
        assertThat(db.rows("SELECT * FROM aigc_task WHERE status='FAILED' AND error_code='PROMPT_FAILED' AND provider_calls=0")).hasSize(index);
        assertThat(db.rows("SELECT * FROM marketing_asset")).isEmpty();
        verifyNoInteractions(images);
    }
    @Test void interruptedPlanningCanBeRetriedWithoutAnUncertainImageSubmission() throws Exception {
        when(text.completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any())).thenAnswer(call -> { throw new InterruptedException("planning interrupted"); });
        String c=campaign(10); service.createBatch(c,batch("IMAGE","interrupted"));
        try { runAll(); } finally { Thread.interrupted(); }
        var task=db.one("SELECT * FROM aigc_task");
        assertThat(str(task,"status")).isEqualTo("FAILED");
        assertThat(str(task,"error_message")).contains("绘图指令");
        assertThat(num(task,"provider_calls")).isZero();
        verifyNoInteractions(images);
        service.retry(str(task,"id"),new MarketingDtos.Retry(false));
        assertThat(str(db.one("SELECT * FROM aigc_task"),"status")).isEqualTo("QUEUED");
    }
    @Test void confirmedImageFailureReusesSavedLanguageModelInstructionsOnRetry() throws Exception {
        when(images.generate(anyString(),anyString())).thenThrow(new MarketingImageClient.GenerationException("HTTP_429","请稍后重试",false))
            .thenReturn("https://sfile.chatglm.cn/test.png");
        String c=campaign(10); service.createBatch(c,batch("IMAGE","retry-plan")); runAll();
        var task=db.one("SELECT * FROM aigc_task");
        assertThat(str(task,"status")).isEqualTo("FAILED");
        service.retry(str(task,"id"),new MarketingDtos.Retry(false)); runAll();
        assertThat(str(db.one("SELECT * FROM aigc_task"),"status")).isEqualTo("SUCCEEDED");
        verify(text,times(1)).completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any());
        verify(images,times(2)).generate(anyString(),anyString());
    }
    @Test void expiredPlanningLeaseIsSafeToRetryAndDoesNotSubmitImages() {
        String c=campaign(10); service.createBatch(c,batch("IMAGE","expired-plan")); worker.claim();
        db.update("UPDATE aigc_task SET status='PLANNING',lease_until=?",LocalDateTime.now().minusMinutes(1)); worker.recoverExpired();
        var task=db.one("SELECT * FROM aigc_task");
        assertThat(str(task,"status")).isEqualTo("FAILED");
        service.retry(str(task,"id"),new MarketingDtos.Retry(false));
        verifyNoInteractions(images);
    }
    @Test void imageRequestsRequireALanguageModelBeforeReservingQuota() {
        settings.setTextProvider("deepseek"); when(text.configured()).thenReturn(false);
        String c=campaign(10);
        assertThatThrownBy(()->service.createBatch(c,batch("IMAGE","no-language-model"))).hasMessageContaining("语言模型");
        assertThat(db.rows("SELECT * FROM aigc_task")).isEmpty();
        assertThat(db.rows("SELECT * FROM marketing_review_log WHERE action='IMAGE_RESERVE'")).isEmpty();
    }
    @Test void ambiguousSubmissionRequiresExplicitAcknowledgementAndCountsQuota() throws Exception {
        String c=campaign(10); when(images.generate(anyString(),anyString())).thenThrow(new MarketingImageClient.GenerationException("SUBMIT_UNKNOWN","请求待核实",true));
        service.createBatch(c,batch("IMAGE","image")); runAll();
        var task=db.one("SELECT * FROM aigc_task"); assertThat(str(task,"status")).isEqualTo("SUBMIT_UNKNOWN");
        assertThatThrownBy(()->service.retry(str(task,"id"),new MarketingDtos.Retry(false))).hasMessageContaining("可能再次计费");
        service.retry(str(task,"id"),new MarketingDtos.Retry(true));
        assertThat(str(db.one("SELECT * FROM aigc_task"),"status")).isEqualTo("QUEUED");
        assertThat(db.rows("SELECT * FROM marketing_review_log WHERE action='IMAGE_RESERVE'")).hasSize(2);
    }
    @Test void rejectsInvalidCopyAndNeverMarksItSuccessful() throws Exception {
        when(text.completeJson(anyString(),any())).thenReturn(json.readTree("{\"movieId\":999,\"titles\":[],\"shortCopy\":\"\"}"));
        String c=campaign(10); service.createBatch(c,batch("COPY","copy")); runAll();
        assertThat(str(db.one("SELECT * FROM aigc_task"),"status")).isEqualTo("FAILED");
        assertThat(db.rows("SELECT * FROM marketing_content_version")).isEmpty();
    }
    @Test void changedCatalogFactsInvalidateApprovalAndExpiredPublicationsHideAssets() {
        String c=campaign(10); service.createBatch(c,batch("IMAGE","image")); runAll();
        String v=version(c); approve(v);
        db.update("UPDATE movie SET director='更新导演' WHERE id=1");
        assertThatThrownBy(()->service.publish(v,publication("stale"))).hasMessageContaining("资料已变化");
        String fresh=version(c); approve(fresh); service.publish(fresh,publication("fresh"));
        db.update("UPDATE marketing_publication SET end_at=?",LocalDateTime.now().minusSeconds(1));
        assertThat(service.placements("CINEMA",10L)).isEmpty();
    }
    @Test void expiredWorkerLeaseDoesNotAutomaticallyResubmitPaidRequests() {
        String c=campaign(10); service.createBatch(c,batch("IMAGE","image"));
        var task=worker.claim(); assertThat(task).isNotNull(); assertThat(worker.claim()).isNull();
        db.update("UPDATE aigc_task SET lease_until=?",LocalDateTime.now().minusMinutes(1)); worker.recoverExpired();
        assertThat(str(db.one("SELECT * FROM aigc_task"),"status")).isEqualTo("SUBMIT_UNKNOWN");
        assertThat(worker.claim()).isNull(); verifyNoInteractions(images);
    }
    @Test void longImageRequestsKeepTheirLeaseBeyondGenerationAndDownloadTimeouts() {
        settings.setRequestTimeoutSeconds(600); settings.setDownloadTimeoutSeconds(120);
        String c=campaign(10); service.createBatch(c,batch("IMAGE","long-generation"));
        var task=worker.claim();
        LocalDateTime leaseUntil=((java.sql.Timestamp)task.get("lease_until")).toLocalDateTime();
        assertThat(leaseUntil).isAfter(LocalDateTime.now().plusSeconds(720));
        worker.recoverExpired();
        assertThat(str(db.one("SELECT * FROM aigc_task"),"status")).isEqualTo("RUNNING");
        assertThat(worker.claim()).isNull();
        verifyNoInteractions(images);
    }
    @Test void perCinemaQuotaAndBatchLimitsAreAtomic() {
        settings.setDailyImageLimit(1); String c=campaign(10);
        service.createBatch(c,batch("IMAGE","first"));
        assertThatThrownBy(()->service.createBatch(c,batch("IMAGE","second"))).hasMessageContaining("额度");
        assertThat(db.rows("SELECT * FROM marketing_batch")).hasSize(1);
        assertThat(db.rows("SELECT * FROM aigc_task")).hasSize(1);
    }
    @Test void concurrentDuplicateBatchRequestsCreateOneBatch() throws Exception {
        String c=campaign(10);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Object> request=()->{ TestIdentity.admin(); try { return service.createBatch(c,batch("IMAGE","same")).get("id"); } finally { TestIdentity.clear(); } };
            var a=executor.submit(request); var b=executor.submit(request);
            assertThat(a.get(5,TimeUnit.SECONDS)).isEqualTo(b.get(5,TimeUnit.SECONDS));
        }
        assertThat(db.rows("SELECT * FROM aigc_task")).hasSize(1);
    }
    @Test void createsExactlyTheSelectedImageCountAndRejectsChangedIdempotentRequests() {
        String c=campaign(10);
        var request=batch("IMAGE","selected-five",5);
        var batch=service.createBatch(c,request);
        assertThat(service.createBatch(c,request).get("id")).isEqualTo(batch.get("id"));
        assertThat(db.rows("SELECT * FROM aigc_task WHERE kind='IMAGE'")).hasSize(5);
        assertThat(db.rows("SELECT * FROM marketing_review_log WHERE action='IMAGE_RESERVE'")).hasSize(5);
        assertThatThrownBy(()->service.createBatch(c,batch("IMAGE","selected-five",6))).hasMessageContaining("张数");
        for(int invalid:List.of(-1,0,19)) assertThatThrownBy(()->service.createBatch(c,batch("IMAGE","invalid-"+invalid,invalid))).hasMessageContaining("1–18");
        assertThat(db.rows("SELECT * FROM aigc_task")).hasSize(5);
        verifyNoInteractions(images);
    }
    @Test void selectedTotalOnlyGeneratesPostersForTheMovieSelectedInTheEditor() {
        String c=(String)service.createCampaign(new MarketingDtos.Campaign(10L,"组合活动","周末一起看电影","朋友",List.of(1L,2L),List.of("简洁","节日"),List.of("1:1","3:4"))).get("id");
        service.createBatch(c,new MarketingDtos.Batch("ALL","total-three",3,posterContent(2)));
        var tasks=db.rows("SELECT * FROM aigc_task");
        assertThat(tasks.stream().filter(t->str(t,"kind").equals("IMAGE")).count()).isEqualTo(3);
        assertThat(tasks.stream().filter(t->str(t,"kind").equals("COPY")).count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aigc_task WHERE kind='IMAGE' AND movie_id=1",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aigc_task WHERE kind='IMAGE' AND movie_id=2",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aigc_task WHERE kind='IMAGE' AND ratio='3:4'",Integer.class)).isEqualTo(1);
    }
    @Test void selectedImageCountUsesTheActualQuotaAndRollsBackRejectedBatches() {
        settings.setDailyImageLimit(2); String c=campaign(10);
        assertThatThrownBy(()->service.createBatch(c,batch("IMAGE","too-many",3))).hasMessageContaining("额度");
        assertThat(db.rows("SELECT * FROM marketing_batch")).isEmpty();
        assertThat(db.rows("SELECT * FROM aigc_task")).isEmpty();
        assertThat(db.rows("SELECT * FROM marketing_review_log WHERE action='IMAGE_RESERVE'")).isEmpty();
        service.createBatch(c,batch("IMAGE","fits-quota",2));
        assertThat(db.rows("SELECT * FROM aigc_task")).hasSize(2);
    }
    @Test void secondMovieIsUsedForLanguageModelImageTasksAndSavedAssets() throws Exception {
        jdbc.update("UPDATE movie SET title='好东西' WHERE id=1");
        jdbc.update("UPDATE movie SET title='海洋奇缘2' WHERE id=2");
        String c=(String)service.createCampaign(new MarketingDtos.Campaign(10L,"会员日","会员日观影","朋友",List.of(1L,2L),List.of("简洁"),List.of("3:4"))).get("id");
        when(text.completeJson(eq(MarketingPrompts.IMAGE_INSTRUCTION),any())).thenAnswer(call -> {
            var context=(Map<?,?>)call.getArgument(1);
            var movie=(com.fasterxml.jackson.databind.JsonNode)context.get("movie");
            assertThat(movie.path("id").asLong()).isEqualTo(2);
            assertThat(movie.path("title").asText()).isEqualTo("海洋奇缘2");
            assertThat(context.get("marketingTitle")).isEqualTo("向着未知海域出发");
            return json.valueToTree(Map.of("movieId",2,"prompt","蓝色海浪与金色星光交织的远航场景，底部保留暗色留白。"));
        });
        service.createBatch(c,new MarketingDtos.Batch("IMAGE","second-movie",2,new MarketingDtos.PosterContent(2L,"向着未知海域出发","与朋友一起穿越星光与海浪，踏上新的远航。")));
        runAll();
        assertThat(db.rows("SELECT * FROM aigc_task WHERE movie_id=2 AND status='SUCCEEDED'")).hasSize(2);
        assertThat(db.rows("SELECT * FROM marketing_asset WHERE movie_id=2")).hasSize(2);
        assertThat(db.rows("SELECT * FROM marketing_asset WHERE movie_id=1")).isEmpty();
        verify(images,times(2)).generate(startsWith("蓝色海浪"),eq("3:4"));
        assertThat(service.capabilities().get("posterContentRequired")).isEqualTo(true);
    }
    @Test void countMigrationBackfillsLegacyBatchesAndPreservesTheirRetryIdentity() throws Exception {
        String c=campaign(10); var request=batch("IMAGE","before-migration",3);
        var original=service.createBatch(c,request);
        jdbc.execute("ALTER TABLE marketing_batch DROP COLUMN image_count");
        try(var connection=jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("db/flyway/V26__marketing_batch_image_count.sql"));
        }
        assertThat(service.createBatch(c,request).get("id")).isEqualTo(original.get("id"));
        assertThat(num(db.one("SELECT * FROM marketing_batch"),"image_count")).isEqualTo(3);
        assertThat(db.rows("SELECT * FROM aigc_task")).hasSize(3);
    }
    @Test void missingOrBlankEditorContentIsRejectedBeforeCreatingTasksOrReservingQuota() {
        String c=campaign(10);
        assertThatThrownBy(()->service.createBatch(c,new MarketingDtos.Batch("IMAGE","missing",1))).hasMessageContaining("编辑发布内容");
        for(var invalid:List.of(new MarketingDtos.PosterContent(1L," ","宣传文案"),new MarketingDtos.PosterContent(1L,"标题"," "),
            new MarketingDtos.PosterContent(1L,"题".repeat(61),"文案"),new MarketingDtos.PosterContent(1L,"标题","文".repeat(301))))
            assertThatThrownBy(()->service.createBatch(c,new MarketingDtos.Batch("IMAGE","invalid",1,invalid))).hasMessageContaining("宣传文案");
        assertThatThrownBy(()->service.createBatch(c,new MarketingDtos.Batch("IMAGE","foreign-movie",1,posterContent(2)))).hasMessageContaining("不属于");
        assertThat(db.rows("SELECT * FROM marketing_batch")).isEmpty();
        assertThat(db.rows("SELECT * FROM aigc_task")).isEmpty();
        assertThat(db.rows("SELECT * FROM marketing_review_log WHERE action='IMAGE_RESERVE'")).isEmpty();
    }
    @Test void editorSnapshotSurvivesCompletionAndChangedCopyCannotReuseTheSameRequestKey() {
        String c=campaign(10); var request=batch("IMAGE","editor-snapshot",1);
        String batchId=(String)service.createBatch(c,request).get("id");
        assertThatThrownBy(()->service.createBatch(c,new MarketingDtos.Batch("IMAGE","editor-snapshot",1,
            new MarketingDtos.PosterContent(1L,"修改后的标题",request.posterContent().copy())))).hasMessageContaining("宣传文案");
        assertThatThrownBy(()->service.createBatch(c,new MarketingDtos.Batch("IMAGE","editor-snapshot",1,
            new MarketingDtos.PosterContent(1L,request.posterContent().title(),"修改后的宣传文案")))).hasMessageContaining("宣传文案");
        runAll();
        assertThat(service.createBatch(c,request).get("id")).isEqualTo(batchId);
        var result=db.parse(str(db.one("SELECT * FROM aigc_task"),"result"));
        assertThat(result.path("posterContent").path("title").asText()).isEqualTo(request.posterContent().title());
        assertThat(result.path("posterContent").path("copy").asText()).isEqualTo(request.posterContent().copy());
        assertThat(db.rows("SELECT * FROM marketing_batch")).hasSize(1);
        assertThat(db.rows("SELECT * FROM aigc_task")).hasSize(1);
    }
    private byte[] png() throws Exception {
        var out=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(160,160,BufferedImage.TYPE_INT_RGB),"png",out); return out.toByteArray();
    }
}
