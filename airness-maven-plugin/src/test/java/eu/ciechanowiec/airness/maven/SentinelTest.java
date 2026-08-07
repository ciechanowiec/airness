package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.Justification;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentinelTest {

    @Test
    @SuppressWarnings("NullAway")
    @Justification("The null value is the behavior under test, so the deliberate contract violation is required.")
    void readsNoEntriesFromAnAbsentOrBlankParameter() {
        assertEquals(List.of(), Sentinel.optional(null));
        assertEquals(List.of(), Sentinel.optional("  "));
    }

    @Test
    void trimsEntriesAndDiscardsEmptySegments() {
        assertEquals(List.of("first", "second"), Sentinel.optional(" first, , second, "));
    }
}
