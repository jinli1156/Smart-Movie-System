package com.movie.ticket.service;

import com.movie.ticket.dto.*;
import com.movie.ticket.entity.*;
import com.movie.ticket.mapper.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SeatRecommendationServiceTest {
    private final ShowtimeMapper showtimes = mock(ShowtimeMapper.class);
    private final HallMapper halls = mock(HallMapper.class);
    private final TicketOrderMapper orders = mock(TicketOrderMapper.class);
    private final SeatAiRecommendationService ai = mock(SeatAiRecommendationService.class);
    private final SeatRecommendationService service = new SeatRecommendationService(showtimes, halls, orders, ai);

    private SeatRecommendationRequest setup() {
        var showtime = new Showtime(); showtime.setHallId(1L); showtime.setBasePrice(BigDecimal.TEN);
        var hall = new Hall(); hall.setTotalRows(6); hall.setTotalCols(8);
        when(showtimes.selectById(1L)).thenReturn(showtime);
        when(halls.selectById(1L)).thenReturn(hall);
        when(orders.selectList(any())).thenReturn(List.of());
        var request = new SeatRecommendationRequest();
        request.setPeople(2); request.setPreference("QUIET"); request.setLimit(3);
        return request;
    }

    @Test void quickPreferenceInvokesModelWithoutNaturalLanguage() {
        var request = setup();
        when(ai.analyze(eq("靠后安静"), eq(2), eq(6), eq(8), anyList())).thenAnswer(call -> {
            List<com.movie.ticket.vo.SeatRecommendationVO> candidates = call.getArgument(4);
            var selected = candidates.getLast();
            return Optional.of(new SeatAiRecommendationService.SeatAiAnalysis(SeatPreference.QUIET,
                    "适合安静观影", List.of(new SeatAiRecommendationService.RankedReason(selected.getSeatLabel(), "后排更安静"))));
        });
        assertEquals("model", service.recommendSmart(1L, request).getAnalysisSource());
        verify(ai).analyze(eq("靠后安静"), eq(2), eq(6), eq(8), anyList());
    }

    @Test void inventedSeatsCannotBeMarkedAsModelRecommendation() {
        var request = setup();
        when(ai.analyze(anyString(), anyInt(), anyInt(), anyInt(), anyList())).thenReturn(Optional.of(
                new SeatAiRecommendationService.SeatAiAnalysis(SeatPreference.QUIET, "虚构座位",
                        List.of(new SeatAiRecommendationService.RankedReason("999排99座", "不存在")))));
        var result = service.recommendSmart(1L, request);
        assertEquals("algorithm", result.getAnalysisSource());
        assertNull(result.getModel());
        assertFalse(result.getRecommendations().isEmpty());
    }

    @Test void seatsOccupiedWhileModelRunsAreRemoved() {
        var request = setup();
        Set<String> newlyOccupied = new HashSet<>();
        when(ai.analyze(anyString(), anyInt(), anyInt(), anyInt(), anyList())).thenAnswer(call -> {
            List<com.movie.ticket.vo.SeatRecommendationVO> candidates = call.getArgument(4);
            var selected = candidates.getFirst(); newlyOccupied.addAll(selected.getSeats());
            var order = new TicketOrder(); order.setSeats(String.join(",", selected.getSeats()));
            when(orders.selectList(any())).thenReturn(List.of(order));
            return Optional.of(new SeatAiRecommendationService.SeatAiAnalysis(SeatPreference.QUIET, "安静后排",
                    List.of(new SeatAiRecommendationService.RankedReason(selected.getSeatLabel(), "适合偏好"))));
        });
        var result = service.recommendSmart(1L, request);
        assertFalse(newlyOccupied.isEmpty());
        assertTrue(result.getRecommendations().stream().flatMap(item -> item.getSeats().stream()).noneMatch(newlyOccupied::contains));
        assertEquals("algorithm", result.getAnalysisSource());
    }

    @Test void smartAndBasicRecommendationsDoNotOfferSeparatedCoupleSeats() {
        var request = setup(); request.setPreference("COUPLE");
        var hall = halls.selectById(1L); hall.setTotalRows(1); hall.setTotalCols(4);
        hall.setSeatLayout("{\"seats\":[[0,1,1,0]],\"aisleAfterCols\":[2]}");
        assertTrue(service.recommend(1L, 2, "COUPLE", 3).isEmpty());
        assertTrue(service.recommendSmart(1L, request).getRecommendations().isEmpty());
        verifyNoInteractions(ai);
    }

    @Test void modelCannotInsertAcrossAisleSeatsAndRefillRemainsWithinSections() {
        var request = setup(); request.setPreference("COUPLE");
        var hall = halls.selectById(1L); hall.setTotalRows(1); hall.setTotalCols(8);
        hall.setSeatLayout("{\"seats\":[[1,1,1,1,1,1,1,1]],\"aisleAfterCols\":[2,4,6]}");
        when(ai.analyze(anyString(), anyInt(), anyInt(), anyInt(), anyList())).thenAnswer(call -> {
            List<com.movie.ticket.vo.SeatRecommendationVO> candidates = call.getArgument(4);
            candidates.forEach(item -> assertEquals(column(item.getSeats().getFirst()) / 2,
                    (column(item.getSeats().getLast()) - 1) / 2));
            var selected = candidates.getFirst();
            var order = new TicketOrder(); order.setSeats(String.join(",", selected.getSeats()));
            when(orders.selectList(any())).thenReturn(List.of(order));
            return Optional.of(new SeatAiRecommendationService.SeatAiAnalysis(SeatPreference.COUPLE, "双人",
                    List.of(new SeatAiRecommendationService.RankedReason("1排 2-3座", "跨过道虚构候选"),
                            new SeatAiRecommendationService.RankedReason(selected.getSeatLabel(), "合法候选"))));
        });
        var result = service.recommendSmart(1L, request);
        assertEquals(3, result.getRecommendations().size());
        result.getRecommendations().forEach(item -> assertEquals(column(item.getSeats().getFirst()) / 2,
                (column(item.getSeats().getLast()) - 1) / 2));
    }

    private int column(String seat) {
        return Integer.parseInt(seat.substring(seat.indexOf('排') + 1, seat.indexOf('座')));
    }
}
