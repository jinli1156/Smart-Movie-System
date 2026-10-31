package com.movie.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Conversation state contains intentions and references, never authoritative prices or inventory. */
public final class AssistantWatchPlan {
    private final ObjectMapper json;
    private ObjectNode criteria;
    private JsonNode options;
    private int revision;
    public static final Set<String> FIELDS = Set.of("date", "people", "movieId", "showtimeId", "cinemaId",
            "genre", "minRating", "maxDuration", "startAfter", "startBefore", "endBefore", "maxPrice",
            "priceExclusive", "maxTotal", "cinemaKeyword", "hallKeyword", "usePoints", "useCoupons",
            "requireTogether", "preference", "naturalLanguage", "excludedGenres", "excludedMovieIds", "scene", "limit");

    public AssistantWatchPlan(ObjectMapper json, JsonNode saved, JsonNode preferences) {
        this.json = json;
        criteria = json.createObjectNode(); options = json.createArrayNode();
        if (saved != null && saved.isObject()) {
            if (saved.path("criteria").isObject()) criteria = (ObjectNode) saved.path("criteria").deepCopy();
            validate(criteria);
            if (saved.path("options").isArray()) {
                var safe = json.createArrayNode();
                for (JsonNode option : saved.path("options")) {
                    if (safe.size() == 6) break;
                    if (!Set.of("movie", "showtime").contains(option.path("kind").asText())
                            || !option.path("id").isIntegralNumber() || !option.path("id").canConvertToLong() || option.path("id").asLong() < 1) continue;
                    var entry = safe.addObject().put("kind", option.path("kind").asText()).put("id", option.path("id").asLong());
                    if (option.path("movieId").isIntegralNumber() && option.path("movieId").asLong() > 0) entry.set("movieId", option.get("movieId"));
                    entry.put("title", option.path("title").asText("").substring(0, Math.min(100, option.path("title").asText("").length())));
                }
                options = safe;
            }
        }
        // Explicit [] in a conversation overrides a stored exclusion for this outing only.
        if (preferences != null) for (String field : List.of("excludedGenres", "excludedMovieIds")) {
            if (!criteria.has(field) && preferences.has(field)) criteria.set(field, preferences.get(field).deepCopy());
        }
        validate(criteria);
    }

    public ObjectNode criteria() { return criteria.deepCopy(); }
    public int revision() { return revision; }
    public boolean hasViewingIntent() {
        var fields = criteria.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (!Set.of("usePoints", "useCoupons", "priceExclusive").contains(entry.getKey())
                    && !(entry.getValue().isArray() && entry.getValue().isEmpty())) return true;
        }
        return false;
    }
    public ObjectNode snapshot() { var node = json.createObjectNode(); node.set("criteria", criteria.deepCopy()); node.set("options", options.deepCopy()); return node; }

    public ObjectNode patch(JsonNode patch) {
        if (patch == null || !patch.isObject()) throw new IllegalArgumentException("请提供观影条件对象");
        var next = criteria.deepCopy();
        var fields = patch.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!FIELDS.contains(field) && !Set.of("reset", "selectOption", "rejectOptions").contains(field)) throw new IllegalArgumentException("未知观影条件");
        }
        for (String flag : List.of("reset", "rejectOptions")) if (patch.has(flag) && !patch.get(flag).isBoolean()) throw new IllegalArgumentException("无效开关");
        if (patch.path("reset").asBoolean()) next.removeAll();
        if (patch.has("selectOption")) {
            JsonNode index = patch.get("selectOption");
            if (!index.isIntegralNumber() || !index.canConvertToInt() || index.asInt() < 1 || index.asInt() > options.size()) throw new IllegalArgumentException("该编号没有对应的候选，请先查询或明确片名");
            JsonNode selected = options.get(index.asInt() - 1);
            next.remove("showtimeId");
            if (selected.path("kind").asText().equals("movie")) next.set("movieId", selected.get("id"));
            else {
                next.set("showtimeId", selected.get("id"));
                if (selected.has("movieId")) next.set("movieId", selected.get("movieId"));
            }
        }
        if (patch.path("rejectOptions").asBoolean()) {
            var ids = new LinkedHashSet<Long>();
            next.path("excludedMovieIds").forEach(id -> ids.add(id.asLong()));
            for (JsonNode option : options) {
                JsonNode id = option.path("kind").asText().equals("movie") ? option.get("id") : option.get("movieId");
                if (id != null) ids.add(id.asLong());
            }
            next.set("excludedMovieIds", json.valueToTree(ids)); next.remove(List.of("movieId", "showtimeId"));
        }
        for (String field : FIELDS) if (patch.has(field)) {
            if (patch.get(field).isNull()) next.remove(field); else next.set(field, patch.get(field).deepCopy());
        }
        // A date/movie change invalidates the old exact showtime, while retaining the selected film.
        if ((patch.has("date") && !Objects.equals(criteria.get("date"), next.get("date")))
                || (patch.has("movieId") && !Objects.equals(criteria.get("movieId"), next.get("movieId")))) {
            next.remove("showtimeId");
        }
        validate(next);
        if (!criteria.equals(next)) revision++;
        criteria = next;
        if (patch.path("reset").asBoolean() || patch.path("rejectOptions").asBoolean()) options = json.createArrayNode();
        return snapshot();
    }

    public static void validate(JsonNode values) {
        if (values == null || !values.isObject()) throw new IllegalArgumentException("无效观影条件");
        values.fields().forEachRemaining(entry -> {
            String key = entry.getKey(); JsonNode value = entry.getValue();
            if (!FIELDS.contains(key)) throw new IllegalArgumentException("未知观影条件");
            switch (key) {
                case "people", "limit", "maxDuration", "movieId", "showtimeId", "cinemaId" -> {
                    long max = key.equals("people") ? 5 : key.equals("limit") ? 3 : key.equals("maxDuration") ? 1000 : Long.MAX_VALUE;
                    if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 1 || value.asLong() > max) throw new IllegalArgumentException("人数为1至5人，方案为1至3项，ID必须有效");
                }
                case "maxPrice", "maxTotal", "minRating" -> {
                    if (!value.isNumber() || value.decimalValue().signum() < 0 || value.decimalValue().compareTo(new BigDecimal(key.equals("minRating") ? "10" : "100000")) > 0 || value.decimalValue().stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("无效预算或评分");
                }
                case "usePoints", "useCoupons", "requireTogether", "priceExclusive" -> { if (!value.isBoolean()) throw new IllegalArgumentException("无效开关"); }
                case "excludedGenres", "excludedMovieIds" -> {
                    if (!value.isArray() || value.size() > (key.equals("excludedGenres") ? 10 : 50)) throw new IllegalArgumentException("排除项过多");
                    for (JsonNode item : value) {
                        if (key.equals("excludedMovieIds")) {
                            if (!item.isIntegralNumber() || !item.canConvertToLong() || item.asLong() < 1) throw new IllegalArgumentException("无效影片ID");
                        } else if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 20) throw new IllegalArgumentException("无效排除类型");
                    }
                }
                default -> {
                    if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 200) throw new IllegalArgumentException("条件文本无效");
                    try {
                        if (key.equals("date")) LocalDate.parse(value.asText());
                        if (Set.of("startAfter", "startBefore", "endBefore").contains(key)) {
                            if (!value.asText().matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException("时间使用HH:mm");
                            LocalTime.parse(value.asText());
                        }
                    } catch (java.time.DateTimeException ex) { throw new IllegalArgumentException("日期或时间无效"); }
                    if (key.equals("scene") && !Set.of("RELAXED", "DATE", "FAMILY", "FRIENDS", "SOLO").contains(value.asText())) throw new IllegalArgumentException("场景无效");
                    if (key.equals("preference") && !Set.of("CENTER", "QUIET", "EASY_ACCESS", "COUPLE", "ACCESSIBLE").contains(value.asText())) throw new IllegalArgumentException("座位偏好无效");
                }
            }
        });
        if (values.has("startAfter") && values.has("startBefore") && values.get("startAfter").asText().compareTo(values.get("startBefore").asText()) >= 0) throw new IllegalArgumentException("开场时间范围冲突，跨午夜请分日期查询");
    }

    public void capture(List<Map<String, Object>> results) {
        // Match the primary result family rendered by the client. Only retain references, not snapshots of money/seats.
        Map<String, Object> last = null;
        for (String source : List.of("plan_movie_outing", "compare_prices")) {
            for (var result : results) if (Boolean.TRUE.equals(result.get("ok")) && source.equals(result.get("source"))) last = result;
            if (last != null) break;
        }
        if (last == null) for (var result : results) {
            if (Boolean.TRUE.equals(result.get("ok")) && Set.of("search_showtimes", "discover_movies", "recommend_movies", "search_movies").contains(Objects.toString(result.get("source"), ""))) last = result;
        }
        if (last == null) return;
        var data = json.valueToTree(last.get("data")); String source = last.get("source").toString();
        boolean film = source.equals("search_movies") || source.equals("recommend_movies") || source.equals("discover_movies");
        JsonNode items = data.path(source.equals("plan_movie_outing") ? "plans" : source.equals("compare_prices") ? "offers" : source.equals("recommend_movies") || source.equals("discover_movies") ? "movies" : "items");
        var refs = json.createArrayNode();
        for (JsonNode item : items) {
            if (refs.size() == (source.equals("compare_prices") || source.equals("plan_movie_outing") ? 3 : 6)) break;
            JsonNode id = item.has("showtimeId") ? item.get("showtimeId") : item.get("id");
            if (id == null || !id.isIntegralNumber() || id.asLong() < 1) continue;
            ObjectNode ref = refs.addObject().put("kind", film ? "movie" : "showtime"); ref.set("id", id);
            if (item.has("movieId")) ref.set("movieId", item.get("movieId"));
            else if (data.has("movieId")) ref.set("movieId", data.get("movieId"));
            ref.put("title", item.path(film ? "title" : "movieTitle").asText(data.path("movieTitle").asText("")));
        }
        options = refs;
    }

    public void rememberQuery(String name, JsonNode args, JsonNode data) {
        if (!Set.of("search_showtimes", "compare_prices", "recommend_seats", "quote_price").contains(name)) return;
        if (name.equals("quote_price") && args.has("orderNo")) return;
        var patch = json.createObjectNode();
        for (String key : FIELDS) if (args.has(key)) {
            if (key.equals("limit")) patch.put(key, Math.min(3, args.get(key).asInt()));
            else patch.set(key, args.get(key));
        }
        if (data.has("date")) patch.set("date", data.get("date"));
        if (data.has("movieId")) patch.set("movieId", data.get("movieId"));
        patch(patch);
    }
}
