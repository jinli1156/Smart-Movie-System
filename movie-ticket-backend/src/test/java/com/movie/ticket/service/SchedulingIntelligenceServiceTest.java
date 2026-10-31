package com.movie.ticket.service;

import com.movie.ticket.dto.SchedulingPredictionRequest;
import com.movie.ticket.dto.SchedulingOptimizationRequest;
import com.movie.ticket.entity.Hall;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingIntelligenceServiceTest {
    @org.junit.jupiter.api.BeforeEach void loginManager() { com.movie.ticket.TestIdentity.admin(); }
    @org.junit.jupiter.api.AfterEach void clearManager() { com.movie.ticket.TestIdentity.clear(); }

    @Mock private MovieMapper movieMapper;
    @Mock private ShowtimeMapper showtimeMapper;
    @Mock private TicketOrderMapper orderMapper;
    @Mock private HallMapper hallMapper;

    private SchedulingIntelligenceService service;
    private Movie movie;
    private Hall hall;

    @BeforeEach
    void setUp() {
        service = new SchedulingIntelligenceService(movieMapper, showtimeMapper, orderMapper, hallMapper,
                new SchedulingAiService(new DeepSeekClient(new com.fasterxml.jackson.databind.ObjectMapper())));
        movie = new Movie();
        movie.setId(1L);
        movie.setTitle("测试影片");
        movie.setRating(new BigDecimal("8.2"));
        movie.setReleaseDate(LocalDate.now().minusDays(8));
        movie.setStatus(1);

        hall = new Hall();
        hall.setId(10L);
        hall.setCinemaId(100L);
        hall.setName("一号厅");
        hall.setTotalSeats(100);
        hall.setStatus(1);
    }

    @Test
    void insightsContainWarningsRecommendationsHeatmapAndEvaluation() {
        Showtime historical = showtime(11L, LocalDateTime.now().minusDays(7), 1, 100, 75);
        Showtime future = showtime(12L, LocalDateTime.now().plusDays(2), 0, 100, 100);
        when(showtimeMapper.selectList(any())).thenReturn(List.of(future, historical));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(movieMapper.selectList(any())).thenReturn(List.of(movie));
        when(movieMapper.selectBatchIds(any())).thenReturn(List.of(movie));
        when(hallMapper.selectList(any())).thenReturn(List.of(hall));

        Map<String, Object> result = service.getInsights(100L);

        Map<?, ?> summary = (Map<?, ?>) result.get("summary");
        assertEquals(1, summary.get("historicalSamples"));
        assertTrue(result.containsKey("lowOccupancyShowtimes"));
        assertFalse(((List<?>) result.get("goldenTimeRecommendations")).isEmpty());
        assertFalse(((List<?>) result.get("frequencyRecommendations")).isEmpty());
        assertEquals(28, ((List<?>) result.get("heatmap")).size());
        assertTrue(((Map<?, ?>) result.get("evaluation")).containsKey("meanAbsoluteError"));
        assertEquals("可解释加权上座率模型 v1", ((Map<?, ?>) result.get("model")).get("name"));
        Map<?, ?> analysis = (Map<?, ?>) result.get("aiAnalysis");
        assertEquals("unconfigured", analysis.get("status"));
        assertEquals(1, ((Map<?, ?>) analysis.get("dataBasis")).get("historicalSamples"));
        assertEquals(null, ((Map<?, ?>) result.get("evaluation")).get("meanAbsoluteError"));
        Map<?, ?> frequency = (Map<?, ?>) ((List<?>) result.get("frequencyRecommendations")).getFirst();
        assertEquals(25.0, frequency.get("historicalRate"));
    }

    @Test
    void optimizationPreservesReservedSeatsAndAvoidsAdjacentDayConflicts() {
        movie.setDuration(120);
        LocalDate date = LocalDate.now().plusDays(2);
        Showtime reserved = showtime(20L, date.atTime(18, 0), 0, 100, 99);
        reserved.setEndTime(date.atTime(20, 0));
        Showtime nextDay = showtime(21L, date.plusDays(1).atStartOfDay(), 0, 100, 100);
        nextDay.setEndTime(date.plusDays(1).atTime(2, 0));
        when(showtimeMapper.selectList(any())).thenReturn(List.of(reserved, nextDay));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(movieMapper.selectList(any())).thenReturn(List.of(movie));
        when(movieMapper.selectBatchIds(any())).thenReturn(List.of(movie));
        when(hallMapper.selectList(any())).thenReturn(List.of(hall));
        SchedulingOptimizationRequest request = new SchedulingOptimizationRequest();
        request.setCinemaId(100L); request.setTargetDate(date); request.setMinDistinctMovies(1);
        request.setTargetShowtimesPerHall(3); request.setMaxShowtimesPerMovie(4);
        Map<String, Object> result = service.optimize(request);
        assertTrue(List.of("OPTIMAL", "FEASIBLE").contains(result.get("status")));
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("selectedShowtimes");
        assertEquals(3, items.size());
        assertTrue(items.stream().anyMatch(s -> Long.valueOf(20).equals(s.get("sourceShowtimeId")) && Boolean.TRUE.equals(s.get("locked"))));
        assertTrue(items.stream().allMatch(s -> !((LocalDateTime) s.get("endTime")).plusMinutes(20).isAfter(nextDay.getStartTime())));
    }

    @Test
    void predictionReturnsBoundedRateAndExplainableDrivers() {
        Showtime historical = showtime(11L, LocalDateTime.now().minusDays(7), 1, 100, 40);
        when(movieMapper.selectById(1L)).thenReturn(movie);
        when(hallMapper.selectById(10L)).thenReturn(hall);
        when(showtimeMapper.selectList(any())).thenReturn(List.of(historical));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(movieMapper.selectList(any())).thenReturn(List.of(movie));
        when(movieMapper.selectBatchIds(any())).thenReturn(List.of(movie));
        when(hallMapper.selectList(any())).thenReturn(List.of(hall));

        SchedulingPredictionRequest request = new SchedulingPredictionRequest();
        request.setMovieId(1L);
        request.setCinemaId(100L);
        request.setHallId(10L);
        request.setStartTime(LocalDateTime.now().plusDays(1).withHour(19));
        Map<String, Object> result = service.predict(request);

        double rate = ((Number) result.get("predictedRate")).doubleValue();
        assertTrue(rate >= 8 && rate <= 95);
        assertEquals("测试影片", result.get("movieTitle"));
        assertFalse(((List<?>) result.get("drivers")).isEmpty());
    }

    @Test
    void cpSatOptimizationProducesConflictFreeDiversePlanAndBaselines() {
        Movie second = movie(2L, "测试影片B", "7.8");
        Movie third = movie(3L, "测试影片C", "8.8");
        when(showtimeMapper.selectList(any())).thenReturn(List.of());
        when(movieMapper.selectList(any())).thenReturn(List.of(movie, second, third));
        when(hallMapper.selectList(any())).thenReturn(List.of(hall));

        SchedulingOptimizationRequest request = new SchedulingOptimizationRequest();
        request.setCinemaId(100L);
        request.setTargetDate(LocalDate.now().plusDays(1));
        request.setTargetShowtimesPerHall(2);
        request.setMaxShowtimesPerMovie(2);
        request.setMinDistinctMovies(2);
        request.setSolverSeconds(3);

        Map<String, Object> result = service.optimize(request);

        assertTrue(List.of("OPTIMAL", "FEASIBLE").contains(result.get("status")));
        List<?> selected = (List<?>) result.get("selectedShowtimes");
        assertEquals(2, selected.size());
        assertTrue(selected.stream().map(item -> (Map<?, ?>) item)
                .map(item -> LocalDateTime.parse(item.get("startTime").toString()))
                .anyMatch(time -> time.getHour() >= 18 && time.getHour() < 22));
        assertEquals(3, ((List<?>) result.get("comparisons")).size());
        Map<?, ?> optimized = (Map<?, ?>) ((List<?>) result.get("comparisons")).get(2);
        assertEquals(100.0, ((Number) optimized.get("constraintSatisfactionRate")).doubleValue());
        assertTrue(((Number) optimized.get("distinctMovies")).intValue() >= 2);
    }

    private Movie movie(long id, String title, String rating) {
        Movie value = new Movie();
        value.setId(id);
        value.setTitle(title);
        value.setRating(new BigDecimal(rating));
        value.setReleaseDate(LocalDate.now().minusDays(8));
        value.setDuration(120);
        value.setStatus(1);
        return value;
    }

    private Showtime showtime(long id, LocalDateTime startTime, int status, int totalSeats, int availableSeats) {
        Showtime showtime = new Showtime();
        showtime.setId(id);
        showtime.setMovieId(1L);
        showtime.setCinemaId(100L);
        showtime.setHallId(10L);
        showtime.setStartTime(startTime);
        showtime.setEndTime(startTime.plusHours(2));
        showtime.setStatus(status);
        showtime.setTotalSeats(totalSeats);
        showtime.setAvailableSeats(availableSeats);
        showtime.setBasePrice(new BigDecimal("45.00"));
        return showtime;
    }
}
