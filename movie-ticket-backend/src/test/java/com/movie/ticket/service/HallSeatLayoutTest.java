package com.movie.ticket.service;

import com.movie.ticket.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class HallSeatLayoutTest {
    @Test void oldHallsRetainThePreviouslyDisplayedCentralAisle() {
        assertEquals(Set.of(2), HallSeatLayout.parse("[[1,0,1,1]]", 1, 4).aisleAfterCols());
        assertFalse(HallSeatLayout.parse("[[1,0,1,1]]", 1, 4).enabled()[0][1]);
        assertEquals(Set.of(2), HallSeatLayout.parse("", 1, 4).aisleAfterCols());
        assertTrue(HallSeatLayout.parse("[[1]]", 1, 1).aisleAfterCols().isEmpty());
    }

    @Test void explicitAislesAndNoAisleOverrideLegacyDefault() {
        assertEquals(Set.of(1, 3), HallSeatLayout.parse(
                "{\"seats\":[[1,1,1,1]],\"aisleAfterCols\":[1,3]}", 1, 4).aisleAfterCols());
        assertTrue(HallSeatLayout.parse(
                "{\"seats\":[[1,1,1,1]],\"aisleAfterCols\":[]}", 1, 4).aisleAfterCols().isEmpty());
    }

    @Test void malformedLayoutsNeverTurnIntoAvailableSeats() {
        for (String json : new String[]{"bad", "[[1]]", "[[1,1,null,1]]",
                "{\"seats\":[[1,1,1,1]],\"aisleAfterCols\":[-1]}",
                "{\"seats\":[[1,1,1,1]],\"aisleAfterCols\":[5]}",
                "{\"seats\":[[1,1,1,1]],\"aisleAfterCols\":[2,2]}",
                "{\"seats\":[[1,1,1,1]],\"aisleAfterCols\":[1.5]}"}) {
            assertThrows(BusinessException.class, () -> HallSeatLayout.parse(json, 1, 4), json);
        }
    }

    @Test void acceptsBothOuterEdgesIncludingASingleColumnHall() {
        assertEquals(Set.of(0, 2, 4), HallSeatLayout.parse(
                "{\"seats\":[[1,1,1,1]],\"aisleAfterCols\":[0,2,4]}", 1, 4).aisleAfterCols());
        assertEquals(Set.of(0, 1), HallSeatLayout.parse(
                "{\"seats\":[[1]],\"aisleAfterCols\":[0,1]}", 1, 1).aisleAfterCols());
    }
}
