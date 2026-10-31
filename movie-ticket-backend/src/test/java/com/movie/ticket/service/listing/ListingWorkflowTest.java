package com.movie.ticket.service.listing;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.movie.ticket.TestIdentity;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import com.movie.ticket.service.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SQL, MyBatis services and Spring transactions, isolated from the development database. */
@SpringJUnitConfig(ListingWorkflowTest.Config.class)
class ListingWorkflowTest {
    @Configuration @EnableTransactionManagement @MapperScan("com.movie.ticket.mapper")
    static class Config {
        @Bean DataSource dataSource() { var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:listing;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000"); return ds; }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception { var f=new MybatisSqlSessionFactoryBean(); f.setDataSource(ds); return f.getObject(); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean ObjectMapper json() { return new ObjectMapper().registerModule(new JavaTimeModule()); }
        @Bean ListingAiClient ai() { return mock(ListingAiClient.class); }
        @Bean ListingImportService imports(ListingService workflow,ObjectMapper json,ListingAiClient ai,FileUploadService uploads) { return new ListingImportService(workflow,new ListingSourceReader(),new ListingRecordParser(json),ai,uploads); }
        @Bean FileUploadService uploads() { return mock(FileUploadService.class); }
        @Bean MovieService movies(MovieMapper m) { return new MovieService(m,mock(RedisTemplate.class)); }
        @Bean CinemaService cinemas(CinemaMapper c) { return new CinemaService(c); }
        @Bean HallService halls(HallMapper h,CinemaMapper c) { return new HallService(h,c); }
        @Bean ListingService listing(DataSource ds,ObjectMapper json,ListingAiClient ai,FileUploadService uploads,MovieService movies,CinemaService cinemas,HallService halls) {
            return new ListingService(new JdbcTemplate(ds),json,ai,new ListingSourceReader(),uploads,movies,cinemas,halls);
        }
    }
    @Autowired ListingService service;
    @Autowired ListingImportService imports;
    @Autowired ListingAiClient ai;
    @Autowired FileUploadService uploads;
    @Autowired ObjectMapper json;
    @Autowired DataSource ds;
    JdbcTemplate jdbc;

    @BeforeEach void setup() {
        TestIdentity.admin(); reset(ai,uploads); jdbc=new JdbcTemplate(ds); jdbc.execute("DROP ALL OBJECTS");
        for (Class<?> c:List.of(Movie.class,Cinema.class,Hall.class)) createTable(c);
        new ResourceDatabasePopulator(new ClassPathResource("db/flyway/V28__listing_drafts.sql"),new ClassPathResource("db/flyway/V29__listing_batch_import.sql")).execute(ds);
        jdbc.update("INSERT INTO cinema(id,name,address,status,deleted) VALUES(10,'东城影院','东城路',1,0),(20,'西城影院','西城路',1,0)");
    }
    @AfterEach void cleanup() { TestIdentity.clear(); }
    private ListingService.Draft movie() { return service.create("MOVIE",null,"{\"title\":\"银幕旅途\",\"genre\":\"剧情\",\"duration\":120,\"description\":\"发行方提供的剧情。\",\"releaseDate\":\"2026-11-01\"}",List.of()); }
    private ListingService.Review review(int version) { return new ListingService.Review(version,true,""); }
    @Test void extractionDoesNotPublishAndExplicitReviewPublishesExactlyOnce() {
        var d=movie(); assertThat(d.status()).isEqualTo("DRAFT"); assertThat(d.extractionMode()).isEqualTo("STRUCTURED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movie",Integer.class)).isZero();
        assertThatThrownBy(()->service.publish(d.id(),new ListingService.Review(1,false,""))).hasMessageContaining("确认");
        var published=service.publish(d.id(),review(1));
        assertThat(published.entityId()).isNotNull(); assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT status FROM movie",Integer.class)).isEqualTo(2);
        assertThat(service.publish(d.id(),review(1)).entityId()).isEqualTo(published.entityId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movie",Integer.class)).isEqualTo(1);
        assertThat(published.history()).hasSize(2);
        assertThatThrownBy(()->service.save(d.id(),new ListingService.Edit(2,d.fields()))).hasMessageContaining("已发布");
    }
    @Test void rejectedDraftRequiresEditingAndOldVersionsCannotOverwrite() {
        var d=movie(); service.reject(d.id(),new ListingService.Review(1,false,"片长待核实"));
        assertThatThrownBy(()->service.publish(d.id(),review(2))).hasMessageContaining("修改并暂存");
        assertThatThrownBy(()->service.save(d.id(),new ListingService.Edit(1,d.fields()))).hasMessageContaining("已被其他管理员修改");
        var revised=service.save(d.id(),new ListingService.Edit(2,d.fields()));
        assertThat(revised.status()).isEqualTo("DRAFT");
        assertThat(service.publish(d.id(),review(3)).history()).hasSize(4);
    }
    @Test void missingAndInvalidFieldsCannotPublishAndAiCannotInjectIdsOrRating() throws Exception {
        when(ai.textConfigured()).thenReturn(true);
        when(ai.extract(anyString(),any())).thenReturn(json.readTree("{\"fields\":{\"title\":\"仅片名\",\"id\":88,\"rating\":9.9,\"cinemaId\":20,\"status\":1}}"));
        var d=service.create("MOVIE",null,"片名：仅片名",List.of());
        assertThat(d.extractionMode()).isEqualTo("AI_TEXT"); assertThat(d.fields().has("rating")).isFalse();
        assertThat(d.fields().has("id")).isFalse(); assertThat(d.fields().path("status").asInt()).isEqualTo(2);
        assertThatThrownBy(()->service.publish(d.id(),review(1))).hasMessageContaining("时长");
        var fields=(com.fasterxml.jackson.databind.node.ObjectNode)d.fields().deepCopy(); fields.put("duration",-1); fields.put("releaseDate","明天");
        var changed=service.save(d.id(),new ListingService.Edit(1,fields));
        assertThat(changed.issues()).anyMatch(i->i.contains("1-600")).anyMatch(i->i.contains("日期"));
    }
    @Test void unavailableAiPreservesTextAndDoesNotPretendToRecognize() throws Exception {
        var d=service.create("MOVIE",null,"发行方原始文字资料",List.of());
        assertThat(d.extractionMode()).isEqualTo("MANUAL"); assertThat(d.extractionWarning()).contains("未配置");
        assertThat(d.sourceText()).isEqualTo("发行方原始文字资料"); assertThat(d.fields().has("title")).isFalse();
        when(ai.textConfigured()).thenReturn(true); when(ai.extract(anyString(),any())).thenThrow(new java.io.IOException("private-upstream-detail"));
        var failed=service.create("MOVIE",null,"另一个原始资料",List.of());
        assertThat(failed.extractionWarning()).contains("暂时失败").doesNotContain("private-upstream-detail");
    }
    @Test void cinemaAdminScopeAppliesToCreateListReadEditRejectAndPublish() {
        var platformDraft=movie();
        TestIdentity.login(2L,"CINEMA_ADMIN",10L);
        assertThatThrownBy(()->service.create("MOVIE",null,"资料",List.of())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.create("CINEMA",null,"资料",List.of())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.create("HALL",20L,"资料",List.of())).hasMessageContaining("其他影院");
        String payload="{\"name\":\"小厅\",\"cinemaId\":20,\"totalRows\":2,\"totalCols\":2,\"seatLayout\":{\"seats\":[[1,1],[1,0]],\"aisleAfterCols\":[1]}}";
        var own=service.create("HALL",10L,payload,List.of());
        assertThat(own.cinemaId()).isEqualTo(10L); assertThat(own.fields().has("cinemaId")).isFalse();
        assertThat(((List<?>)service.list(null,null,1,20).get("records"))).hasSize(1);
        assertThatThrownBy(()->service.get(platformDraft.id())).isInstanceOf(BusinessException.class);
        TestIdentity.login(3L,"CINEMA_ADMIN",20L);
        assertThatThrownBy(()->service.get(own.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.save(own.id(),new ListingService.Edit(1,own.fields()))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.reject(own.id(),new ListingService.Review(1,false,"拒绝"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.publish(own.id(),review(1))).isInstanceOf(BusinessException.class);
        TestIdentity.login(2L,"CINEMA_ADMIN",10L); var published=service.publish(own.id(),review(1));
        assertThat(jdbc.queryForObject("SELECT cinema_id FROM hall WHERE id=?",Long.class,published.entityId())).isEqualTo(10L);
        TestIdentity.login(2L,"CINEMA_ADMIN",null); assertThatThrownBy(()->service.list(null,null,1,20)).hasMessageContaining("尚未分配");
    }
    @Test void csvQuotesAndSingleRecordRuleAreEnforced() {
        var d=service.create("MOVIE",null,"title,duration,genre,description\n\"旅途,再见\",120,剧情,\"第一行\n第二行\"",List.of());
        assertThat(d.fields().path("title").asText()).isEqualTo("旅途,再见");
        assertThat(d.fields().path("description").asText()).contains("第二行");
        assertThatThrownBy(()->service.create("MOVIE",null,"title,duration\n一,120\n二,100",List.of())).hasMessageContaining("拆分");
        assertThatThrownBy(()->service.create("MOVIE",null,"[{\"title\":\"一\"},{\"title\":\"二\"}]",List.of())).hasMessageContaining("一条");
    }
    @Test void duplicateAndInvalidSeatLayoutCannotPublish() {
        var first=movie(); var second=movie(); service.publish(first.id(),review(1));
        assertThatThrownBy(()->service.publish(second.id(),review(1))).hasMessageContaining("重复");
        var hall=service.create("HALL",10L,"{\"name\":\"不规则厅\",\"totalRows\":2,\"totalCols\":2,\"seatLayout\":\"[[1,1]]\"}",List.of());
        assertThatThrownBy(()->service.publish(hall.id(),review(1))).hasMessageContaining("布局");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hall",Integer.class)).isZero();
    }
    @Test void deletedTestMovieCanBeImportedAgainWithANewDraft() {
        var first=movie(); var published=service.publish(first.id(),review(1));
        jdbc.update("UPDATE movie SET deleted=1 WHERE id=?",published.entityId());
        var next=movie(); var replacement=service.publish(next.id(),review(1));
        assertThat(replacement.entityId()).isNotEqualTo(published.entityId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movie WHERE deleted=0",Integer.class)).isEqualTo(1);
    }
    @Test void publishRollsBackIfLiveInsertFailsAndCinemaFieldsPublish() {
        var d=service.create("CINEMA",null,"{\"name\":\"新影院\",\"address\":\"新街道一号\",\"lng\":116.1234567,\"lat\":39.1234567}",List.of());
        jdbc.execute("ALTER TABLE cinema ADD CONSTRAINT forbid_new CHECK (name <> '新影院')");
        assertThatThrownBy(()->service.publish(d.id(),review(1))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(service.get(d.id()).status()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT publication_key FROM listing_draft WHERE id=?",String.class,d.id())).isNull();
        jdbc.execute("ALTER TABLE cinema DROP CONSTRAINT forbid_new");
        assertThat(service.publish(d.id(),review(1)).entityId()).isNotNull();
    }
    @Test void concurrentPublishingOfSameDraftProducesOneLiveRecord() throws Exception {
        var d=movie(); var gate=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Long> publish=()-> { TestIdentity.admin(); try { gate.await(); return service.publish(d.id(),review(1)).entityId(); } finally { TestIdentity.clear(); } };
            Future<Long> a=pool.submit(publish),b=pool.submit(publish); gate.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movie",Integer.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    private String batchMovies(int count) {
        var records=json.createArrayNode();
        for (int n=1;n<=count;n++) records.addObject().put("title","批量影片"+n).put("duration",90+n).put("genre","剧情").put("description","第"+n+"部影片的独立简介");
        return records.toString();
    }
    @Test void jsonArrayCreatesAllFiveDraftsAndPublishesOnlyTheReviewedOne() {
        var batch=imports.create("MOVIE",null,batchMovies(5),List.of());
        assertThat(batch.total()).isEqualTo(5); assertThat(batch.extractionMode()).isEqualTo("STRUCTURED");
        assertThat(batch.drafts()).extracting(d->d.fields().path("title").asText()).containsExactly("批量影片1","批量影片2","批量影片3","批量影片4","批量影片5");
        assertThat(service.batch(batch.id())).hasSize(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movie",Integer.class)).isZero();
        var selected=batch.drafts().get(2); service.publish(selected.id(),review(1));
        assertThat(jdbc.queryForObject("SELECT title FROM movie",String.class)).isEqualTo("批量影片3");
        assertThat(service.get(batch.drafts().getFirst().id()).status()).isEqualTo("DRAFT");
        assertThat(service.get(selected.id()).batchId()).isEqualTo(batch.id());
        verifyNoInteractions(ai);
    }
    @Test void multiRowChineseCsvAndMultipleJsonFilesKeepEveryRecord() {
        var csv=imports.create("MOVIE",null,"电影名称,时长（分钟）,类型,剧情简介\n一,90,剧情,\"第一部,有逗号\"\n二,100,喜剧,\"第二部\n有换行\"",List.of());
        assertThat(csv.total()).isEqualTo(2); assertThat(csv.drafts().get(1).fields().path("description").asText()).contains("有换行");
        var one=new org.springframework.mock.web.MockMultipartFile("files","one.json","application/json","{\"title\":\"文件一\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var two=new org.springframework.mock.web.MockMultipartFile("files","two.json","application/json","[{\"title\":\"文件二\"},{\"title\":\"文件三\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(imports.create("MOVIE",null,null,List.of(one,two)).total()).isEqualTo(3);
    }
    @Test void aiReturnsIndependentRecordsAndUnavailableModelReportsUnsplitSource() throws Exception {
        when(ai.textConfigured()).thenReturn(true);
        when(ai.extract(anyString(),any())).thenReturn(json.readTree("{\"records\":"+batchMovies(3)+"}"));
        var batch=imports.create("MOVIE",null,"三部影片的发行资料原文",List.of());
        assertThat(batch.total()).isEqualTo(3); assertThat(batch.extractionMode()).isEqualTo("AI_TEXT");
        assertThat(batch.drafts().get(2).fields().path("description").asText()).isEqualTo("第3部影片的独立简介");
        when(ai.textConfigured()).thenReturn(false);
        var manual=imports.create("MOVIE",null,"尚未拆分的多部影片原文",List.of());
        assertThat(manual.total()).isEqualTo(1); assertThat(manual.warning()).contains("无法自动拆分"); assertThat(manual.extractionMode()).isEqualTo("MANUAL");
    }
    @Test void explicitAiModeReadsUnformattedMaterialInsteadOfApplyingTemplateRules() throws Exception {
        when(ai.textConfigured()).thenReturn(true);
        when(ai.extract(anyString(),any())).thenReturn(json.readTree("{\"records\":"+batchMovies(2)+"}"));
        var batch=imports.create("MOVIE",null,"【发行方资料】\n影片介绍混合排版，不是 JSON 数组。",List.of(),"AI");
        assertThat(batch.total()).isEqualTo(2); assertThat(batch.extractionMode()).isEqualTo("AI_TEXT");
        verify(ai).extract(eq("MOVIE"),argThat(source->source.text().contains("混合排版")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movie",Integer.class)).isZero();
    }
    @Test void templateModeNeverCallsAiAndRejectsUnstructuredSources() {
        assertThat(imports.create("MOVIE",null,batchMovies(2),List.of(),"TEMPLATE").total()).isEqualTo(2);
        assertThatThrownBy(()->imports.create("MOVIE",null,"影片简介原文",List.of(),"TEMPLATE")).hasMessageContaining("切换到 AI 导入");
        var file=new org.springframework.mock.web.MockMultipartFile("files","source.txt","text/plain",new byte[]{1});
        assertThatThrownBy(()->imports.create("MOVIE",null,null,List.of(file),"TEMPLATE")).hasMessageContaining("仅支持");
        assertThatThrownBy(()->imports.create("MOVIE",null,batchMovies(1),List.of(),"UNKNOWN")).hasMessageContaining("导入方式无效");
        verifyNoInteractions(ai);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing_draft",Integer.class)).isEqualTo(2);
    }
    @Test void explicitAiModeKeepsFailuresOutOfDraftsAndDoesNotSilentlyUseTemplates() throws Exception {
        assertThatThrownBy(()->imports.create("MOVIE",null,batchMovies(1),List.of(),"AI")).hasMessageContaining("文字模型未配置");
        when(ai.textConfigured()).thenReturn(true);
        when(ai.extract(anyString(),any())).thenThrow(new java.io.IOException("unavailable"));
        assertThatThrownBy(()->imports.create("MOVIE",null,"发行原文",List.of(),"AI")).hasMessageContaining("AI 识别暂时失败");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing_draft",Integer.class)).isZero();
    }
    @Test void invalidRecordAndLimitFailureNeverSaveAPartialBatch() throws Exception {
        assertThatThrownBy(()->imports.create("MOVIE",null,batchMovies(21),List.of())).hasMessageContaining("20");
        assertThatThrownBy(()->imports.create("MOVIE",null,"[{\"title\":\"可识别\"},null]",List.of())).hasMessageContaining("第 2 条");
        assertThatThrownBy(()->imports.create("MOVIE",null,"[]",List.of())).hasMessageContaining("列表为空");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing_draft",Integer.class)).isZero();
        jdbc.execute("ALTER TABLE listing_draft ADD CONSTRAINT reject_second CHECK (fields NOT LIKE '%批量影片2%')");
        assertThatThrownBy(()->imports.create("MOVIE",null,batchMovies(3),List.of())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing_draft",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing_review_log",Integer.class)).isZero();
    }
    @Test void batchLookupAndEveryHallStayInAssignedCinemaScope() {
        TestIdentity.login(2L,"CINEMA_ADMIN",10L);
        var batch=imports.create("HALL",10L,"[{\"name\":\"一号厅\"},{\"name\":\"二号厅\"}]",List.of());
        assertThat(batch.drafts()).allMatch(d->d.cinemaId().equals(10L)); assertThat(service.batch(batch.id())).hasSize(2);
        TestIdentity.login(3L,"CINEMA_ADMIN",20L);
        assertThatThrownBy(()->service.batch(batch.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->imports.create("HALL",10L,"[{\"name\":\"越权\"}]",List.of())).isInstanceOf(BusinessException.class);
    }
    @Test void sharedImageIsUploadedOnceAndNotAssignedAsEveryMoviePoster() throws Exception {
        var out=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);
        var image=new org.springframework.mock.web.MockMultipartFile("files","poster.png","image/png",out.toByteArray());
        when(uploads.uploadImage(any())).thenReturn(Map.of("url","/api/uploads/shared.png"));
        var batch=imports.create("MOVIE",null,batchMovies(3),List.of(image));
        verify(uploads,times(1)).uploadImage(any());
        assertThat(batch.drafts()).allMatch(d->!d.fields().has("poster") && d.sourceImages().equals(List.of("/api/uploads/shared.png")));
        assertThat(batch.warning()).contains("逐条核对");
    }
    @Test void fiveMovieExcelFixtureImportsAndValidatesAllRows() throws Exception {
        var bytes=new ClassPathResource("listing/five-test-movies.xlsx").getContentAsByteArray();
        var file=new org.springframework.mock.web.MockMultipartFile("files","五部影片.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes);
        var batch=imports.create("MOVIE",null,null,List.of(file));
        assertThat(batch.total()).isEqualTo(5); assertThat(batch.drafts()).allMatch(d->d.issues().isEmpty());
        assertThat(batch.drafts().getFirst().fields().path("releaseDate").asText()).isEqualTo("2026-11-06");
        assertThat(batch.drafts().getLast().fields().path("title").asText()).isEqualTo("测试·最后一公里");
        for (var draft:batch.drafts()) service.publish(draft.id(),review(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movie",Integer.class)).isEqualTo(5);
    }
    private void createTable(Class<?> entity) {
        List<String> columns=new ArrayList<>();
        for (var field:entity.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            String column=field.getName().replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase();
            Class<?> type=field.getType();
            String sqlType=type==Long.class?"BIGINT":type==Integer.class?"INT":type==BigDecimal.class?"DECIMAL(18,7)":type==LocalDate.class?"DATE":type==LocalDateTime.class?"TIMESTAMP":"VARCHAR(20000)";
            columns.add(column+" "+(column.equals("id")?"BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY":sqlType+(column.equals("deleted")?" DEFAULT 0":"")));
        }
        jdbc.execute("CREATE TABLE "+entity.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value()+" ("+String.join(",",columns)+")");
    }
}
