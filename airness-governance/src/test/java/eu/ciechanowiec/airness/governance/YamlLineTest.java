package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * A line is read for where its node starts, whether it opens a block, and what it opens around the lines after
 * it.
 */
class YamlLineTest {

    @Test
    void readsAKeyThatOpensABlock() {
        assertEquals(new YamlLine(1, 2, 2, true, YamlLine.NONE, 0), YamlLine.of("  key: # note", 1));
    }

    @Test
    void readsASequenceEntryFromWhereItsNodeStarts() {
        assertAll(
            () -> assertEquals(new YamlLine(2, 2, 4, false, YamlLine.NONE, 0), YamlLine.of("  - name: x", 2)),
            () -> assertEquals(new YamlLine(3, 2, 4, true, YamlLine.NONE, 0), YamlLine.of("  - with:", 3)),
            () -> assertEquals(new YamlLine(4, 2, 2, true, YamlLine.NONE, 0), YamlLine.of("  -", 4)),
            () -> assertEquals(new YamlLine(5, 0, 2, true, YamlLine.NONE, 0), YamlLine.of("- - &anchor", 5))
        );
    }

    @Test
    void readsTheColumnABlockScalarEndsAt() {
        assertAll(
            () -> assertEquals(new YamlLine(1, 4, 6, false, 6, 0), YamlLine.of("    - run: |", 1)),
            () -> assertEquals(new YamlLine(2, 4, 6, false, 4, 0), YamlLine.of("    - >-", 2)),
            () -> assertEquals(new YamlLine(3, 0, 0, false, 0, 0), YamlLine.of("text: !!str |2", 3))
        );
    }

    @Test
    void readsTheFlowCollectionsALineLeavesOpen() {
        assertAll(
            () -> assertEquals(new YamlLine(1, 0, 0, false, YamlLine.NONE, 1), YamlLine.of("args: [a,", 1)),
            () -> assertEquals(new YamlLine(2, 0, 2, false, YamlLine.NONE, 2), YamlLine.of("- [{a: 1,", 2)),
            () -> assertEquals(new YamlLine(3, 0, 0, false, YamlLine.NONE, 0), YamlLine.of("args: [a]", 3))
        );
    }
}
