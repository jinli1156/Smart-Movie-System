package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.dto.ColdStartRequest;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import com.movie.ticket.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 混合推荐业务：融合内容偏好和用户行为，生成推荐理由、偏好画像及离线评估结果。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendService {

    private static final String HOT_MOVIES_KEY = "hot:movies";
    private static final String VIEW = "VIEW";
    private static final String FAVORITE = "FAVORITE";
    private static final double VIEW_WEIGHT = 1;
    private static final double FAVORITE_WEIGHT = 3;
    private static final double PURCHASE_WEIGHT = 5;
    private static final double FIVE_STAR_WEIGHT = 6;

    private final MovieMapper movieMapper;
    private final TicketOrderMapper orderMapper;
    private final ShowtimeMapper showtimeMapper;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final UserMovieBehaviorMapper behaviorMapper;
    private final CinemaMapper cinemaMapper;
    private final MovieRecommendationAiService movieRecommendationAiService;

    /** 按热度查询热门影片，供首页或推荐兜底使用。 */
    public List<MovieVO> getHotMovies(Integer limit) {
        int size = safeLimit(limit, 10);
        String key = HOT_MOVIES_KEY + ":" + size;
        try {
            List<String> ids = redisTemplate.opsForList().range(key, 0, -1);
            if (ids != null && !ids.isEmpty()) {
                return ids.stream().map(Long::valueOf).map(movieMapper::selectById)
                        .filter(Objects::nonNull).map(this::toMovieVO).toList();
            }
        } catch (RuntimeException exception) {
            log.warn("读取热门电影缓存失败，降级查库: {} - {}", key, exception.getMessage());
        }
        List<Movie> movies = candidates();
        TrendContext trend = trendContext(movies, LocalDateTime.now());
        List<Movie> result = movies.stream()
                .sorted(Comparator.comparingDouble((Movie m) -> trendScore(m, trend)).reversed())
                .limit(size).toList();
        try {
            if (!result.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(key, result.stream().map(m -> m.getId().toString()).toList());
                redisTemplate.expire(key, 1, TimeUnit.HOURS);
            }
        } catch (RuntimeException exception) {
            log.warn("写入热门电影缓存失败: {} - {}", key, exception.getMessage());
        }
        return result.stream().map(this::toMovieVO).toList();
    }

    /** 融合用户偏好与行为生成个性化影片及推荐理由。 */
    public List<MovieRecommendationVO> getRecommendedMovies(Long userId, Integer limit,
                                                              Double latitude, Double longitude) {
        int size = safeLimit(limit, 10);
        List<Movie> movies = candidates();
        List<MovieRecommendationVO> pool = recommend(userId, size, latitude, longitude, movies,
                RecommendationOptions.production());
        return movieRecommendationAiService.rank(pool, viewingEvidence(userId, movies), size);
    }

    private List<MovieRecommendationVO> recommend(Long userId, int limit, Double latitude,
                                                   Double longitude, List<Movie> movies,
                                                   RecommendationOptions options) {
        if (movies.isEmpty()) return List.of();
        Map<Long, Movie> movieMap = movies.stream().collect(Collectors.toMap(Movie::getId, Function.identity()));
        UserSignals signals = signals(userId, movieMap, options);
        LocalDateTime referenceTime = options.cutoff() == null ? LocalDateTime.now() : options.cutoff();
        SceneContext scene = sceneContext(movies, latitude, longitude, referenceTime, options.cutoff() != null);
        TrendContext trend = trendContext(movies, referenceTime, options.cutoff() != null);
        List<ScoredMovie> scored = new ArrayList<>();
        for (Movie movie : movies) {
            if (signals.purchased().contains(movie.getId())) continue;
            double content = contentScore(movie, signals.profile());
            double preference = preferenceScore(movie, signals.explicitGenres());
            double behavior = behaviorScore(movie, signals.interactions(), movieMap);
            double sceneValue = options.useScene() ? sceneScore(movie, scene) : 0;
            double trendValue = options.useTrend() ? trendScore(movie, trend) : 0;
            double total = weightedTotal(content, preference, behavior, sceneValue, trendValue, options);
            scored.add(new ScoredMovie(movie, clamp(total), content, preference, behavior, sceneValue, trendValue,
                    reason(movie, signals, movieMap, scene)));
        }
        List<ScoredMovie> selected = options.useDiversity() ? diversify(scored, limit) : scored.stream()
                .sorted(Comparator.comparingDouble(ScoredMovie::total).reversed()).limit(limit).toList();
        return selected.stream().map(this::toRecommendationVO).toList();
    }

    /** 返回用于偏好雷达图的维度分数及依据。 */
    public RecommendationRadarVO getRadar(Long userId) {
        List<MovieRecommendationVO> recommendations = recommend(userId, 8, null, null, candidates(),
                RecommendationOptions.production());
        RecommendationProfileVO profile = profileFacts(userId);
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("内容契合", averageScore(recommendations, score -> score.getContent()));
        dimensions.put("显式偏好", averageScore(recommendations, score -> score.getUserPreference()));
        dimensions.put("行为相似", averageScore(recommendations, score -> score.getBehavior()));
        dimensions.put("场景适配", averageScore(recommendations, score -> score.getScene()));
        dimensions.put("近期趋势", averageScore(recommendations, score -> score.getTrend()));
        int evidence = profile.getViewedCount() + profile.getFavoriteCount()
                + profile.getPurchasedCount() + profile.getRatedCount();
        String strongest = dimensions.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("内容契合");
        return RecommendationRadarVO.builder().dimensions(dimensions)
                .topGenres(profile.getFavoriteGenres().stream().limit(5).toList())
                .evidenceCount(evidence)
                .summary(evidence == 0 ? "暂无行为证据，当前以影片口碑和趋势作为冷启动依据"
                        : "当前推荐主要由“" + strongest + "”驱动，共使用 " + evidence + " 条用户侧证据")
                .build();
    }

    /** 依据影片内容特征返回相似影片。 */
    public List<MovieVO> getSimilarMovies(Long movieId, Integer limit) {
        Movie source = movieMapper.selectById(movieId);
        if (source == null) return List.of();
        return candidates().stream().filter(movie -> !movieId.equals(movie.getId()))
                .sorted(Comparator.comparingDouble((Movie m) -> similarity(source, m)).reversed())
                .limit(safeLimit(limit, 6)).map(this::toMovieVO).toList();
    }

    /** 生成用户观影偏好画像与解释。 */
    public RecommendationProfileVO getProfile(Long userId) {
        return movieRecommendationAiService.describe(profileFacts(userId), viewingEvidence(userId, candidates()));
    }

    private Map<String, Object> viewingEvidence(Long userId, List<Movie> movies) {
        Map<Long, Movie> catalog = movies.stream().collect(Collectors.toMap(Movie::getId, Function.identity()));
        UserSignals signals = signals(userId, catalog, RecommendationOptions.production());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("explicitGenres", signals.explicitGenres());
        evidence.put("weightedGenres", signals.profile().genres());
        evidence.put("weightedDirectors", signals.profile().directors());
        evidence.put("weightedLanguages", signals.profile().languages());
        evidence.put("preferredDuration", signals.profile().duration());
        return evidence;
    }

    private RecommendationProfileVO profileFacts(Long userId) {
        User user = userMapper.selectById(userId);
        List<UserMovieBehavior> behaviors = behaviors(userId);
        long purchased = orderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getStatus, 1));
        long rated = commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getUserId, userId));
        int viewed = behaviors.stream().filter(b -> VIEW.equals(b.getBehaviorType()))
                .mapToInt(b -> value(b.getBehaviorCount())).sum();
        int favorites = (int) behaviors.stream().filter(b -> FAVORITE.equals(b.getBehaviorType())).count();
        List<String> genres = user == null || user.getFavoriteGenres() == null ? List.of() : user.getFavoriteGenres();
        return RecommendationProfileVO.builder()
                .needsColdStart(viewed == 0 && favorites == 0 && purchased == 0 && rated == 0 && genres.isEmpty())
                .favoriteGenres(genres).viewedCount(viewed).favoriteCount(favorites)
                .purchasedCount((int) purchased).ratedCount((int) rated).build();
    }

    /** 保存新用户选择的影片及类型偏好，初始化推荐画像。 */
    @Transactional(rollbackFor = Exception.class)
    public void initializeColdStart(Long userId, ColdStartRequest request) {
        List<Long> ids = request.getMovieIds().stream().distinct().toList();
        if (ids.size() < 3) throw new BusinessException("请至少选择三部不同的电影");
        List<Movie> movies = movieMapper.selectBatchIds(ids);
        if (movies.size() != ids.size()) throw new BusinessException("选择中包含不存在的电影");
        movies.forEach(movie -> setFavorite(userId, movie.getId(), true));
        List<String> genres = request.getFavoriteGenres() == null ? new ArrayList<>()
                : new ArrayList<>(request.getFavoriteGenres());
        if (genres.isEmpty()) {
            Map<String, Long> counts = movies.stream().flatMap(m -> tokens(m.getGenre()).stream())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            genres = counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5).map(Map.Entry::getKey).toList();
        }
        User update = new User();
        update.setId(userId);
        update.setFavoriteGenres(genres.stream().distinct().limit(5).toList());
        userMapper.updateById(update);
    }

    /** 记录影片浏览行为，作为后续推荐的隐式反馈。 */
    @Transactional(rollbackFor = Exception.class)
    public void recordView(Long userId, Long movieId) {
        requireMovie(movieId);
        UserMovieBehavior behavior = findBehavior(userId, movieId, VIEW);
        if (behavior == null) behaviorMapper.insert(newBehavior(userId, movieId, VIEW));
        else {
            behavior.setBehaviorCount(Math.min(20, value(behavior.getBehaviorCount()) + 1));
            behavior.setActive(1);
            behavior.setLastOccurredAt(LocalDateTime.now());
            behaviorMapper.updateById(behavior);
        }
    }

    /** 切换当前用户对影片的想看状态，并返回最新状态。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long userId, Long movieId) {
        requireMovie(movieId);
        UserMovieBehavior behavior = findBehavior(userId, movieId, FAVORITE);
        boolean active = behavior == null || !Integer.valueOf(1).equals(behavior.getActive());
        setFavorite(userId, movieId, active);
        return active;
    }

    /** 检查当前用户是否已将影片加入想看。 */
    public boolean isFavorite(Long userId, Long movieId) {
        UserMovieBehavior behavior = findBehavior(userId, movieId, FAVORITE);
        return behavior != null && Integer.valueOf(1).equals(behavior.getActive());
    }

    /** 读取当前用户的想看影片及收藏信息。 */
    public List<FavoriteMovieVO> getFavorites(Long userId) {
        List<UserMovieBehavior> favorites = behaviorMapper.selectList(
                new LambdaQueryWrapper<UserMovieBehavior>()
                        .eq(UserMovieBehavior::getUserId, userId)
                        .eq(UserMovieBehavior::getBehaviorType, FAVORITE)
                        .eq(UserMovieBehavior::getActive, 1)
                        .orderByDesc(UserMovieBehavior::getLastOccurredAt));
        if (favorites.isEmpty()) return List.of();

        Map<Long, Movie> movies = movieMapper.selectBatchIds(
                        favorites.stream().map(UserMovieBehavior::getMovieId).distinct().toList())
                .stream().collect(Collectors.toMap(Movie::getId, Function.identity()));
        return favorites.stream().map(behavior -> {
            Movie movie = movies.get(behavior.getMovieId());
            if (movie == null) return null;
            FavoriteMovieVO vo = new FavoriteMovieVO();
            BeanUtils.copyProperties(movie, vo);
            vo.setStatusText(statusText(movie.getStatus()));
            vo.setFavoriteTime(behavior.getLastOccurredAt());
            return vo;
        }).filter(Objects::nonNull).toList();
    }

    /** 移除本人想看记录。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long userId, Long movieId) {
        UserMovieBehavior behavior = findBehavior(userId, movieId, FAVORITE);
        if (behavior == null || !Integer.valueOf(1).equals(behavior.getActive())) return;
        setFavorite(userId, movieId, false);
    }

    /** 按时间划分进行离线推荐评估，比较模型、基线和消融指标。 */
    public RecommendationEvaluationVO evaluate(int k) {
        int size = Math.max(1, Math.min(20, k));
        List<UserMovieBehavior> favorites = behaviorMapper.selectList(new LambdaQueryWrapper<UserMovieBehavior>()
                .eq(UserMovieBehavior::getBehaviorType, FAVORITE).eq(UserMovieBehavior::getActive, 1)
                .orderByAsc(UserMovieBehavior::getLastOccurredAt));
        Map<Long, List<UserMovieBehavior>> byUser = favorites.stream()
                .filter(item -> item.getLastOccurredAt() != null)
                .collect(Collectors.groupingBy(UserMovieBehavior::getUserId));
        Map<String, EvaluationAccumulator> metrics = new LinkedHashMap<>();
        metrics.put("HYBRID", new EvaluationAccumulator());
        metrics.put("POPULAR", new EvaluationAccumulator());
        metrics.put("CONTENT", new EvaluationAccumulator());
        metrics.put("NO_DECAY", new EvaluationAccumulator());
        metrics.put("NO_SCENE", new EvaluationAccumulator());
        metrics.put("NO_DIVERSITY", new EvaluationAccumulator());
        Set<Long> eligibleCatalog = new HashSet<>();
        int skipped = 0;
        for (Map.Entry<Long, List<UserMovieBehavior>> entry : byUser.entrySet()) {
            UserMovieBehavior heldOut = entry.getValue().getLast();
            LocalDateTime cutoff = heldOut.getLastOccurredAt();
            List<Movie> catalog = evaluationCandidates(cutoff);
            Set<Long> catalogIds = catalog.stream().map(Movie::getId).collect(Collectors.toSet());
            if (!catalogIds.contains(heldOut.getMovieId()) || entry.getValue().stream()
                    .filter(item -> item.getLastOccurredAt().isBefore(cutoff)).findAny().isEmpty()) {
                skipped++;
                continue;
            }
            eligibleCatalog.addAll(catalogIds);
            RecommendationOptions full = RecommendationOptions.evaluation(cutoff, Set.of(heldOut.getMovieId()));
            Map<String, List<MovieRecommendationVO>> runs = new LinkedHashMap<>();
            runs.put("HYBRID", recommend(entry.getKey(), size, null, null, catalog, full));
            runs.put("POPULAR", popularBaseline(entry.getKey(), size, catalog, full));
            runs.put("CONTENT", contentBaseline(entry.getKey(), size, catalog, full));
            runs.put("NO_DECAY", recommend(entry.getKey(), size, null, null, catalog, full.withTimeDecay(false)));
            runs.put("NO_SCENE", recommend(entry.getKey(), size, null, null, catalog, full.withScene(false)));
            runs.put("NO_DIVERSITY", recommend(entry.getKey(), size, null, null, catalog, full.withDiversity(false)));
            runs.forEach((key, recommendations) -> metrics.get(key)
                    .add(recommendations, heldOut.getMovieId(), size));
        }
        RecommendationExperimentVO hybrid = experiment("HYBRID", "完整混合模型", "FULL", metrics.get("HYBRID"),
                size, eligibleCatalog.size(), 0, "时间衰减 + 内容偏好 + 行为 + 场景 + 趋势 + 多样性重排");
        double fullRecall = hybrid.getRecallAtK();
        List<RecommendationExperimentVO> baselines = List.of(
                experiment("POPULAR", "热门推荐基线", "BASELINE", metrics.get("POPULAR"), size,
                        eligibleCatalog.size(), fullRecall, "仅使用留出时间点之前七天的销量与影片评分"),
                experiment("CONTENT", "纯内容推荐基线", "BASELINE", metrics.get("CONTENT"), size,
                        eligibleCatalog.size(), fullRecall, "仅使用留出时间点之前的用户行为构建内容画像")
        );
        List<RecommendationExperimentVO> ablations = List.of(
                experiment("NO_DECAY", "去掉时间衰减", "ABLATION", metrics.get("NO_DECAY"), size,
                        eligibleCatalog.size(), fullRecall, "所有历史行为使用相同时间权重"),
                experiment("NO_SCENE", "去掉场景特征", "ABLATION", metrics.get("NO_SCENE"), size,
                        eligibleCatalog.size(), fullRecall, "不使用未来七天场次、价格与距离特征"),
                experiment("NO_DIVERSITY", "去掉多样性重排", "ABLATION", metrics.get("NO_DIVERSITY"), size,
                        eligibleCatalog.size(), fullRecall, "直接按相关性得分排序，不限制类型集中度")
        );
        return RecommendationEvaluationVO.builder().k(size).evaluatedUsers(hybrid.getEvaluatedUsers())
                .precisionAtK(hybrid.getPrecisionAtK()).recallAtK(hybrid.getRecallAtK())
                .ndcgAtK(hybrid.getNdcgAtK()).catalogCoverage(hybrid.getCatalogCoverage())
                .diversity(hybrid.getDiversity()).skippedUsers(skipped)
                .baselines(baselines).ablations(ablations)
                .protocol("严格时间 leave-one-out：每位用户留出最后一次想看，在该时间点截断浏览、想看、购买、评分和趋势；评估时禁用无历史快照的静态类型偏好")
                .candidatePolicy("仅使用留出时间点后 7 天内存在可售场次的影片，避免向历史用户推荐当时不可购买的影片")
                .generatedAt(LocalDateTime.now()).build();
    }

    private List<Movie> evaluationCandidates(LocalDateTime cutoff) {
        List<Showtime> available = showtimeMapper.selectList(new LambdaQueryWrapper<Showtime>()
                .ne(Showtime::getStatus, 2)
                .le(Showtime::getCreateTime, cutoff)
                .ge(Showtime::getStartTime, cutoff)
                .lt(Showtime::getStartTime, cutoff.plusDays(7)));
        List<Long> movieIds = available.stream().map(Showtime::getMovieId).filter(Objects::nonNull)
                .distinct().toList();
        return movieIds.isEmpty() ? List.of() : movieMapper.selectBatchIds(movieIds);
    }

    private List<MovieRecommendationVO> popularBaseline(Long userId, int limit, List<Movie> catalog,
                                                         RecommendationOptions options) {
        Map<Long, Movie> movieMap = catalog.stream().collect(Collectors.toMap(Movie::getId, Function.identity()));
        UserSignals signals = signals(userId, movieMap, options);
        TrendContext trend = trendContext(catalog, options.referenceTime(), true);
        return catalog.stream().filter(movie -> !signals.purchased().contains(movie.getId()))
                .sorted(Comparator.comparingDouble((Movie movie) -> trendScore(movie, trend)).reversed())
                .limit(limit).map(movie -> new ScoredMovie(movie, trendScore(movie, trend), 0, 0, 0, 0,
                        trendScore(movie, trend), "基线模型：该影片在留出时间点之前的口碑与销量较高"))
                .map(this::toRecommendationVO).toList();
    }

    private List<MovieRecommendationVO> contentBaseline(Long userId, int limit, List<Movie> catalog,
                                                         RecommendationOptions options) {
        Map<Long, Movie> movieMap = catalog.stream().collect(Collectors.toMap(Movie::getId, Function.identity()));
        UserSignals signals = signals(userId, movieMap, options.withScene(false).withTrend(false).withDiversity(false));
        return catalog.stream().filter(movie -> !signals.purchased().contains(movie.getId()))
                .map(movie -> new ScoredMovie(movie, contentScore(movie, signals.profile()),
                        contentScore(movie, signals.profile()), 0,
                        behaviorScore(movie, signals.interactions(), movieMap), 0, 0,
                        "基线模型：影片内容与你在留出时间点之前的观影画像相似"))
                .sorted(Comparator.comparingDouble(ScoredMovie::total).reversed()).limit(limit)
                .map(this::toRecommendationVO).toList();
    }

    private RecommendationExperimentVO experiment(String key, String name, String category,
                                                    EvaluationAccumulator accumulator, int k, int catalogSize,
                                                    double fullRecall, String description) {
        double precision = accumulator.users == 0 ? 0 : accumulator.hits / (double) (accumulator.users * k);
        double recall = accumulator.users == 0 ? 0 : accumulator.hits / (double) accumulator.users;
        return RecommendationExperimentVO.builder().key(key).name(name).category(category)
                .evaluatedUsers(accumulator.users).precisionAtK(round4(precision)).recallAtK(round4(recall))
                .ndcgAtK(round4(accumulator.users == 0 ? 0 : accumulator.ndcg / accumulator.users))
                .catalogCoverage(round4(catalogSize == 0 ? 0 : accumulator.covered.size() / (double) catalogSize))
                .diversity(round4(accumulator.users == 0 ? 0 : accumulator.diversity / accumulator.users))
                .recallChangeVsFull("FULL".equals(category) ? 0 : round4(recall - fullRecall))
                .description(description).build();
    }

    private double weightedTotal(double content, double preference, double behavior, double scene, double trend,
                                 RecommendationOptions options) {
        double contentWeight = .30;
        double preferenceWeight = .15;
        double behaviorWeight = .20;
        double sceneWeight = options.useScene() ? .20 : 0;
        double trendWeight = options.useTrend() ? .15 : 0;
        double sum = contentWeight + preferenceWeight + behaviorWeight + sceneWeight + trendWeight;
        return (content * contentWeight + preference * preferenceWeight + behavior * behaviorWeight
                + scene * sceneWeight + trend * trendWeight) / sum;
    }

    private int averageScore(List<MovieRecommendationVO> recommendations,
                             Function<RecommendationScoreVO, Integer> getter) {
        return (int) Math.round(recommendations.stream().map(MovieRecommendationVO::getRecommendationScores)
                .filter(Objects::nonNull).map(getter).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average().orElse(0));
    }

    private boolean availableAt(LocalDateTime eventTime, LocalDateTime cutoff) {
        return cutoff == null || (eventTime != null && !eventTime.isAfter(cutoff));
    }

    private UserSignals signals(Long userId, Map<Long, Movie> movieMap, RecommendationOptions options) {
        User user = userMapper.selectById(userId);
        List<String> explicit = !options.useExplicitPreferences() || user == null || user.getFavoriteGenres() == null
                ? List.of() : user.getFavoriteGenres();
        Map<Long, Double> interactions = new HashMap<>();
        for (UserMovieBehavior behavior : behaviors(userId)) {
            if (options.heldOut().contains(behavior.getMovieId())
                    || !availableAt(behavior.getLastOccurredAt(), options.cutoff())) continue;
            double weight = VIEW.equals(behavior.getBehaviorType())
                    ? Math.min(3, value(behavior.getBehaviorCount())) * VIEW_WEIGHT : FAVORITE_WEIGHT;
            interactions.merge(behavior.getMovieId(), weight * decay(behavior.getLastOccurredAt(),
                    options.referenceTime(), options.useTimeDecay()), Double::sum);
        }
        List<TicketOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getStatus, 1));
        Map<Long, Showtime> showtimes = loadShowtimes(orders.stream().map(TicketOrder::getShowtimeId).toList());
        Set<Long> purchased = new HashSet<>();
        for (TicketOrder order : orders) {
            Showtime showtime = showtimes.get(order.getShowtimeId());
            if (showtime == null || options.heldOut().contains(showtime.getMovieId())
                    || !availableAt(order.getCreateTime(), options.cutoff())) continue;
            purchased.add(showtime.getMovieId());
            interactions.merge(showtime.getMovieId(), PURCHASE_WEIGHT * decay(order.getCreateTime(),
                    options.referenceTime(), options.useTimeDecay()), Double::sum);
        }
        Set<String> fiveStarDirectors = new HashSet<>();
        for (Comment comment : commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getUserId, userId))) {
            if (options.heldOut().contains(comment.getMovieId()) || comment.getRating() == null || comment.getRating() < 5
                    || !availableAt(comment.getCreateTime(), options.cutoff())) continue;
            interactions.merge(comment.getMovieId(), FIVE_STAR_WEIGHT * decay(comment.getCreateTime(),
                    options.referenceTime(), options.useTimeDecay()), Double::sum);
            Movie rated = movieMap.get(comment.getMovieId());
            if (rated == null) rated = movieMapper.selectById(comment.getMovieId());
            if (rated != null) fiveStarDirectors.addAll(tokens(rated.getDirector()));
        }
        return new UserSignals(interactions, purchased, new HashSet<>(explicit), fiveStarDirectors,
                profile(interactions, movieMap, explicit));
    }

    private FeatureProfile profile(Map<Long, Double> interactions, Map<Long, Movie> movieMap, List<String> explicit) {
        Map<String, Double> genres = new HashMap<>(), directors = new HashMap<>(), actors = new HashMap<>(), languages = new HashMap<>();
        double durationSum = 0, durationWeight = 0;
        for (Map.Entry<Long, Double> entry : interactions.entrySet()) {
            Movie movie = movieMap.get(entry.getKey());
            if (movie == null) movie = movieMapper.selectById(entry.getKey());
            if (movie == null) continue;
            add(genres, movie.getGenre(), entry.getValue());
            add(directors, movie.getDirector(), entry.getValue());
            add(actors, movie.getActors(), entry.getValue());
            add(languages, movie.getLanguage(), entry.getValue());
            if (movie.getDuration() != null) { durationSum += movie.getDuration() * entry.getValue(); durationWeight += entry.getValue(); }
        }
        explicit.forEach(genre -> genres.merge(normalize(genre), 4.0, Double::sum));
        return new FeatureProfile(genres, directors, actors, languages, durationWeight == 0 ? null : durationSum / durationWeight);
    }

    private double contentScore(Movie movie, FeatureProfile profile) {
        if (profile.empty()) return 50;
        double duration = profile.duration() == null || movie.getDuration() == null ? 50
                : clamp(100 - Math.abs(movie.getDuration() - profile.duration()) / 1.2);
        return affinity(movie.getGenre(), profile.genres()) * .35 + affinity(movie.getDirector(), profile.directors()) * .20
                + affinity(movie.getActors(), profile.actors()) * .20 + affinity(movie.getLanguage(), profile.languages()) * .15
                + duration * .10;
    }

    private double preferenceScore(Movie movie, Set<String> preferences) {
        if (preferences.isEmpty()) return 50;
        Set<String> genres = tokens(movie.getGenre());
        long matches = preferences.stream().map(this::normalize).filter(genres::contains).count();
        return clamp(20 + 80.0 * matches / preferences.size());
    }

    private double behaviorScore(Movie movie, Map<Long, Double> interactions, Map<Long, Movie> movieMap) {
        if (interactions.isEmpty()) return 50;
        double sum = 0, weights = 0;
        for (Map.Entry<Long, Double> entry : interactions.entrySet()) {
            Movie source = movieMap.get(entry.getKey());
            if (source == null) source = movieMapper.selectById(entry.getKey());
            if (source == null || source.getId().equals(movie.getId())) continue;
            sum += similarity(source, movie) * entry.getValue(); weights += entry.getValue();
        }
        return weights == 0 ? 50 : sum / weights;
    }

    private SceneContext sceneContext(List<Movie> movies, Double lat, Double lng, LocalDateTime referenceTime,
                                      boolean historicalSnapshot) {
        LocalDateTime now = referenceTime == null ? LocalDateTime.now() : referenceTime;
        List<Showtime> showtimes = showtimeMapper.selectList(new LambdaQueryWrapper<Showtime>()
                .in(Showtime::getMovieId, movies.stream().map(Movie::getId).toList()).ne(Showtime::getStatus, 2)
                .le(historicalSnapshot, Showtime::getCreateTime, now)
                .ge(Showtime::getStartTime, now).le(Showtime::getStartTime, now.plusDays(7)));
        Set<Long> cinemaIds = showtimes.stream().map(Showtime::getCinemaId).collect(Collectors.toSet());
        Map<Long, Cinema> cinemas = cinemaIds.isEmpty() ? Map.of() : cinemaMapper.selectBatchIds(cinemaIds).stream()
                .collect(Collectors.toMap(Cinema::getId, Function.identity()));
        return new SceneContext(showtimes.stream().collect(Collectors.groupingBy(Showtime::getMovieId)), cinemas, lat, lng, now);
    }

    private double sceneScore(Movie movie, SceneContext context) {
        List<Showtime> showtimes = context.showtimes().getOrDefault(movie.getId(), List.of());
        if (showtimes.isEmpty()) return 0;
        double soon = showtimes.stream().anyMatch(s -> s.getStartTime().isBefore(context.now().plusDays(2))) ? 20 : 8;
        double minPrice = showtimes.stream().map(Showtime::getBasePrice).filter(Objects::nonNull)
                .min(BigDecimal::compareTo).orElse(BigDecimal.valueOf(100)).doubleValue();
        return clamp(40 + soon + clamp(100 - Math.max(0, minPrice - 35) * 1.5) * .20 + nearestCinema(showtimes, context) * .20);
    }

    private double nearestCinema(List<Showtime> showtimes, SceneContext context) {
        if (context.lat() == null || context.lng() == null) return 50;
        double nearest = Double.MAX_VALUE;
        for (Showtime showtime : showtimes) {
            Cinema cinema = context.cinemas().get(showtime.getCinemaId());
            if (cinema != null && cinema.getLat() != null && cinema.getLng() != null)
                nearest = Math.min(nearest, haversine(context.lat(), context.lng(), cinema.getLat().doubleValue(), cinema.getLng().doubleValue()));
        }
        return nearest == Double.MAX_VALUE ? 50 : clamp(100 - nearest * 12);
    }

    private TrendContext trendContext(List<Movie> movies, LocalDateTime referenceTime) {
        return trendContext(movies, referenceTime, false);
    }

    private TrendContext trendContext(List<Movie> movies, LocalDateTime referenceTime, boolean historicalRating) {
        if (movies.isEmpty()) return new TrendContext(Map.of(), 1, Map.of());
        LocalDateTime end = referenceTime == null ? LocalDateTime.now() : referenceTime;
        List<Showtime> showtimes = showtimeMapper.selectList(new LambdaQueryWrapper<Showtime>()
                .in(Showtime::getMovieId, movies.stream().map(Movie::getId).toList()));
        Map<Long, Long> movieByShowtime = showtimes.stream().collect(Collectors.toMap(Showtime::getId, Showtime::getMovieId, (a, b) -> a));
        List<TicketOrder> recent = movieByShowtime.isEmpty() ? List.of() : orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, 1).in(TicketOrder::getShowtimeId, movieByShowtime.keySet())
                .ge(TicketOrder::getCreateTime, end.minusDays(7)).le(TicketOrder::getCreateTime, end));
        Map<Long, Long> sales = recent.stream().filter(order -> availableAt(order.getCreateTime(), end))
                .map(o -> Map.entry(movieByShowtime.get(o.getShowtimeId()), Math.max(1, value(o.getTicketCount()))))
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingLong(Map.Entry::getValue)));
        Map<Long, Double> ratings;
        if (historicalRating) {
            List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .in(Comment::getMovieId, movies.stream().map(Movie::getId).toList())
                    .le(Comment::getCreateTime, end));
            ratings = comments.stream().filter(comment -> comment.getRating() != null)
                    .collect(Collectors.groupingBy(Comment::getMovieId,
                            Collectors.averagingDouble(comment -> comment.getRating() * 20.0)));
        } else {
            ratings = movies.stream().collect(Collectors.toMap(Movie::getId,
                    movie -> movie.getRating() == null ? 50.0 : movie.getRating().doubleValue() * 10));
        }
        return new TrendContext(sales, sales.values().stream().mapToLong(Long::longValue).max().orElse(1), ratings);
    }

    private double trendScore(Movie movie, TrendContext context) {
        double rating = context.ratings().getOrDefault(movie.getId(), 50.0);
        return clamp(rating * .70 + 100.0 * context.sales().getOrDefault(movie.getId(), 0L) / context.maxSales() * .30);
    }

    private String reason(Movie movie, UserSignals signals, Map<Long, Movie> movieMap, SceneContext scene) {
        List<String> reasons = new ArrayList<>();
        Set<String> genres = tokens(movie.getGenre());
        signals.explicitGenres().stream().map(this::normalize).filter(genres::contains).findFirst()
                .ifPresent(genre -> reasons.add("你喜欢" + genre + "片"));
        tokens(movie.getDirector()).stream().filter(signals.fiveStarDirectors()::contains).findFirst()
                .ifPresent(director -> reasons.add("你曾给" + director + "作品五星评价"));
        if (reasons.size() < 2) signals.interactions().entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()).map(e -> movieMap.get(e.getKey()))
                .filter(Objects::nonNull).filter(source -> !source.getId().equals(movie.getId()) && similarity(source, movie) >= 55)
                .findFirst().ifPresent(source -> reasons.add("它与你喜欢的《" + source.getTitle() + "》风格相近"));
        if (reasons.size() < 2 && !scene.showtimes().getOrDefault(movie.getId(), List.of()).isEmpty()) reasons.add("未来七天有合适场次");
        if (reasons.isEmpty()) reasons.add("近期口碑与购票热度较高");
        return "因为" + String.join("，并且", reasons.stream().limit(2).toList());
    }

    private List<ScoredMovie> diversify(List<ScoredMovie> source, int limit) {
        List<ScoredMovie> remaining = source.stream().sorted(Comparator.comparingDouble(ScoredMovie::total).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
        List<ScoredMovie> selected = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        int cap = Math.max(2, (int) Math.ceil(limit * .5));
        while (!remaining.isEmpty() && selected.size() < limit) {
            ScoredMovie best = remaining.stream().filter(item -> counts.getOrDefault(primaryGenre(item.movie()), 0) < cap)
                    .max(Comparator.comparingDouble(item -> item.total() - counts.getOrDefault(primaryGenre(item.movie()), 0) * 8
                            - selected.stream().mapToDouble(s -> similarity(item.movie(), s.movie()) * .05).max().orElse(0)))
                    .orElse(remaining.getFirst());
            remaining.remove(best); selected.add(best); counts.merge(primaryGenre(best.movie()), 1, Integer::sum);
        }
        return selected;
    }

    private double similarity(Movie a, Movie b) {
        double duration = a.getDuration() == null || b.getDuration() == null ? .5
                : clamp(100 - Math.abs(a.getDuration() - b.getDuration()) / 1.2) / 100;
        return 100 * (jaccard(tokens(a.getGenre()), tokens(b.getGenre())) * .35
                + jaccard(tokens(a.getDirector()), tokens(b.getDirector())) * .20
                + jaccard(tokens(a.getActors()), tokens(b.getActors())) * .20
                + jaccard(tokens(a.getLanguage()), tokens(b.getLanguage())) * .15 + duration * .10);
    }

    private double diversity(List<MovieRecommendationVO> movies) {
        if (movies.size() < 2) return 0;
        double sum = 0; int pairs = 0;
        for (int i = 0; i < movies.size(); i++) for (int j = i + 1; j < movies.size(); j++) {
            sum += 1 - jaccard(tokens(movies.get(i).getGenre()), tokens(movies.get(j).getGenre())); pairs++;
        }
        return sum / pairs;
    }

    private MovieRecommendationVO toRecommendationVO(ScoredMovie scored) {
        MovieRecommendationVO vo = new MovieRecommendationVO();
        BeanUtils.copyProperties(scored.movie(), vo);
        vo.setStatusText(statusText(scored.movie().getStatus()));
        vo.setMatchScore((int) Math.round(scored.total()));
        vo.setRecommendationReason(scored.reason());
        vo.setRecommendationScores(new RecommendationScoreVO(round(scored.content()), round(scored.preference()),
                round(scored.behavior()), round(scored.scene()), round(scored.trend())));
        return vo;
    }

    private List<UserMovieBehavior> behaviors(Long userId) {
        return behaviorMapper.selectList(new LambdaQueryWrapper<UserMovieBehavior>()
                .eq(UserMovieBehavior::getUserId, userId).eq(UserMovieBehavior::getActive, 1));
    }

    private UserMovieBehavior findBehavior(Long userId, Long movieId, String type) {
        return behaviorMapper.selectOne(new LambdaQueryWrapper<UserMovieBehavior>()
                .eq(UserMovieBehavior::getUserId, userId).eq(UserMovieBehavior::getMovieId, movieId)
                .eq(UserMovieBehavior::getBehaviorType, type));
    }

    private void setFavorite(Long userId, Long movieId, boolean active) {
        UserMovieBehavior behavior = findBehavior(userId, movieId, FAVORITE);
        if (behavior == null) { behavior = newBehavior(userId, movieId, FAVORITE); behavior.setActive(active ? 1 : 0); behaviorMapper.insert(behavior); }
        else { behavior.setActive(active ? 1 : 0); behavior.setLastOccurredAt(LocalDateTime.now()); behaviorMapper.updateById(behavior); }
    }

    private UserMovieBehavior newBehavior(Long userId, Long movieId, String type) {
        UserMovieBehavior b = new UserMovieBehavior();
        b.setUserId(userId); b.setMovieId(movieId); b.setBehaviorType(type); b.setBehaviorCount(1);
        b.setActive(1); b.setLastOccurredAt(LocalDateTime.now()); return b;
    }

    private Map<Long, Showtime> loadShowtimes(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return showtimeMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Showtime::getId, Function.identity()));
    }

    private List<Movie> candidates() {
        return movieMapper.selectList(new LambdaQueryWrapper<Movie>().eq(Movie::getStatus, 1));
    }

    private Movie requireMovie(Long id) {
        Movie movie = movieMapper.selectById(id); if (movie == null) throw new BusinessException("电影不存在"); return movie;
    }

    private MovieVO toMovieVO(Movie movie) {
        MovieVO vo = new MovieVO(); BeanUtils.copyProperties(movie, vo); vo.setStatusText(statusText(movie.getStatus())); return vo;
    }

    private void add(Map<String, Double> map, String raw, double weight) { tokens(raw).forEach(t -> map.merge(t, weight, Double::sum)); }
    private double affinity(String raw, Map<String, Double> profile) {
        if (profile.isEmpty()) return 0; double max = profile.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        return tokens(raw).stream().mapToDouble(t -> profile.getOrDefault(t, 0.0) / max * 100).max().orElse(0);
    }
    private Set<String> tokens(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split("[,/、，|]")).map(this::normalize).filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return .5; Set<String> union = new HashSet<>(a); union.addAll(b);
        Set<String> intersection = new HashSet<>(a); intersection.retainAll(b); return union.isEmpty() ? 0 : intersection.size() / (double) union.size();
    }
    private String primaryGenre(Movie movie) { return tokens(movie.getGenre()).stream().sorted().findFirst().orElse("其他"); }
    private double decay(LocalDateTime time, LocalDateTime referenceTime, boolean enabled) {
        if (!enabled || time == null) return 1;
        LocalDateTime reference = referenceTime == null ? LocalDateTime.now() : referenceTime;
        return Math.max(.35, Math.pow(.97, Math.max(0, Duration.between(time, reference).toDays())));
    }
    private double haversine(double a, double b, double c, double d) {
        double x = Math.toRadians(c - a), y = Math.toRadians(d - b);
        double v = Math.sin(x / 2) * Math.sin(x / 2) + Math.cos(Math.toRadians(a)) * Math.cos(Math.toRadians(c)) * Math.sin(y / 2) * Math.sin(y / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(v), Math.sqrt(1 - v));
    }
    private int value(Integer value) { return value == null ? 1 : value; }
    private int safeLimit(Integer value, int fallback) { return value == null ? fallback : Math.max(1, Math.min(50, value)); }
    private int round(double value) { return (int) Math.round(clamp(value)); }
    private double clamp(double value) { return Math.max(0, Math.min(100, value)); }
    private double round4(double value) { return Math.round(value * 10_000.0) / 10_000.0; }
    private String statusText(Integer value) { return Map.of(0, "未上映", 1, "正在上映", 2, "即将上映", 3, "已下架").getOrDefault(value, "未知"); }

    private final class EvaluationAccumulator {
        private int users;
        private int hits;
        private double ndcg;
        private double diversity;
        private final Set<Long> covered = new HashSet<>();

        private void add(List<MovieRecommendationVO> recommendations, Long relevantMovieId, int k) {
            users++;
            if (recommendations.isEmpty()) return;
            covered.addAll(recommendations.stream().map(MovieVO::getId).toList());
            diversity += RecommendService.this.diversity(recommendations);
            for (int index = 0; index < Math.min(k, recommendations.size()); index++) {
                if (Objects.equals(recommendations.get(index).getId(), relevantMovieId)) {
                    hits++;
                    ndcg += 1.0 / (Math.log(index + 2) / Math.log(2));
                    break;
                }
            }
        }
    }

    private record RecommendationOptions(boolean useTimeDecay, boolean useScene, boolean useTrend,
                                         boolean useDiversity, boolean useExplicitPreferences,
                                         LocalDateTime cutoff, Set<Long> heldOut) {
        private static RecommendationOptions production() {
            return new RecommendationOptions(true, true, true, true, true, null, Set.of());
        }

        private static RecommendationOptions evaluation(LocalDateTime cutoff, Set<Long> heldOut) {
            return new RecommendationOptions(true, true, true, true, false, cutoff, heldOut);
        }

        private LocalDateTime referenceTime() {
            return cutoff == null ? LocalDateTime.now() : cutoff;
        }

        private RecommendationOptions withTimeDecay(boolean value) {
            return new RecommendationOptions(value, useScene, useTrend, useDiversity,
                    useExplicitPreferences, cutoff, heldOut);
        }

        private RecommendationOptions withScene(boolean value) {
            return new RecommendationOptions(useTimeDecay, value, useTrend, useDiversity,
                    useExplicitPreferences, cutoff, heldOut);
        }

        private RecommendationOptions withTrend(boolean value) {
            return new RecommendationOptions(useTimeDecay, useScene, value, useDiversity,
                    useExplicitPreferences, cutoff, heldOut);
        }

        private RecommendationOptions withDiversity(boolean value) {
            return new RecommendationOptions(useTimeDecay, useScene, useTrend, value,
                    useExplicitPreferences, cutoff, heldOut);
        }
    }

    private record FeatureProfile(Map<String, Double> genres, Map<String, Double> directors,
                                  Map<String, Double> actors, Map<String, Double> languages, Double duration) {
        boolean empty() { return genres.isEmpty() && directors.isEmpty() && actors.isEmpty() && languages.isEmpty(); }
    }
    private record UserSignals(Map<Long, Double> interactions, Set<Long> purchased, Set<String> explicitGenres,
                               Set<String> fiveStarDirectors, FeatureProfile profile) {}
    private record SceneContext(Map<Long, List<Showtime>> showtimes, Map<Long, Cinema> cinemas,
                                Double lat, Double lng, LocalDateTime now) {}
    private record TrendContext(Map<Long, Long> sales, long maxSales, Map<Long, Double> ratings) {}
    private record ScoredMovie(Movie movie, double total, double content, double preference,
                               double behavior, double scene, double trend, String reason) {}
}
