package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.entity.*;
import com.movie.ticket.mapper.*;
import com.movie.ticket.vo.*;
import com.movie.ticket.dto.SeatRecommendationRequest;
import com.movie.ticket.dto.PriceCompareRequest;
import com.movie.ticket.exception.BusinessException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** 助手工具分发：校验工具参数并调用站内查询能力，将真实业务数据返回给模型。 */
@Service
public class AssistantToolService {
    private final MovieMapper movies;
    private final CinemaMapper cinemas;
    private final TicketOrderMapper orders;
    private final ShowtimeMapper showtimeMapper;
    private final ShowtimeService showtimes;
    private final RecommendService recommendations;
    private final AssistantKnowledgeService knowledge;
    private final AssistantBookingService booking;
    private final AssistantOutingService outings;
    private final AssistantCheckoutService checkout;
    private final ObjectMapper mapper;
    private final JsonNode definitions;

    public AssistantToolService(MovieMapper movies, CinemaMapper cinemas, TicketOrderMapper orders,
            ShowtimeMapper showtimeMapper, ShowtimeService showtimes, RecommendService recommendations,
            AssistantKnowledgeService knowledge, ObjectMapper mapper, AssistantBookingService booking, AssistantOutingService outings, AssistantCheckoutService checkout) throws IOException {
        this.movies = movies; this.cinemas = cinemas; this.orders = orders;
        this.showtimeMapper = showtimeMapper; this.showtimes = showtimes;
        this.recommendations = recommendations; this.knowledge = knowledge; this.mapper = mapper;
        this.booking = booking;
        this.outings = outings;
        this.checkout = checkout;
        try (var input = new ClassPathResource("assistant/tools.json").getInputStream()) {
            definitions = mapper.readTree(input);
        }
    }

    /** 返回模型可调用的工具定义与参数约束。 */
    public JsonNode definitions() { return definitions.deepCopy(); }

    /** 根据工具名称分派真实业务查询，并统一封装结果。 */
    public Map<String, Object> execute(String name, String arguments, Long userId) {
        try {
            JsonNode args = mapper.readTree(arguments);
            if (Set.of("plan_movie_outing", "discover_movies", "prepare_ticket_order").contains(name)) AssistantWatchPlan.validate(args);
            else validate(name, args);
            if (Set.of("get_my_orders", "get_my_preferences", "quote_price", "prepare_ticket_order").contains(name) && userId == null) {
                return failure("LOGIN_REQUIRED", "请先登录，再查询自己的订单、偏好或试算优惠。");
            }
            int page = args.path("page").asInt(1);
            Object data = switch (name) {
                case "prepare_ticket_order" -> checkout.prepare(userId, args, null);
                case "plan_movie_outing" -> outings.plan(args, userId);
                case "discover_movies" -> discoverMovies(args);
                case "search_movies" -> searchMovies(args, page);
                case "search_cinemas" -> searchCinemas(args, page);
                case "search_showtimes" -> searchShowtimes(args, page);
                case "get_seats" -> seats(args.path("showtimeId").asLong());
                case "recommend_seats" -> recommendSeats(args);
                case "compare_prices" -> comparePrices(args, userId);
                case "quote_price" -> booking.quote(userId, id(args, "showtimeId"),
                        args.has("people") ? args.path("people").asInt() : null,
                        args.has("orderNo") ? args.path("orderNo").asText() : null,
                        args.path("usePoints").asBoolean(true));
                case "get_my_orders" -> myOrders(args, page, userId);
                case "recommend_movies" -> userId == null
                        ? Map.of("mode", "热门推荐（访客）", "movies", recommendations.getHotMovies(args.path("limit").asInt(5)).stream().map(this::movieSummary).toList())
                        : Map.of("mode", "个性化推荐", "movies", recommendations.getRecommendedMovies(userId, args.path("limit").asInt(5), null, null)
                                .stream().map(m -> { var summary = movieSummary(m); summary.put("reason", m.getRecommendationReason()); return summary; }).toList());
                case "get_my_preferences" -> recommendations.getProfile(userId);
                case "search_knowledge" -> knowledge.search(args.path("query").asText());
                default -> throw new IllegalArgumentException();
            };
            return Map.of("ok", true, "source", name, "queriedAt", now().toString(), "data", data);
        } catch (BusinessException ex) {
            return failure("BUSINESS_RULE", ex.getMessage());
        } catch (IllegalArgumentException | IOException ex) {
            return failure("INVALID_ARGUMENTS", "工具或参数无效，请按工具定义重试，日期使用YYYY-MM-DD，不接受userId。");
        } catch (Exception ex) {
            // Never echo database errors, credentials, or private records into model context.
            return failure("QUERY_FAILED", "本次查询失败，无法确认业务数据，请稍后重试。");
        }
    }

    private void validate(String name, JsonNode args) {
        if (args == null || !args.isObject()) throw new IllegalArgumentException();
        JsonNode schema = null;
        for (JsonNode definition : definitions) {
            if (definition.path("function").path("name").asText().equals(name)) schema = definition.path("function").path("parameters");
        }
        if (schema == null) throw new IllegalArgumentException();
        for (JsonNode required : schema.path("required")) if (!args.hasNonNull(required.asText())) throw new IllegalArgumentException();
        var fields = args.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            JsonNode property = schema.path("properties").path(field.getKey());
            JsonNode value = field.getValue();
            if (property.isMissingNode()) throw new IllegalArgumentException();
            if (property.path("type").asText().equals("integer")) {
                if (!value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException();
                if (property.has("minimum") && value.asLong() < property.path("minimum").asLong()) throw new IllegalArgumentException();
                if (property.has("maximum") && value.asLong() > property.path("maximum").asLong()) throw new IllegalArgumentException();
                if (property.has("enum")) {
                    boolean found = false;
                    for (JsonNode option : property.path("enum")) if (option.asLong() == value.asLong()) found = true;
                    if (!found) throw new IllegalArgumentException();
                }
            } else if (property.path("type").asText().equals("number")) {
                if (!value.isNumber() || value.decimalValue().compareTo(BigDecimal.ZERO) < 0
                        || value.decimalValue().compareTo(new BigDecimal("100000")) > 0
                        || value.decimalValue().stripTrailingZeros().scale() > 2) throw new IllegalArgumentException();
                if (property.has("minimum") && value.decimalValue().compareTo(property.path("minimum").decimalValue()) < 0) throw new IllegalArgumentException();
                if (property.has("maximum") && value.decimalValue().compareTo(property.path("maximum").decimalValue()) > 0) throw new IllegalArgumentException();
            } else if (property.path("type").asText().equals("boolean")) {
                if (!value.isBoolean()) throw new IllegalArgumentException();
            } else if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 200) throw new IllegalArgumentException();
            if (value.isTextual() && property.has("enum")) {
                boolean found = false;
                for (JsonNode option : property.path("enum")) if (option.asText().equals(value.asText())) found = true;
                if (!found) throw new IllegalArgumentException();
            }
        }
        if (args.has("priceExclusive") && !args.has("maxPrice")) throw new IllegalArgumentException();
        if (name.equals("quote_price")) {
            if (args.has("orderNo") ? args.has("showtimeId") || args.has("people")
                    : !args.has("showtimeId") || !args.has("people")) throw new IllegalArgumentException();
        }
    }

    private Object searchMovies(JsonNode args, int page) {
        var query = new LambdaQueryWrapper<Movie>().in(Movie::getStatus, 1, 2);
        if (args.has("keyword")) {
            String keyword = args.path("keyword").asText();
            query.and(w -> w.like(Movie::getTitle, keyword).or().like(Movie::getEnglishTitle, keyword)
                    .or().like(Movie::getDirector, keyword).or().like(Movie::getActors, keyword));
        }
        if (args.has("genre")) query.like(Movie::getGenre, args.path("genre").asText());
        if (args.has("status")) query.eq(Movie::getStatus, args.path("status").asInt());
        var result = movies.selectPage(new Page<>(page, 8), query.orderByDesc(Movie::getRating).orderByAsc(Movie::getId));
        return Map.of("items", result.getRecords().stream().map(m -> project(m, "id", "title", "englishTitle", "genre", "director", "actors", "duration", "rating", "status", "releaseDate", "description", "poster")).toList(),
                "total", result.getTotal(), "page", page, "size", 8);
    }

    private Object discoverMovies(JsonNode args) {
        var query = new LambdaQueryWrapper<Movie>().in(Movie::getStatus, 1, 2);
        if (args.has("genre")) query.like(Movie::getGenre, args.get("genre").asText());
        if (args.has("minRating")) query.ge(Movie::getRating, args.get("minRating").decimalValue());
        if (args.has("maxDuration")) query.le(Movie::getDuration, args.get("maxDuration").asInt());
        for (JsonNode genre : args.path("excludedGenres")) query.and(q -> q.isNull(Movie::getGenre).or().notLike(Movie::getGenre, genre.asText()));
        if (!args.path("excludedMovieIds").isEmpty()) {
            List<Long> ids = new ArrayList<>(); args.get("excludedMovieIds").forEach(id -> ids.add(id.asLong()));
            query.notIn(Movie::getId, ids);
        }
        var eligible = new ArrayList<>(movies.selectList(query));
        String scene = args.path("scene").asText("");
        eligible.sort(Comparator.<Movie>comparingInt(m -> -AssistantSceneMatcher.score(scene, m.getGenre()))
                .thenComparing(Movie::getRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Movie::getId));
        var items = eligible.stream().limit(args.path("limit").asInt(3)).map(movie -> {
            var value = project(movie, "id", "title", "poster", "genre", "duration", "rating", "status", "description");
            value.put("reason", scene.isEmpty() ? "已应用本次题材、评分、时长及排除条件，按本系统评分排序" : AssistantSceneMatcher.evidence(scene, movie.getGenre()));
            return value;
        }).toList();
        return Map.of("movies", items, "total", eligible.size(), "criteria", args, "notice", AssistantSceneMatcher.NOTICE);
    }

    private Object comparePrices(JsonNode args, Long userId) {
        var request = new PriceCompareRequest();
        request.setMovieId(id(args, "movieId"));
        request.setShowtimeId(id(args, "showtimeId"));
        request.setPeople(args.path("people").asInt());
        request.setUsePoints(args.path("usePoints").asBoolean(true));
        request.setUseCoupons(args.path("useCoupons").asBoolean(true));
        if (args.has("date")) {
            try { request.setDate(LocalDate.parse(args.path("date").asText())); }
            catch (DateTimeException ex) { throw new IllegalArgumentException(); }
        }
        if (args.has("maxTotal")) request.setMaxTotal(args.path("maxTotal").decimalValue());
        return booking.compare(userId, request);
    }

    private Object searchCinemas(JsonNode args, int page) {
        var query = new LambdaQueryWrapper<Cinema>().eq(Cinema::getStatus, 1);
        if (args.has("keyword")) query.and(w -> w.like(Cinema::getName, args.path("keyword").asText()).or().like(Cinema::getAddress, args.path("keyword").asText()));
        var result = cinemas.selectPage(new Page<>(page, 8), query.orderByAsc(Cinema::getId));
        return Map.of("items", result.getRecords().stream().map(c -> project(c, "id", "name", "address", "description")).toList(),
                "total", result.getTotal(), "page", page, "size", 8);
    }

    private Object searchShowtimes(JsonNode args, int page) {
        LocalDate date;
        try { date = args.has("date") ? LocalDate.parse(args.path("date").asText()) : now().toLocalDate(); }
        catch (DateTimeException ex) { throw new IllegalArgumentException(); }
        int limit = args.path("limit").asInt(8);
        BigDecimal maxPrice = args.has("maxPrice") ? args.path("maxPrice").decimalValue() : null;
        boolean priceExclusive = args.path("priceExclusive").asBoolean(false);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        try {
            if (args.has("startAfter")) start = date.atTime(parseTime(args.path("startAfter").asText()));
            if (args.has("startBefore")) end = date.atTime(parseTime(args.path("startBefore").asText()));
            if (!start.isBefore(end)) throw new IllegalArgumentException();
        } catch (DateTimeException ex) { throw new IllegalArgumentException(); }
        Map<String, Object> filters = new LinkedHashMap<>();
        for (String field : List.of("genre", "minRating", "maxDuration", "hallKeyword", "cinemaKeyword", "people")) {
            if (args.has(field)) filters.put(field, mapper.convertValue(args.get(field), Object.class));
        }
        var result = showtimeMapper.selectAssistantPage(new Page<>(page, limit), id(args, "movieId"), id(args, "cinemaId"),
                start, end, now().plusMinutes(15), maxPrice, priceExclusive, filters);
        var data = new LinkedHashMap<String, Object>();
        data.put("items", result.getRecords().stream().map(s -> project(s, "id", "movieId", "movieTitle", "moviePoster", "movieGenre", "movieRating", "movieDuration", "cinemaId", "cinemaName", "hallName", "startTime", "endTime", "basePrice", "availableSeats")).toList());
        data.put("filters", mapper.convertValue(args, Map.class));
        data.put("notice", "全部条件在分页前筛选；时段含起点、不含终点；人数仅检查余票总数，连座需另查。影厅关键词按本系统影厅名称匹配。");
        data.put("date", date.toString()); data.put("page", page); data.put("size", limit); data.put("total", result.getTotal());
        data.put("hasMore", result.getCurrent() < result.getPages());
        if (maxPrice != null) {
            data.put("maxPrice", maxPrice); data.put("priceExclusive", priceExclusive);
            data.put("priceBasis", "场次基础票价，未假设优惠券或积分抵扣");
        }
        return data;
    }

    private LocalTime parseTime(String value) {
        if (!value.matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException();
        return LocalTime.parse(value);
    }

    private Object recommendSeats(JsonNode args) {
        var request = new SeatRecommendationRequest();
        request.setPeople(args.path("people").asInt());
        request.setLimit(args.path("limit").asInt(3));
        request.setPreference(args.path("preference").asText("CENTER"));
        request.setNaturalLanguage(args.path("naturalLanguage").asText(null));
        return booking.recommendSeats(args.path("showtimeId").asLong(), request);
    }

    private Object seats(Long showtimeId) {
        var showtime = showtimes.getById(showtimeId);
        var cinema = cinemas.selectById(showtime.getCinemaId());
        var movie = movies.selectById(showtime.getMovieId());
        if (cinema == null || movie == null || !Integer.valueOf(1).equals(cinema.getStatus()) || !Integer.valueOf(1).equals(movie.getStatus())) throw new IllegalArgumentException();
        if (!Integer.valueOf(0).equals(showtime.getStatus()) || showtime.getStartTime().isBefore(now().plusMinutes(15))) {
            return Map.of("bookable", false, "message", "该场次已不可购票，请重新选择场次。");
        }
        var result = new LinkedHashMap<>(showtimes.getSeatStatus(showtimeId));
        result.put("showtimeId", showtimeId);
        result.put("movieId", movie.getId());
        result.put("movieTitle", movie.getTitle());
        if (movie.getPoster() != null) result.put("moviePoster", movie.getPoster());
        result.put("bookable", true);
        result.put("notice", "余票为查询快照，未锁座，以提交订单校验为准。");
        return result;
    }

    private Object myOrders(JsonNode args, int page, Long userId) {
        // Explicit ownership predicate, including for administrators. No model-provided identity.
        var result = orders.selectOrderPage(new Page<>(page, 8), userId,
                args.has("orderNo") ? args.path("orderNo").asText() : null,
                args.has("status") ? args.path("status").asInt() : null, false);
        return Map.of("items", result.getRecords().stream().map(o -> project(o, "orderNo", "movieId", "movieTitle", "moviePoster", "cinemaName", "hallName", "showtime", "seats", "seatCount", "totalPrice", "status", "statusText", "isPicked")).toList(),
                "page", page, "size", 8, "total", result.getTotal());
    }

    private Map<String, Object> movieSummary(MovieVO movie) {
        return project(movie, "id", "title", "poster", "genre", "duration", "rating", "status", "description");
    }

    private Map<String, Object> project(Object object, String... fields) {
        JsonNode node = mapper.valueToTree(object);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : fields) if (node.hasNonNull(field)) result.put(field, node.get(field));
        return result;
    }

    private Long id(JsonNode args, String field) { return args.has(field) ? args.path(field).asLong() : null; }
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
    private Map<String, Object> failure(String code, String message) { return Map.of("ok", false, "code", code, "message", message); }
}
