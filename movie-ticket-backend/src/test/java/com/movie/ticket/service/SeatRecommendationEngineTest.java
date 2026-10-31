package com.movie.ticket.service;

import com.movie.ticket.dto.SeatPreference;
import com.movie.ticket.vo.SeatRecommendationVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatRecommendationEngineTest {

    private final SeatRecommendationEngine engine = new SeatRecommendationEngine();

    @Test
    void findsACompleteContinuousBlockForPartySize() {
        List<SeatRecommendationVO> result = engine.recommend(
                10, 12, allEnabled(10, 12), Set.of("7排6座"), 3,
                SeatPreference.CENTER, 3, new BigDecimal("45.00"));

        assertEquals(3, result.size());
        result.forEach(recommendation -> {
            assertEquals(3, recommendation.getSeats().size());
            assertFalse(recommendation.getSeats().contains("7排6座"));
            int first = column(recommendation.getSeats().getFirst());
            int last = column(recommendation.getSeats().getLast());
            assertEquals(2, last - first);
        });
    }

    @Test
    void penalizesASelectionThatWouldLeaveAnOrphanSeat() {
        List<SeatRecommendationVO> result = engine.recommend(
                1, 6, allEnabled(1, 6), Set.of("1排6座"), 2,
                SeatPreference.CENTER, 1, BigDecimal.TEN);

        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().getSingleSeatPenalty());
    }

    @Test
    void soldRecommendationIsReplacedByTheNextAvailableBlock() {
        List<SeatRecommendationVO> first = engine.recommend(
                8, 10, allEnabled(8, 10), Set.of(), 2,
                SeatPreference.CENTER, 1, BigDecimal.TEN);
        Set<String> nowOccupied = new HashSet<>(first.getFirst().getSeats());

        List<SeatRecommendationVO> refreshed = engine.recommend(
                8, 10, allEnabled(8, 10), nowOccupied, 2,
                SeatPreference.CENTER, 1, BigDecimal.TEN);

        assertEquals(1, refreshed.size());
        assertNotEquals(first.getFirst().getSeats(), refreshed.getFirst().getSeats());
        assertTrue(refreshed.getFirst().getSeats().stream().noneMatch(nowOccupied::contains));
    }

    @Test
    void everyPreferenceAndPartyStaysInsideOneAisleSection() {
        for (SeatPreference preference : SeatPreference.values()) {
            for (int people = 2; people <= 5; people++) {
                var result = engine.recommend(3, 12, allEnabled(3, 12), Set.of("2排5座"), people,
                        preference, 5, BigDecimal.TEN, Set.of(3, 8));
                assertFalse(result.isEmpty());
                result.forEach(item -> {
                    int start = column(item.getSeats().getFirst());
                    int end = column(item.getSeats().getLast());
                    assertFalse(start <= 3 && end > 3);
                    assertFalse(start <= 8 && end > 8);
                    assertFalse(item.getSeats().contains("2排5座"));
                });
            }
        }
    }

    @Test
    void coupleCannotUseTheOnlyTwoSeatsAcrossAnAisle() {
        assertTrue(engine.recommend(1, 4, allEnabled(1, 4), Set.of("1排1座", "1排4座"), 2,
                SeatPreference.COUPLE, 3, BigDecimal.TEN, Set.of(2)).isEmpty());
        assertEquals(List.of("1排2座", "1排3座"), engine.recommend(1, 4, allEnabled(1, 4),
                Set.of("1排1座", "1排4座"), 2, SeatPreference.COUPLE, 3, BigDecimal.TEN, Set.of())
                .getFirst().getSeats());
    }

    @Test
    void aisleBoundariesAreUsedForOrphansContinuityAndAccess() {
        var singles = engine.recommend(1, 4, allEnabled(1, 4), Set.of(), 1,
                SeatPreference.CENTER, 5, BigDecimal.TEN, Set.of(2));
        assertEquals(18, singles.stream().filter(item -> item.getSeats().equals(List.of("1排1座")))
                .findFirst().orElseThrow().getSingleSeatPenalty());
        var pairs = engine.recommend(1, 4, allEnabled(1, 4), Set.of(), 2,
                SeatPreference.CENTER, 5, BigDecimal.TEN, Set.of(2));
        pairs.forEach(item -> {
            assertEquals(100, item.getScores().getContinuity());
            assertEquals(100, item.getScores().getAccessConvenience());
        });
    }

    @Test
    void outsideAislesDoNotSplitSeatsOrAddOrphanPenalties() {
        var result = engine.recommend(1, 4, allEnabled(1, 4), Set.of(), 4,
                SeatPreference.COUPLE, 3, BigDecimal.TEN, Set.of(0, 4));
        assertEquals(1, result.size());
        assertEquals(List.of("1排1座", "1排2座", "1排3座", "1排4座"), result.getFirst().getSeats());
        assertEquals(0, result.getFirst().getSingleSeatPenalty());
        assertEquals(100, result.getFirst().getScores().getAccessConvenience());
        assertTrue(engine.recommend(1, 4, allEnabled(1, 4), Set.of("1排1座", "1排4座"), 2,
                SeatPreference.COUPLE, 3, BigDecimal.TEN, Set.of(0, 2, 4)).isEmpty());
    }

    private boolean[][] allEnabled(int rows, int cols) {
        boolean[][] enabled = new boolean[rows][cols];
        for (int row = 0; row < rows; row++) {
            java.util.Arrays.fill(enabled[row], true);
        }
        return enabled;
    }

    private int column(String seat) {
        return Integer.parseInt(seat.substring(seat.indexOf('排') + 1, seat.indexOf('座')));
    }
}
