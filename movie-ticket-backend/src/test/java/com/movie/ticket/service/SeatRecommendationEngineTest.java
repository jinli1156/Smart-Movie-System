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
