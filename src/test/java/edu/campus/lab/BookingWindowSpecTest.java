package edu.campus.lab;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingWindowSpecTest {
    @ParameterizedTest(name = "{0}: room [{1},{2}], request [{3},{4}]")
    @CsvSource({
        "A1, 2, 7, 2, 2, true",
        "A2, 2, 7, 7, 7, true",
        "A3, 2, 7, 2, 7, true",
        "A4, 2, 7, 3, 5, true",
        "A5, 2, 7, 1, 2, false",
        "A6, 2, 7, 7, 8, false",
        "A7, 2, 7, 5, 4, false",
        "A8, 6, 6, 6, 6, true"
    })
    void matchesBookingSpec(String id, int low, int high, int from, int to, boolean accepted) {
        if (accepted) {
            assertDoesNotThrow(() -> Schedule.periods(from, to, low, high), id);
        } else {
            Rule rejection = assertThrows(Rule.class, () -> Schedule.periods(from, to, low, high), id);
            assertEquals(400, rejection.status, id);
        }
    }
}
