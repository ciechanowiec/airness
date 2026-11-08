package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommaListTest {

    @Test
    void readsNoEntriesFromAnAbsentOrBlankParameter() {
        assertEquals(List.of(), CommaList.entries(null));
        assertEquals(List.of(), CommaList.entries("  "));
    }

    @Test
    void trimsEntriesAndDiscardsEmptySegments() {
        assertEquals(List.of("first", "second"), CommaList.entries(" first, , second, "));
    }
}
