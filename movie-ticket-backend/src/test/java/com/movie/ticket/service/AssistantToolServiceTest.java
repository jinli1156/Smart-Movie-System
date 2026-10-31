package com.movie.ticket.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.mapper.*;
import com.movie.ticket.vo.OrderVO;
import com.movie.ticket.vo.ShowtimeVO;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssistantToolServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final MovieMapper movies = mock(MovieMapper.class);
    private final CinemaMapper cinemas = mock(CinemaMapper.class);
    private final TicketOrderMapper orders = mock(TicketOrderMapper.class);
    private final ShowtimeMapper showtimes = mock(ShowtimeMapper.class);
    private final RecommendService recommendations = mock(RecommendService.class);
    private final AssistantBookingService booking = mock(AssistantBookingService.class);
    private AssistantToolService tools;

    @BeforeEach void setup() throws Exception {
        tools = new AssistantToolService(movies, cinemas, orders, showtimes, mock(ShowtimeService.class),
                recommendations, new AssistantKnowledgeService(mapper), mapper, booking, mock(AssistantOutingService.class), mock(AssistantCheckoutService.class));
    }

    @Test void anonymousPrivateQueriesNeverTouchDatabase() {
        assertEquals("LOGIN_REQUIRED", tools.execute("get_my_orders", "{}", null).get("code"));
        assertEquals("LOGIN_REQUIRED", tools.execute("get_my_preferences", "{}", null).get("code"));
        assertEquals("LOGIN_REQUIRED", tools.execute("quote_price", "{\"showtimeId\":1,\"people\":2}", null).get("code"));
        assertEquals("LOGIN_REQUIRED", tools.execute("prepare_ticket_order", "{\"showtimeId\":1,\"people\":2}", null).get("code"));
        assertEquals("INVALID_ARGUMENTS", tools.execute("confirm_ticket_order", "{}", 1L).get("code"));
        verifyNoInteractions(orders, recommendations, booking);
    }

    @Test void rejectForgedIdentityUnknownToolAndInvalidArguments() {
        assertEquals("INVALID_ARGUMENTS", tools.execute("get_my_orders", "{\"userId\":2}", 1L).get("code"));
        assertEquals("INVALID_ARGUMENTS", tools.execute("pay_order", "{}", 1L).get("code"));
        assertEquals("INVALID_ARGUMENTS", tools.execute("get_seats", "{\"showtimeId\":-1}", 1L).get("code"));
        assertEquals("INVALID_ARGUMENTS", tools.execute("get_seats", "{\"showtimeId\":1.5}", 1L).get("code"));
        assertEquals("INVALID_ARGUMENTS", tools.execute("get_seats", "{}", 1L).get("code"));
        assertEquals("INVALID_ARGUMENTS", tools.execute("search_movies", "{\"page\":101}", 1L).get("code"));
        assertEquals("INVALID_ARGUMENTS", tools.execute("search_movies", "{\"status\":0}", 1L).get("code"));
        assertEquals("INVALID_ARGUMENTS", tools.execute("search_showtimes", "{\"date\":\"tomorrow\"}", 1L).get("code"));
        verifyNoInteractions(orders, movies, showtimes);
    }

    @Test void ordersAlwaysUseAuthenticatedOwnerAndOmitSensitiveFields() throws Exception {
        var order = new OrderVO(); order.setUserId(7L); order.setOrderNo("ORDER-7");
        order.setMovieTitle("测试电影"); order.setTicketCode("PRIVATE-TICKET-CODE"); order.setUserName("private-name");
        when(orders.selectOrderPage(any(), eq(7L), eq("ORDER-7"), eq(0), eq(false)))
                .thenReturn(new Page<OrderVO>(1, 8, 1).setRecords(List.of(order)));
        var result = tools.execute("get_my_orders", "{\"orderNo\":\"ORDER-7\",\"status\":0}", 7L);
        assertEquals(true, result.get("ok"));
        verify(orders).selectOrderPage(any(), eq(7L), eq("ORDER-7"), eq(0), eq(false));
        String serialized = mapper.writeValueAsString(result);
        assertTrue(serialized.contains("ORDER-7"));
        assertFalse(serialized.contains("PRIVATE-TICKET-CODE"));
        assertFalse(serialized.contains("private-name"));
        assertFalse(serialized.contains("userId"));
    }

    @Test void queryFailureIsNotReportedAsEmptyAndDoesNotLeakException() {
        when(orders.selectOrderPage(any(), eq(7L), isNull(), isNull(), eq(false)))
                .thenThrow(new RuntimeException("password=secret"));
        var result = tools.execute("get_my_orders", "{}", 7L);
        assertEquals(false, result.get("ok"));
        assertEquals("QUERY_FAILED", result.get("code"));
        assertFalse(result.toString().contains("secret"));
    }

    @Test void knowledgeRetrievesProjectRefundRulesWithAttributionAndNoUnrelatedGuess() throws Exception {
        var knowledge = new AssistantKnowledgeService(mapper);
        var matches = knowledge.search("退票手续费是多少？");
        assertEquals("refund", matches.get(0).path("id").asText());
        assertTrue(matches.get(0).path("content").asText().contains("10%"));
        assertTrue(matches.get(0).path("source").asText().contains("OrderService"));
        assertTrue(knowledge.search("宠物能进场吗").isEmpty());
    }

    @Test void budgetQueryFiltersAllMoviesAtDatabaseAndLimitsOneResult() {
        when(showtimes.selectAssistantPage(any(), isNull(), isNull(), any(), any(), any(), eq(new BigDecimal("40")), eq(true), anyMap()))
                .thenReturn(new Page<ShowtimeVO>(1, 1, 0));
        var result = tools.execute("search_showtimes", "{\"maxPrice\":40,\"priceExclusive\":true,\"limit\":1}", null);
        assertEquals(true, result.get("ok"));
        var data = mapper.valueToTree(result.get("data"));
        assertEquals(1, data.path("size").asInt());
        assertEquals(0, data.path("total").asInt());
        assertFalse(data.path("hasMore").asBoolean());
        verify(showtimes).selectAssistantPage(argThat(page -> page.getSize() == 1), isNull(), isNull(), any(), any(), any(), eq(new BigDecimal("40")), eq(true), anyMap());
        verifyNoInteractions(movies, recommendations);
    }

    @Test void sceneDiscoveryAppliesExclusionsAndUsesOnlyActualGenreEvidence() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "scene"), com.movie.ticket.entity.Movie.class);
        var comedy = new com.movie.ticket.entity.Movie(); comedy.setId(1L); comedy.setTitle("轻松候选"); comedy.setGenre("喜剧"); comedy.setRating(new BigDecimal("7"));
        var action = new com.movie.ticket.entity.Movie(); action.setId(2L); action.setTitle("动作候选"); action.setGenre("动作"); action.setRating(new BigDecimal("9"));
        when(movies.selectList(any())).thenReturn(List.of(action, comedy));
        var result = mapper.valueToTree(tools.execute("discover_movies", "{\"scene\":\"RELAXED\",\"excludedGenres\":[\"恐怖\"],\"excludedMovieIds\":[3],\"limit\":1}", null));
        assertTrue(result.path("ok").asBoolean()); assertEquals(1, result.at("/data/movies/0/id").asInt());
        assertTrue(result.at("/data/movies/0/reason").asText().contains("喜剧"));
        assertTrue(result.at("/data/notice").asText().contains("不能证明适龄"));
        verify(movies).selectList(argThat(wrapper -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.movie.ticket.entity.Movie>) wrapper;
            return query.getSqlSegment().contains("genre NOT LIKE") && query.getSqlSegment().contains("id NOT IN")
                    && query.getParamNameValuePairs().containsValue("%恐怖%") && query.getParamNameValuePairs().containsValue(3L);
        }));
    }

    @Test void recommendationAndShowtimeResultsIncludeCatalogPosters() {
        var movie = new com.movie.ticket.vo.MovieVO();
        movie.setId(1L); movie.setTitle("测试影片"); movie.setPoster("/uploads/movie.jpg"); movie.setDuration(120);
        when(recommendations.getHotMovies(5)).thenReturn(List.of(movie));
        var recommended = mapper.valueToTree(tools.execute("recommend_movies", "{}", null));
        assertEquals("/uploads/movie.jpg", recommended.at("/data/movies/0/poster").asText());
        assertEquals(120, recommended.at("/data/movies/0/duration").asInt());

        var showtime = new ShowtimeVO(); showtime.setId(2L); showtime.setMoviePoster("/uploads/movie.jpg");
        when(showtimes.selectAssistantPage(any(), isNull(), isNull(), any(), any(), any(), isNull(), eq(false), anyMap()))
                .thenReturn(new Page<ShowtimeVO>(1, 8, 1).setRecords(List.of(showtime)));
        var result = mapper.valueToTree(tools.execute("search_showtimes", "{}", null));
        assertEquals("/uploads/movie.jpg", result.at("/data/items/0/moviePoster").asText());
    }

    @Test void budgetSupportsInclusiveDecimalPricesAndRejectsInvalidBounds() {
        when(showtimes.selectAssistantPage(any(), isNull(), isNull(), any(), any(), any(), argThat(price -> price != null && price.compareTo(new BigDecimal("39.90")) == 0), eq(false), anyMap()))
                .thenReturn(new Page<ShowtimeVO>(1, 1, 0));
        assertEquals(true, tools.execute("search_showtimes", "{\"maxPrice\":39.90,\"limit\":1}", null).get("ok"));
        for (String arguments : List.of("{\"maxPrice\":-1}", "{\"maxPrice\":\"40\"}", "{\"maxPrice\":100001}",
                "{\"maxPrice\":39.999}", "{\"priceExclusive\":true}", "{\"maxPrice\":40,\"priceExclusive\":\"true\"}", "{\"limit\":0}", "{\"limit\":9}")) {
            assertEquals("INVALID_ARGUMENTS", tools.execute("search_showtimes", arguments, null).get("code"));
        }
    }

    @Test void comparisonRejectsForgedIdentityAndMalformedInputsBeforeQuery() {
        for (String args : List.of("{\"movieId\":2,\"people\":2,\"userId\":999}",
                "{\"movieId\":2,\"people\":6}", "{\"movieId\":2,\"people\":2,\"date\":\"tomorrow\"}",
                "{\"movieId\":2,\"people\":2,\"maxTotal\":-1}")) {
            assertEquals("INVALID_ARGUMENTS", tools.execute("compare_prices", args, 7L).get("code"));
        }
        verifyNoInteractions(booking);
    }

    @Test void comparisonToolUsesAuthenticatedIdentityAndPreservesDiscountPreferences() {
        var result = new com.movie.ticket.vo.PriceComparisonVO(2L, "电影", null, java.time.LocalDate.now(), 2,
                true, false, true, BigDecimal.TEN, 0, 0, java.time.LocalDateTime.now(), List.of(), "仅比价");
        when(booking.compare(eq(7L), any())).thenReturn(result);
        var response = tools.execute("compare_prices", "{\"movieId\":2,\"people\":2,\"usePoints\":false,\"maxTotal\":80}", 7L);
        assertEquals(true, response.get("ok")); assertEquals("compare_prices", response.get("source"));
        assertEquals("电影", mapper.valueToTree(response.get("data")).path("movieTitle").asText());
        verify(booking).compare(eq(7L), argThat(r -> r.getMovieId() == 2 && r.getPeople() == 2
                && !r.isUsePoints() && r.isUseCoupons() && r.getMaxTotal().compareTo(new BigDecimal("80")) == 0));
    }

    @Test void complexConditionsArePassedTogetherWithExactTimeBounds() {
        when(showtimes.selectAssistantPage(any(), isNull(), isNull(), any(), any(), any(), any(), eq(false), anyMap()))
                .thenReturn(new Page<ShowtimeVO>(1, 8, 0));
        String args = """
                {"date":"2099-09-05","startAfter":"20:00","startBefore":"23:00","genre":"科幻",
                 "minRating":8,"maxDuration":120,"hallKeyword":"IMAX","cinemaKeyword":"南宁","people":2,"maxPrice":50}
                """;
        assertEquals(true, tools.execute("search_showtimes", args, null).get("ok"));
        verify(showtimes).selectAssistantPage(any(), isNull(), isNull(),
                eq(java.time.LocalDateTime.parse("2099-09-05T20:00")), eq(java.time.LocalDateTime.parse("2099-09-05T23:00")),
                any(), eq(new BigDecimal("50")), eq(false), argThat(f -> f.size() == 6 && f.get("people").equals(2)
                        && f.get("minRating").equals(8) && f.get("maxDuration").equals(120) && f.get("hallKeyword").equals("IMAX")));
    }

    @Test void newToolsRejectInvalidBoundsAndAmbiguousQuoteTargets() {
        for (String args : List.of("{\"minRating\":10.1}", "{\"maxDuration\":0}", "{\"people\":6}",
                "{\"startAfter\":\"24:00\"}", "{\"startAfter\":\"8:00\"}",
                "{\"startAfter\":\"23:00\",\"startBefore\":\"01:00\"}")) {
            assertEquals("INVALID_ARGUMENTS", tools.execute("search_showtimes", args, null).get("code"));
        }
        for (String args : List.of("{}", "{\"showtimeId\":1}", "{\"orderNo\":\"X\",\"people\":2}",
                "{\"orderNo\":\"X\",\"userId\":8}", "{\"showtimeId\":1,\"people\":2,\"price\":1}")) {
            assertEquals("INVALID_ARGUMENTS", tools.execute("quote_price", args, 7L).get("code"));
        }
        assertEquals("INVALID_ARGUMENTS", tools.execute("recommend_seats", "{\"showtimeId\":1,\"people\":2,\"preference\":\"INVALID\"}", null).get("code"));
        verifyNoInteractions(showtimes, booking);
    }

    @Test void quotePassesCapturedIdentityAndPointsChoiceToService() {
        when(booking.quote(7L, 1L, 2, null, false)).thenReturn(java.util.Map.of("notice", "仅试算"));
        assertEquals(true, tools.execute("quote_price", "{\"showtimeId\":1,\"people\":2,\"usePoints\":false}", 7L).get("ok"));
        verify(booking).quote(7L, 1L, 2, null, false);
    }
}
