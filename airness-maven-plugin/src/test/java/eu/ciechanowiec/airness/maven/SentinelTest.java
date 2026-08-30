package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SentinelTest {

    @Test
    void readsNoEntriesFromAnAbsentOrBlankParameter() {
        assertEquals(List.of(), Sentinel.optional(null));
        assertEquals(List.of(), Sentinel.optional("  "));
    }

    @Test
    void trimsEntriesAndDiscardsEmptySegments() {
        assertEquals(List.of("first", "second"), Sentinel.optional(" first, , second, "));
    }
}
