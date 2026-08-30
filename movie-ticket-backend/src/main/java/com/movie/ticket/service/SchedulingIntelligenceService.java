package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.dto.SchedulingPredictionRequest;
import com.movie.ticket.dto.SchedulingSimulationRequest;
import com.movie.ticket.entity.Hall;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于历史售票数据的可解释排片决策模型。
 *
 * <p>它刻意保持为轻量加权模型：无需训练任务或额外模型表，新订单会自然参与下一次计算。</p>
 */
@Service
@RequiredArgsConstructor
public class SchedulingIntelligenceService {

    private static final double DEFAULT_OCCUPANCY = 0.45;
    private static final double LOW_OCCUPANCY_LINE = 0.35;
    private static final int FUTURE_DAYS = 14;

    private final MovieMapper movieMapper;
    private final ShowtimeMapper showtimeMapper;
    private final TicketOrderMapper orderMapper;
    private final HallMapper hallMapper;

    public Map<String, Object> getInsights(Long cinemaId) {
        ModelContext context = loadContext(cinemaId);
        LocalDateTime now = LocalDateTime.now();

        List<Map<String, Object>> allLowOccupancy = context.showtimes().stream()
                .filter(s -> Objects.equals(s.getStatus(), 0))
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(now))
                .filter(s -> s.getStartTime().isBefore(now.plusDays(FUTURE_DAYS)))
                .map(s -> lowOccupancyItem(s, context))
                .filter(item -> ((Number) item.get("predictedRate")).doubleValue() < LOW_OCCUPANCY_LINE * 100)
                .sorted(Comparator.comparingDouble(item -> ((Number) item.get("predictedRate")).doubleValue()))
                .toList();
        List<Map<String, Object>> lowOccupancy = allLowOccupancy.stream().limit(12).toList();

        List<Map<String, Object>> goldenRecommendations = buildGoldenRecommendations(context, cinemaId);
        List<Map<String, Object>> frequencyRecommendations = buildFrequencyRecommendations(context);
        List<Map<String, Object>> heatmap = buildHeatmap(context);
        Map<String, Object> evaluation = buildEvaluation(context);

        double futureAverage = context.showtimes().stream()
                .filter(s -> Objects.equals(s.getStatus(), 0) && s.getStartTime() != null && s.getStartTime().isAfter(now))
                .limit(200)
                .mapToDouble(s -> predict(s.getMovieId(), s.getHallId(), s.getStartTime(), context).rate())
                .average().orElse(context.globalOccupancy());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("predictedAverageRate", percent(futureAverage));
        summary.put("lowOccupancyCount", allLowOccupancy.size());
        summary.put("actionableSuggestionCount", frequencyRecommendations.stream()
                .filter(item -> !"KEEP".equals(item.get("action"))).count());
        summary.put("historicalSamples", context.historical().size());
        summary.put("modelConfidence", confidence(context.historical().size()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("lowOccupancyShowtimes", lowOccupancy);
        result.put("goldenTimeRecommendations", goldenRecommendations);
        result.put("frequencyRecommendations", frequencyRecommendations);
        result.put("heatmap", heatmap);
        result.put("halls", context.halls().values().stream()
                .sorted(Comparator.comparing(Hall::getName, Comparator.nullsLast(String::compareTo)))
                .map(h -> Map.<String, Object>of("id", h.getId(), "name", h.getName()))
                .toList());
        result.put("evaluation", evaluation);
        result.put("model", modelDescription());
        result.put("generatedAt", now);
        return result;
    }

    public Map<String, Object> predict(SchedulingPredictionRequest request) {
        validateTarget(request.getMovieId(), request.getCinemaId(), request.getHallId());
        ModelContext context = loadContext(request.getCinemaId());
        Prediction prediction = predict(request.getMovieId(), request.getHallId(), request.getStartTime(), context);
        return predictionResult(request.getMovieId(), request.getHallId(), request.getStartTime(), prediction, context);
    }

    public Map<String, Object> simulate(SchedulingSimulationRequest request) {
        Showtime original = showtimeMapper.selectById(request.getOriginalShowtimeId());
        if (original == null) {
            throw new BusinessException("原场次不存在");
        }
        Long targetHallId = request.getTargetHallId() == null ? original.getHallId() : request.getTargetHallId();
        LocalDateTime targetStart = request.getTargetStartTime() == null ? original.getStartTime() : request.getTargetStartTime();
        validateTarget(request.getTargetMovieId(), original.getCinemaId(), targetHallId);

        ModelContext context = loadContext(original.getCinemaId());
        Prediction before = predict(original.getMovieId(), original.getHallId(), original.getStartTime(), context);
        Prediction after = predict(request.getTargetMovieId(), targetHallId, targetStart, context);
        Hall beforeHall = context.halls().get(original.getHallId());
        Hall afterHall = context.halls().get(targetHallId);
        int beforeCapacity = capacity(original, beforeHall);
        int afterCapacity = afterHall == null || afterHall.getTotalSeats() == null ? beforeCapacity : afterHall.getTotalSeats();
        double beforeRevenue = before.rate() * beforeCapacity * money(original.getBasePrice());
        double afterRevenue = after.rate() * afterCapacity * money(original.getBasePrice());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("original", predictionResult(original.getMovieId(), original.getHallId(), original.getStartTime(), before, context));
        result.put("adjusted", predictionResult(request.getTargetMovieId(), targetHallId, targetStart, after, context));
        result.put("occupancyLift", round((after.rate() - before.rate()) * 100));
        result.put("expectedExtraAudience", Math.round(after.rate() * afterCapacity - before.rate() * beforeCapacity));
        result.put("expectedRevenueLift", BigDecimal.valueOf(afterRevenue - beforeRevenue).setScale(2, RoundingMode.HALF_UP));
        result.put("recommendation", after.rate() > before.rate() + 0.02 ? "建议采用调整方案" :
                after.rate() < before.rate() - 0.02 ? "建议保留原排片" : "两种方案效果接近，可结合人工判断");
        result.put("notice", "模拟结果仅用于决策参考，不会修改真实排片");
        return result;
    }

    private Map<String, Object> lowOccupancyItem(Showtime showtime, ModelContext context) {
        Prediction prediction = predict(showtime.getMovieId(), showtime.getHallId(), showtime.getStartTime(), context);
        Movie movie = context.movies().get(showtime.getMovieId());
        Hall hall = context.halls().get(showtime.getHallId());
        Alternative alternative = bestAlternative(showtime, prediction.rate(), context);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("showtimeId", showtime.getId());
        item.put("movieId", showtime.getMovieId());
        item.put("movieTitle", movie == null ? "未知影片" : movie.getTitle());
        item.put("hallId", showtime.getHallId());
        item.put("hallName", hall == null ? "未知影厅" : hall.getName());
        item.put("startTime", showtime.getStartTime());
        item.put("predictedRate", percent(prediction.rate()));
        item.put("confidence", prediction.confidence());
        item.put("reason", prediction.drivers().isEmpty() ? "综合需求低于预警线" : prediction.drivers().get(0).get("description"));
        if (alternative != null) {
            item.put("recommendedMovieId", alternative.movieId());
            item.put("recommendedMovieTitle", alternative.title());
            item.put("recommendedRate", percent(alternative.rate()));
            item.put("potentialLift", round((alternative.rate() - prediction.rate()) * 100));
        }
        return item;
    }

    private List<Map<String, Object>> buildGoldenRecommendations(ModelContext context, Long cinemaId) {
        if (context.movies().isEmpty() || context.halls().isEmpty()) {
            return List.of();
        }
        Hall referenceHall = context.halls().values().stream()
                .max(Comparator.comparingInt(h -> h.getTotalSeats() == null ? 0 : h.getTotalSeats()))
                .orElseThrow();
        LocalDateTime goldenTime = nextGoldenTime();

        return context.movies().values().stream()
                .filter(m -> Objects.equals(m.getStatus(), 1))
                .map(movie -> {
                    Prediction prediction = predict(movie.getId(), referenceHall.getId(), goldenTime, context);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("movieId", movie.getId());
                    item.put("movieTitle", movie.getTitle());
                    item.put("rating", movie.getRating());
                    item.put("recommendedTime", goldenTime);
                    item.put("timeLabel", "周五/周末 19:00-22:00");
                    item.put("hallId", referenceHall.getId());
                    item.put("hallName", referenceHall.getName());
                    item.put("predictedRate", percent(prediction.rate()));
                    item.put("confidence", prediction.confidence());
                    item.put("reason", prediction.drivers().isEmpty() ? "综合表现稳定" : prediction.drivers().get(0).get("description"));
                    return item;
                })
                .sorted((a, b) -> Double.compare(number(b.get("predictedRate")), number(a.get("predictedRate"))))
                .limit(6)
                .toList();
    }

    private List<Map<String, Object>> buildFrequencyRecommendations(ModelContext context) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Long> scheduledCounts = context.showtimes().stream()
                .filter(s -> Objects.equals(s.getStatus(), 0) && s.getStartTime() != null)
                .filter(s -> s.getStartTime().isAfter(now) && s.getStartTime().isBefore(now.plusDays(7)))
                .collect(Collectors.groupingBy(Showtime::getMovieId, Collectors.counting()));

        return context.movies().values().stream()
                .filter(m -> Objects.equals(m.getStatus(), 1))
                .map(movie -> {
                    Aggregate demand = context.movieStats().get(movie.getId());
                    double expected = smoothed(demand, context.globalOccupancy(), 3);
                    long current = scheduledCounts.getOrDefault(movie.getId(), 0L);
                    String action = expected >= 0.68 ? "ADD" : expected <= 0.34 && current > 0 ? "REDUCE" : "KEEP";
                    int change = "ADD".equals(action) ? Math.max(1, (int) Math.ceil((expected - 0.60) * 10)) :
                            "REDUCE".equals(action) ? -Math.max(1, (int) Math.ceil((0.42 - expected) * 8)) : 0;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("movieId", movie.getId());
                    item.put("movieTitle", movie.getTitle());
                    item.put("historicalRate", percent(expected));
                    item.put("currentWeeklyShowtimes", current);
                    item.put("action", action);
                    item.put("suggestedChange", change);
                    item.put("reason", frequencyReason(action, expected, demand == null ? 0 : demand.count()));
                    return item;
                })
                .sorted((a, b) -> {
                    int actionCompare = actionPriority(String.valueOf(a.get("action"))) - actionPriority(String.valueOf(b.get("action")));
                    return actionCompare != 0 ? actionCompare : Double.compare(number(b.get("historicalRate")), number(a.get("historicalRate")));
                })
                .limit(12)
                .toList();
    }

    private List<Map<String, Object>> buildHeatmap(ModelContext context) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Hall hall : context.halls().values()) {
            for (int day = 1; day <= 7; day++) {
                for (int slot = 0; slot < 4; slot++) {
                    Aggregate aggregate = context.heatStats().get(heatKey(hall.getId(), day, slot));
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("hallId", hall.getId());
                    point.put("hallName", hall.getName());
                    point.put("weekday", day);
                    point.put("weekdayLabel", weekdayLabel(day));
                    point.put("timeSlot", slot);
                    point.put("timeSlotLabel", slotLabel(slot));
                    point.put("occupancyRate", aggregate == null || aggregate.count() == 0 ? null : percent(aggregate.average()));
                    point.put("sampleCount", aggregate == null ? 0 : aggregate.count());
                    result.add(point);
                }
            }
        }
        return result;
    }

    private Map<String, Object> buildEvaluation(ModelContext context) {
        List<Showtime> samples = context.historical().stream()
                .sorted(Comparator.comparing(Showtime::getStartTime).reversed())
                .limit(30)
                .toList();
        double mae = samples.stream().mapToDouble(s -> Math.abs(
                actualRate(s, context.soldSeats()) - predict(s.getMovieId(), s.getHallId(), s.getStartTime(), context).rate()))
                .average().orElse(0);
        List<Map<String, Object>> comparisons = samples.stream().limit(8).map(s -> {
            Movie movie = context.movies().get(s.getMovieId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("showtimeId", s.getId());
            item.put("movieTitle", movie == null ? "未知影片" : movie.getTitle());
            item.put("startTime", s.getStartTime());
            item.put("predictedRate", percent(predict(s.getMovieId(), s.getHallId(), s.getStartTime(), context).rate()));
            item.put("actualRate", percent(actualRate(s, context.soldSeats())));
            return item;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meanAbsoluteError", percent(mae));
        result.put("accuracy", round(Math.max(0, 100 - mae * 100)));
        result.put("sampleCount", samples.size());
        result.put("comparisons", comparisons);
        return result;
    }

    private Prediction predict(Long movieId, Long hallId, LocalDateTime startTime, ModelContext context) {
        double baseline = context.globalOccupancy();
        Aggregate movieAggregate = context.movieStats().get(movieId);
        double movieHistory = smoothed(movieAggregate, baseline, 3);
        long movieTickets = context.movieTickets().getOrDefault(movieId, 0L);
        double popularity = context.maxMovieTickets() == 0 ? baseline : Math.sqrt((double) movieTickets / context.maxMovieTickets());
        double movieDemand = movieHistory * 0.75 + popularity * 0.25;
        double weekday = smoothed(context.weekdayStats().get(startTime.getDayOfWeek().getValue()), baseline, 5);
        double slot = smoothed(context.slotStats().get(slot(startTime.toLocalTime())), baseline, 5);
        Movie movie = context.movies().get(movieId);
        double rating = movie == null || movie.getRating() == null ? 0.65 : clamp(movie.getRating().doubleValue() / 10, 0.30, 0.98);
        double freshness = freshness(movie, startTime.toLocalDate());
        Aggregate hallAggregate = context.hallStats().get(hallId);
        Hall hall = context.halls().get(hallId);
        double hallHistory = smoothed(hallAggregate, baseline, 4);
        double capacityFit = hall == null || hall.getTotalSeats() == null ? hallHistory :
                hallHistory * 0.75 + clamp(1 - Math.max(0, hall.getTotalSeats() - 120) / 400.0, 0.35, 1) * 0.25;

        double rate = movieDemand * 0.35 + weekday * 0.18 + slot * 0.20 + rating * 0.10
                + freshness * 0.09 + capacityFit * 0.08;
        rate = clamp(rate, 0.08, 0.95);

        List<Map<String, Object>> drivers = new ArrayList<>();
        addDriver(drivers, "影片热度", movieDemand, baseline, 0.35);
        addDriver(drivers, "星期客流", weekday, baseline, 0.18);
        addDriver(drivers, "时段客流", slot, baseline, 0.20);
        addDriver(drivers, "影片评分", rating, baseline, 0.10);
        addDriver(drivers, "上映新鲜度", freshness, baseline, 0.09);
        addDriver(drivers, "影厅匹配", capacityFit, baseline, 0.08);
        drivers.sort((a, b) -> Double.compare(Math.abs(number(b.get("impact"))), Math.abs(number(a.get("impact")))));

        int evidence = (movieAggregate == null ? 0 : movieAggregate.count())
                + (context.weekdayStats().get(startTime.getDayOfWeek().getValue()) == null ? 0 : context.weekdayStats().get(startTime.getDayOfWeek().getValue()).count());
        return new Prediction(rate, confidence(Math.min(context.historical().size(), evidence)), drivers.stream().limit(3).toList());
    }

    private Map<String, Object> predictionResult(Long movieId, Long hallId, LocalDateTime startTime,
                                                  Prediction prediction, ModelContext context) {
        Movie movie = context.movies().get(movieId);
        Hall hall = context.halls().get(hallId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("movieId", movieId);
        result.put("movieTitle", movie == null ? "未知影片" : movie.getTitle());
        result.put("hallId", hallId);
        result.put("hallName", hall == null ? "未知影厅" : hall.getName());
        result.put("startTime", startTime);
        result.put("predictedRate", percent(prediction.rate()));
        result.put("confidence", prediction.confidence());
        result.put("expectedAudience", Math.round(prediction.rate() * (hall == null || hall.getTotalSeats() == null ? 0 : hall.getTotalSeats())));
        result.put("drivers", prediction.drivers());
        return result;
    }

    private Alternative bestAlternative(Showtime showtime, double currentRate, ModelContext context) {
        return context.movies().values().stream()
                .filter(m -> Objects.equals(m.getStatus(), 1) && !Objects.equals(m.getId(), showtime.getMovieId()))
                .map(m -> new Alternative(m.getId(), m.getTitle(),
                        predict(m.getId(), showtime.getHallId(), showtime.getStartTime(), context).rate()))
                .filter(a -> a.rate() > currentRate + 0.05)
                .max(Comparator.comparingDouble(Alternative::rate))
                .orElse(null);
    }

    private ModelContext loadContext(Long cinemaId) {
        LambdaQueryWrapper<Showtime> showtimeQuery = new LambdaQueryWrapper<Showtime>()
                .ne(Showtime::getStatus, 2)
                .orderByDesc(Showtime::getStartTime);
        if (cinemaId != null) {
            showtimeQuery.eq(Showtime::getCinemaId, cinemaId);
        }
        List<Showtime> showtimes = showtimeMapper.selectList(showtimeQuery);
        Set<Long> showtimeIds = showtimes.stream().map(Showtime::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<TicketOrder> orders = showtimeIds.isEmpty() ? List.of() : orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, 1).in(TicketOrder::getShowtimeId, showtimeIds));

        Map<Long, Integer> soldSeats = new HashMap<>();
        for (TicketOrder order : orders) {
            soldSeats.merge(order.getShowtimeId(), order.getTicketCount() == null ? 0 : order.getTicketCount(), Integer::sum);
        }
        for (Showtime showtime : showtimes) {
            int storedSold = showtime.getTotalSeats() == null || showtime.getAvailableSeats() == null ? 0 :
                    Math.max(0, showtime.getTotalSeats() - showtime.getAvailableSeats());
            soldSeats.merge(showtime.getId(), storedSold, Math::max);
        }

        List<Movie> activeMovies = movieMapper.selectList(new LambdaQueryWrapper<Movie>().eq(Movie::getStatus, 1));
        Set<Long> movieIds = showtimes.stream().map(Showtime::getMovieId).collect(Collectors.toSet());
        activeMovies.forEach(m -> movieIds.add(m.getId()));
        Map<Long, Movie> movies = activeMovies.stream().collect(Collectors.toMap(Movie::getId, Function.identity(), (a, b) -> a));
        if (!movieIds.isEmpty()) {
            movieMapper.selectBatchIds(movieIds).forEach(m -> movies.put(m.getId(), m));
        }

        LambdaQueryWrapper<Hall> hallQuery = new LambdaQueryWrapper<Hall>().eq(Hall::getStatus, 1);
        if (cinemaId != null) {
            hallQuery.eq(Hall::getCinemaId, cinemaId);
        }
        Map<Long, Hall> halls = hallMapper.selectList(hallQuery).stream()
                .collect(Collectors.toMap(Hall::getId, Function.identity(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        List<Showtime> historical = showtimes.stream()
                .filter(s -> s.getStartTime() != null && (Objects.equals(s.getStatus(), 1) || s.getStartTime().isBefore(now)))
                .filter(s -> s.getTotalSeats() != null && s.getTotalSeats() > 0)
                .toList();
        double global = historical.stream().mapToDouble(s -> actualRate(s, soldSeats)).average().orElse(DEFAULT_OCCUPANCY);
        global = clamp(global, 0.15, 0.85);

        Map<Long, Aggregate> movieStats = new HashMap<>();
        Map<Integer, Aggregate> weekdayStats = new HashMap<>();
        Map<Integer, Aggregate> slotStats = new HashMap<>();
        Map<Long, Aggregate> hallStats = new HashMap<>();
        Map<String, Aggregate> heatStats = new HashMap<>();
        Map<Long, Long> movieTickets = new HashMap<>();
        for (Showtime showtime : historical) {
            double actual = actualRate(showtime, soldSeats);
            add(movieStats, showtime.getMovieId(), actual);
            int weekday = showtime.getStartTime().getDayOfWeek().getValue();
            int timeSlot = slot(showtime.getStartTime().toLocalTime());
            add(weekdayStats, weekday, actual);
            add(slotStats, timeSlot, actual);
            add(hallStats, showtime.getHallId(), actual);
            add(heatStats, heatKey(showtime.getHallId(), weekday, timeSlot), actual);
            movieTickets.merge(showtime.getMovieId(), (long) soldSeats.getOrDefault(showtime.getId(), 0), Long::sum);
        }
        long maxMovieTickets = movieTickets.values().stream().mapToLong(Long::longValue).max().orElse(0);
        return new ModelContext(showtimes, historical, movies, halls, soldSeats, movieStats, weekdayStats,
                slotStats, hallStats, heatStats, movieTickets, maxMovieTickets, global);
    }

    private void validateTarget(Long movieId, Long cinemaId, Long hallId) {
        Movie movie = movieMapper.selectById(movieId);
        if (movie == null) throw new BusinessException("影片不存在");
        Hall hall = hallMapper.selectById(hallId);
        if (hall == null) throw new BusinessException("影厅不存在");
        if (!Objects.equals(hall.getCinemaId(), cinemaId)) throw new BusinessException("影厅不属于所选影院");
    }

    private Map<String, Object> modelDescription() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("name", "可解释加权上座率模型 v1");
        model.put("weights", Map.of("影片热度", 35, "时段", 20, "星期", 18, "影片评分", 10, "上映天数", 9, "影厅匹配", 8));
        model.put("lowOccupancyThreshold", 35);
        model.put("training", "实时读取已支付历史订单，无需离线训练");
        return model;
    }

    private LocalDateTime nextGoldenTime() {
        LocalDate date = LocalDate.now();
        while (date.getDayOfWeek() != DayOfWeek.FRIDAY && date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            date = date.plusDays(1);
        }
        return LocalDateTime.of(date, LocalTime.of(19, 30));
    }

    private double freshness(Movie movie, LocalDate targetDate) {
        if (movie == null || movie.getReleaseDate() == null) return 0.60;
        long days = ChronoUnit.DAYS.between(movie.getReleaseDate(), targetDate);
        if (days < 0) return 0.55;
        if (days <= 14) return 0.92;
        if (days <= 30) return 0.82;
        if (days <= 90) return 0.68;
        if (days <= 180) return 0.58;
        return 0.48;
    }

    private void addDriver(List<Map<String, Object>> drivers, String name, double score, double baseline, double weight) {
        double impact = (score - baseline) * weight * 100;
        String direction = impact >= 0 ? "UP" : "DOWN";
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", name);
        driver.put("score", percent(score));
        driver.put("weight", round(weight * 100));
        driver.put("impact", round(impact));
        driver.put("direction", direction);
        driver.put("description", name + (impact >= 0 ? "高于" : "低于") + "历史基准，预计" + (impact >= 0 ? "拉升" : "拖累") + "上座率");
        drivers.add(driver);
    }

    private static <K> void add(Map<K, Aggregate> map, K key, double value) {
        map.compute(key, (ignored, current) -> current == null ? new Aggregate(value, 1) : current.add(value));
    }

    private double actualRate(Showtime showtime, Map<Long, Integer> soldSeats) {
        if (showtime.getTotalSeats() == null || showtime.getTotalSeats() <= 0) return 0;
        return clamp((double) soldSeats.getOrDefault(showtime.getId(), 0) / showtime.getTotalSeats(), 0, 1);
    }

    private int capacity(Showtime showtime, Hall hall) {
        if (showtime.getTotalSeats() != null && showtime.getTotalSeats() > 0) return showtime.getTotalSeats();
        return hall == null || hall.getTotalSeats() == null ? 0 : hall.getTotalSeats();
    }

    private double smoothed(Aggregate aggregate, double prior, int priorSamples) {
        if (aggregate == null) return prior;
        return (aggregate.sum() + prior * priorSamples) / (aggregate.count() + priorSamples);
    }

    private int slot(LocalTime time) {
        if (time.isBefore(LocalTime.NOON)) return 0;
        if (time.isBefore(LocalTime.of(18, 0))) return 1;
        if (time.isBefore(LocalTime.of(22, 0))) return 2;
        return 3;
    }

    private String heatKey(Long hallId, int weekday, int slot) {
        return hallId + ":" + weekday + ":" + slot;
    }

    private String weekdayLabel(int day) {
        return List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日").get(day - 1);
    }

    private String slotLabel(int slot) {
        return List.of("上午 06-12", "下午 12-18", "黄金 18-22", "深夜 22-06").get(slot);
    }

    private String frequencyReason(String action, double rate, int samples) {
        String evidence = samples == 0 ? "暂无同片历史，按全局基准估算" : "基于 " + samples + " 个历史场次";
        return switch (action) {
            case "ADD" -> evidence + "，需求旺盛（" + percent(rate) + "%），建议增加黄金时段供给";
            case "REDUCE" -> evidence + "，需求偏弱（" + percent(rate) + "%），建议减少非黄金场次";
            default -> evidence + "，供需暂时平衡，建议维持当前场次数";
        };
    }

    private int actionPriority(String action) {
        return "ADD".equals(action) ? 0 : "REDUCE".equals(action) ? 1 : 2;
    }

    private String confidence(int samples) {
        if (samples >= 30) return "高";
        if (samples >= 8) return "中";
        return "低";
    }

    private double money(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    private double percent(double value) {
        return round(value * 100);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Aggregate(double sum, int count) {
        Aggregate add(double value) { return new Aggregate(sum + value, count + 1); }
        double average() { return count == 0 ? 0 : sum / count; }
    }

    private record Prediction(double rate, String confidence, List<Map<String, Object>> drivers) {}
    private record Alternative(Long movieId, String title, double rate) {}
    private record ModelContext(List<Showtime> showtimes, List<Showtime> historical,
                                Map<Long, Movie> movies, Map<Long, Hall> halls,
                                Map<Long, Integer> soldSeats, Map<Long, Aggregate> movieStats,
                                Map<Integer, Aggregate> weekdayStats, Map<Integer, Aggregate> slotStats,
                                Map<Long, Aggregate> hallStats, Map<String, Aggregate> heatStats,
                                Map<Long, Long> movieTickets, long maxMovieTickets, double globalOccupancy) {}
}
