package com.movie.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssistantWatchPlanTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void pricingFlagsAloneAndOrderQuotesDoNotCreateAViewingRequest() throws Exception {
        var plan = new AssistantWatchPlan(json, null, null);
        plan.patch(json.readTree("{\"usePoints\":false,\"useCoupons\":true,\"excludedGenres\":[]}"));
        assertFalse(plan.hasViewingIntent());
        plan.rememberQuery("quote_price", json.readTree("{\"orderNo\":\"test-order\",\"people\":2}"), json.readTree("{\"movieId\":9}"));
        assertFalse(plan.hasViewingIntent());
        plan.patch(json.readTree("{\"people\":2}"));
        assertTrue(plan.hasViewingIntent());
    }
    @Test void explicitQuantityIsBoundedWithoutInventingOtherConditionsOrInterpretingHypotheticals() {
        assertEquals(1, AssistantExplicitChanges.parse(json, "只推荐一部轻松的电影，不要恐怖片").path("limit").asInt());
        assertEquals(2, AssistantExplicitChanges.parse(json, "明天两个人，预算100元，请推荐两个有连座的方案").path("limit").asInt());
        assertFalse(AssistantExplicitChanges.parse(json, "如果不用积分会怎样？").has("usePoints"));
        assertFalse(AssistantExplicitChanges.supportsScene("DATE", "我们两个人看电影"));
        assertTrue(AssistantExplicitChanges.supportsScene("DATE", "我和女朋友去看电影"));
    }
    @Test void incrementalCorrectionsKeepConstraintsAndDateChangeClearsExactShowtime() throws Exception {
        var plan = new AssistantWatchPlan(json, null, null);
        plan.patch(json.readTree(""" 
                {"date":"2030-01-01","people":2,"maxTotal":80,"excludedGenres":["恐怖"],"endBefore":"22:00"}
                """));
        plan.capture(List.of(Map.of("ok", true, "source", "plan_movie_outing", "data", Map.of("plans", List.of(
                Map.of("showtimeId", 11, "movieId", 1, "movieTitle", "第一部"), Map.of("showtimeId", 22, "movieId", 2, "movieTitle", "第二部"))))));
        plan.patch(json.readTree("{\"selectOption\":2,\"usePoints\":false}"));
        assertEquals(22, plan.criteria().get("showtimeId").asInt());
        var restored = new AssistantWatchPlan(json, json.readTree(plan.snapshot().toString()), null);
        restored.patch(json.readTree("{\"date\":\"2030-01-02\"}"));
        assertFalse(restored.criteria().has("showtimeId"));
        assertEquals(2, restored.criteria().get("movieId").asInt());
        assertEquals(2, restored.criteria().get("people").asInt());
        assertEquals(80, restored.criteria().get("maxTotal").asInt());
        assertEquals("恐怖", restored.criteria().get("excludedGenres").get(0).asText());
        assertEquals("22:00", restored.criteria().get("endBefore").asText());
        assertFalse(restored.criteria().get("usePoints").asBoolean());
        assertFalse(restored.snapshot().toString().contains("totalPrice"));
    }
    @Test void rejectingOptionsDeduplicatesMovieIdsAndPreservesOtherFilters() throws Exception {
        var plan = new AssistantWatchPlan(json, null, json.readTree("{\"excludedGenres\":[\"恐怖\"]}"));
        plan.patch(json.readTree("{\"people\":3,\"movieId\":9,\"maxTotal\":100}"));
        plan.capture(List.of(Map.of("ok", true, "source", "search_showtimes", "data", Map.of("items", List.of(
                Map.of("id", 1, "movieId", 9), Map.of("id", 2, "movieId", 9), Map.of("id", 3, "movieId", 10))))));
        plan.patch(json.readTree("{\"rejectOptions\":true}"));
        assertEquals("[9,10]", plan.criteria().get("excludedMovieIds").toString());
        assertFalse(plan.criteria().has("movieId"));
        assertEquals(100, plan.criteria().get("maxTotal").asInt());
        assertTrue(plan.snapshot().get("options").isEmpty());
        plan.patch(json.readTree("{\"excludedGenres\":[]}"));
        assertTrue(new AssistantWatchPlan(json, plan.snapshot(), json.readTree("{\"excludedGenres\":[\"恐怖\"]}")).criteria().get("excludedGenres").isEmpty());
    }
    @Test void invalidUpdatesAreAtomicAndIdentityOrPricesCannotEnterState() throws Exception {
        var plan = new AssistantWatchPlan(json, null, null); plan.patch(json.readTree("{\"people\":2}"));
        String before = plan.snapshot().toString();
        for (String invalid : List.of("{\"people\":6}", "{\"people\":2.2}", "{\"userId\":7}", "{\"maxTotal\":-1}",
                "{\"reset\":true,\"date\":\"bad\"}", "{\"startAfter\":\"20:00\",\"startBefore\":\"18:00\"}",
                "{\"excludedMovieIds\":[0]}", "{\"selectOption\":4294967297}", "{\"totalPrice\":1}", "{\"requireTogether\":\"false\"}")) {
            assertThrows(IllegalArgumentException.class, () -> plan.patch(json.readTree(invalid)), invalid);
            assertEquals(before, plan.snapshot().toString());
        }
    }
    @Test void suggestionsFollowActualStageAndEmptyResultsDoNotInviteSelectingMissingOptions() {
        assertEquals("只有我一个人", AssistantSuggestionService.suggest(json, List.of(Map.of("ok", true, "source", "plan_movie_outing", "data", Map.of("needs", List.of("people")))), json.createObjectNode(), false).getFirst());
        var empty = AssistantSuggestionService.suggest(json, List.of(Map.of("ok", true, "source", "plan_movie_outing", "data", Map.of("plans", List.of()))), json.createObjectNode(), false);
        assertTrue(empty.contains("换成明天，其他条件不变")); assertFalse(empty.toString().contains("第一个"));
        var orders = AssistantSuggestionService.suggest(json, List.of(Map.of("ok", true, "source", "get_my_orders", "data", Map.of())), json.createObjectNode(), true);
        assertTrue(orders.toString().contains("退票")); assertFalse(orders.toString().contains("连座"));
    }
}
