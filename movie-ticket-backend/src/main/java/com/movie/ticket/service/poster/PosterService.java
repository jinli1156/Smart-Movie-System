package com.movie.ticket.service.poster;

import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.service.marketing.MarketingImageClient;
import com.movie.ticket.service.marketing.MarketingSettings;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j @Service
public class PosterService {
    public record Style(String id, String name, String description) {}
    public static final List<Style> STYLES = List.of(
        new Style("CYBERPUNK", "赛博朋克", "霓虹都市、机械装甲与未来光影"),
        new Style("FANTASY", "东方神话", "神话意象、流云烈焰与东方奇境"),
        new Style("SCIFI", "星际科幻", "深空星云、宇宙航行与宏大银幕"),
        new Style("ANIME", "日系动画", "细腻笔触、明亮色彩与青春故事"),
        new Style("RETRO", "复古胶片", "胶片颗粒、复古色调与经典构图"),
        new Style("SUSPENSE", "悬疑电影", "明暗对照、迷雾与悬念氛围"));
    public record Create(@NotBlank @Pattern(regexp = "[a-fA-F0-9-]{36}") String requestId,
                         @Size(max = 80) String movieTitle, @NotBlank String style,
                         @Size(max = 1000) String prompt, @NotBlank String ratio) {}
    public record Poster(String id, String movieTitle, String style, String prompt, String ratio,
                         boolean hasPortrait, String status, String errorMessage, boolean retryable, LocalDateTime createdAt) {}
    record Task(String id, long userId, String requestHash, String movieTitle, String style, String prompt,
                String ratio, boolean hasPortrait, String portraitKey, String status, String providerResult,
                String storageKey, String errorMessage, boolean retryable, int attempts, LocalDateTime createdAt) {}
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db;
    private final MarketingImageClient images;
    private final MarketingSettings imageSettings;
    private final PosterSettings settings;
    private final PosterFiles files;
    private final AtomicInteger active = new AtomicInteger();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        var thread = new Thread(r, "personal-poster-worker"); thread.setDaemon(true); return thread;
    });
    public PosterService(JdbcTemplate db, MarketingImageClient images, MarketingSettings imageSettings, PosterSettings settings, PosterFiles files) {
        this.db = db; this.images = images; this.imageSettings = imageSettings; this.settings = settings; this.files = files;
    }
    private LocalDateTime now() { return LocalDateTime.now(ZONE); }
    private boolean available() { return settings.isWorkerEnabled() && imageSettings.imageConfigured(); }
    public Map<String, Object> capabilities() {
        Long used = db.queryForObject("SELECT COUNT(*) FROM personal_movie_poster WHERE user_id=? AND created_at>=?", Long.class,
            UserContext.getUserId(), now().toLocalDate().atStartOfDay());
        return Map.of("available", available(), "photoSupported", images.supportsReferencePhotos(), "styles", STYLES,
            "imageProvider", imageSettings.getImageProvider(), "imageModel", imageSettings.getImageModel(),
            "dailyLimit", settings.getDailyLimit(), "remaining", Math.max(0, settings.getDailyLimit() - (used == null ? 0 : used)),
            "message", available() ? "生成可能需要几分钟，离开页面后仍会继续创作" : "AI 海报服务暂未开放，请稍后再来");
    }
    public List<Poster> list() {
        return tasks("SELECT * FROM personal_movie_poster WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 50", UserContext.getUserId())
            .stream().map(this::view).toList();
    }
    public Poster get(String id) { return view(owned(id)); }

    @Transactional(rollbackFor = Exception.class)
    public Poster create(Create input, MultipartFile photo) throws IOException {
        String movie = Objects.toString(input.movieTitle(), "").strip(), prompt = Objects.toString(input.prompt(), "").strip();
        if (input.requestId() == null || !input.requestId().matches("[a-fA-F0-9-]{36}") || movie.length() > 80 || prompt.length() > 1000
            || STYLES.stream().noneMatch(s -> s.id().equals(input.style())) || input.ratio() == null || !Set.of("3:4", "1:1", "16:9").contains(input.ratio()))
            throw new BusinessException(400, "请检查电影主题、海报风格和画幅参数");
        if (movie.isBlank() && prompt.isBlank()) throw new BusinessException(400, "请填写电影主题或描述你的海报创意");
        byte[] portrait = files.portrait(photo);
        String hash = digest(movie + "\n" + input.style() + "\n" + prompt + "\n" + input.ratio(), portrait);
        long userId = UserContext.getUserId();
        // Serialize request replay and quota reservation for this account, including concurrent uploads.
        db.queryForList("SELECT id FROM `user` WHERE id=? FOR UPDATE", userId);
        var existing = tasks("SELECT * FROM personal_movie_poster WHERE user_id=? AND request_id=?", userId, input.requestId());
        if (!existing.isEmpty()) {
            if (!existing.getFirst().requestHash().equals(hash)) throw new BusinessException(409, "请求内容已改变，请重新提交海报创意");
            return view(existing.getFirst());
        }
        requireAvailable();
        if (portrait != null && !images.supportsReferencePhotos()) throw new BusinessException(400, "当前服务暂不支持照片，请移除照片后生成");
        Long count = db.queryForObject("SELECT COUNT(*) FROM personal_movie_poster WHERE user_id=? AND created_at>=?", Long.class,
            userId, now().toLocalDate().atStartOfDay());
        if (count != null && count >= settings.getDailyLimit()) throw new BusinessException(429, "今天的海报创作次数已用完，明天再来吧");
        String portraitKey = portrait == null ? null : files.savePortrait(portrait);
        if (portraitKey != null) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) removePortrait(portraitKey); }
        });
        String id = UUID.randomUUID().toString(); LocalDateTime timestamp = now();
        db.update("INSERT INTO personal_movie_poster(id,user_id,request_id,request_hash,movie_title,style,prompt,ratio,has_portrait,portrait_key,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,'QUEUED',?,?)",
            id, userId, input.requestId(), hash, movie, input.style(), prompt, input.ratio(), portrait != null, portraitKey, timestamp, timestamp);
        return view(task(id));
    }

    @Transactional
    public Poster retry(String id) {
        db.queryForList("SELECT id FROM personal_movie_poster WHERE id=? FOR UPDATE", id);
        Task t = owned(id);
        if (Set.of("QUEUED", "RUNNING", "SUCCEEDED").contains(t.status())) return view(t);
        if (!t.retryable() || t.attempts() >= 3) throw new BusinessException(409, "该任务暂不可重试，请核实生成结果");
        if (!settings.isWorkerEnabled()) throw new BusinessException(503, "海报创作服务暂不可用");
        if (t.providerResult() == null) requireAvailable();
        db.update("UPDATE personal_movie_poster SET status='QUEUED',error_message=NULL,retryable=FALSE,attempts=attempts+1,updated_at=? WHERE id=?", now(), id);
        return view(task(id));
    }

    public Path file(String id) {
        Task t = owned(id);
        if (!t.status().equals("SUCCEEDED")) throw new BusinessException(409, "海报还在创作中");
        Path path = files.path(t.storageKey());
        if (!Files.isRegularFile(path)) throw new BusinessException(404, "海报文件暂不可用");
        return path;
    }
    private void requireAvailable() { if (!available()) throw new BusinessException(503, "AI 海报服务暂不可用，请稍后再来"); }

    @Scheduled(fixedDelayString = "${poster.poll-interval-ms:2500}")
    public void poll() {
        if (!settings.isWorkerEnabled() || pool.isShutdown()) return;
        long expiry = Math.max(1200L, (long) imageSettings.getRequestTimeoutSeconds() + imageSettings.getDownloadTimeoutSeconds() + 180);
        db.update("UPDATE personal_movie_poster SET status='FAILED',retryable=(provider_result IS NOT NULL),error_message='创作任务中断；已有画面可继续保存，其他结果请先核实',updated_at=? WHERE status='RUNNING' AND updated_at<?", now(), now().minusSeconds(expiry));
        int capacity = 2 - active.get();
        if (capacity <= 0) return;
        for (Task t : tasks("SELECT * FROM personal_movie_poster WHERE status='QUEUED' ORDER BY created_at LIMIT " + capacity)) {
            active.incrementAndGet();
            try { pool.submit(() -> { try { generate(t.id()); } finally { active.decrementAndGet(); } }); }
            catch (RejectedExecutionException e) { active.decrementAndGet(); }
        }
    }

    void generate(String id) {
        if (db.update("UPDATE personal_movie_poster SET status='RUNNING',updated_at=? WHERE id=? AND status='QUEUED'", now(), id) != 1) return;
        boolean submitted = false, persisted = false;
        String result = null;
        try {
            Task t = task(id); result = t.providerResult(); persisted = result != null;
            if (result == null) {
                String prompt = imagePrompt(t);
                byte[] portrait = t.portraitKey() == null ? null : Files.readAllBytes(files.path(t.portraitKey()));
                submitted = true;
                result = portrait == null ? images.generate(prompt, t.ratio()) : images.generateWithReference(prompt, t.ratio(), portrait);
                db.update("UPDATE personal_movie_poster SET provider_result=?,updated_at=? WHERE id=?", result, now(), id);
                persisted = true;
            }
            String key = files.savePoster(images.download(result));
            db.update("UPDATE personal_movie_poster SET status='SUCCEEDED',storage_key=?,retryable=FALSE,error_message=NULL,updated_at=? WHERE id=?", key, now(), id);
            images.releaseResult(result);
            removePortrait(t.portraitKey());
            try { db.update("UPDATE personal_movie_poster SET portrait_key=NULL WHERE id=?", id); }
            catch (RuntimeException e) { log.warn("已完成海报的照片引用清理未完成 id={}", id); }
        } catch (MarketingImageClient.GenerationException e) {
            fail(id, e.getMessage(), !e.isUncertain());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); fail(id, "海报创作已中断，请稍后查看结果", persisted);
        } catch (Exception e) {
            log.warn("个性化海报任务异常 id={} type={}", id, e.getClass().getSimpleName());
            fail(id, persisted ? "画面已生成，保存未完成，可以继续保存" : submitted ? "生图结果尚未确定，请先核实，避免重复生成" : "创作暂未完成，请稍后重试", persisted || !submitted);
        }
    }
    private String imagePrompt(Task t) {
        Style style = STYLES.stream().filter(s -> s.id().equals(t.style())).findFirst().orElseThrow();
        return "创作一张专属电影风格观影海报，完整成片，具有电影海报的视觉叙事、光影和构图。\n"
            + "用户选择的视觉风格：" + style.name() + "，" + style.description() + "。\n"
            + "电影主题（可空）：" + t.movieTitle() + "\n用户创作描述：" + t.prompt() + "\n"
            + (t.hasPortrait() ? "附图为用户的人物参考照片。以照片中的人物作为唯一主角，尽可能保留其面部特征和辨识度，并根据电影主题及风格设计服装、姿态和场景。不要只把照片贴在背景上。\n"
                : "没有人物参考照片。若用户希望成为主角，创作虚构电影主角形象，不宣称还原用户本人相貌。\n")
            + "可使用简洁中文主标题与原创观影标语。创作新的海报，不直接复制已有海报。不添加官方出品、真实演员署名、票务凭证或二维码。";
    }
    private void removePortrait(String key) {
        try { files.delete(key); } catch (IOException e) { log.warn("人物参考照片清理未完成"); }
    }
    private void fail(String id, String message, boolean retryable) {
        db.update("UPDATE personal_movie_poster SET status='FAILED',error_message=?,retryable=?,updated_at=? WHERE id=?", message, retryable, now(), id);
    }
    private String digest(String input, byte[] photo) {
        try {
            var sha = MessageDigest.getInstance("SHA-256"); sha.update(input.getBytes(StandardCharsets.UTF_8));
            if (photo != null) sha.update(photo);
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Task owned(String id) {
        Task t = task(id);
        if (t.userId() != UserContext.getUserId()) throw new BusinessException(404, "海报不存在或无权访问");
        return t;
    }
    private Poster view(Task t) { return new Poster(t.id(), t.movieTitle(), t.style(), t.prompt(), t.ratio(), t.hasPortrait(), t.status(), t.errorMessage(), t.retryable() && t.attempts() < 3, t.createdAt()); }
    private Task task(String id) {
        var rows = tasks("SELECT * FROM personal_movie_poster WHERE id=?", id);
        if (rows.isEmpty()) throw new BusinessException(404, "海报不存在");
        return rows.getFirst();
    }
    private List<Task> tasks(String sql, Object... args) { return db.query(sql, this::map, args); }
    private Task map(ResultSet r, int index) throws SQLException {
        return new Task(r.getString("id"), r.getLong("user_id"), r.getString("request_hash"), r.getString("movie_title"),
            r.getString("style"), r.getString("prompt"), r.getString("ratio"), r.getBoolean("has_portrait"), r.getString("portrait_key"),
            r.getString("status"), r.getString("provider_result"), r.getString("storage_key"), r.getString("error_message"),
            r.getBoolean("retryable"), r.getInt("attempts"), r.getTimestamp("created_at").toLocalDateTime());
    }
    @PreDestroy public void close() { pool.shutdownNow(); }
}
