package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoopCountersCheckstyleTest {

    private static final String STEP = "AirnessLoopStepsInItsHeader";
    private static final String BOUND = "AirnessLoopBoundIsAnOrdering";

    @Test
    void readsOnlyAStepOfTheDeclaredCounterAsTheLastStatement(@TempDir Path directory) {
        assertEquals(1, this.findings(directory, STEP, "for (int i = 0; i < 9;) { m(); i--; }"));
        assertEquals(0, this.findings(directory, STEP, "for (int i = 0; i < 9;) { m(); if (i > 3) { i++; } }"));
        assertEquals(0, this.findings(directory, STEP, "for (int i = 0; i < 9; i++) { m(); }"));
        assertEquals(0, this.findings(directory, STEP, "for (int i = 0; i < 9;) m();"));
        assertEquals(0, this.findings(directory, STEP, "for (int i = 0; i < 9;) { m(); this.f++; }"));
        assertEquals(0, this.findings(directory, STEP, "for (int i = 0; i < 9;) { m(); a[0]++; }"));
        assertEquals(0, this.findings(directory, STEP, "for (int i = 0; i < 9;) { }"));
        assertEquals(0, this.findings(directory, STEP, "for (;;) { m(); f++; }"));
    }

    @Test
    void readsOnlyAnInequalityOfAnInitialisedCounterSteppedByOne(@TempDir Path directory) {
        assertEquals(1, this.findings(directory, BOUND, "for (f = 0; f != 9; ++f) { m(); }"));
        assertEquals(0, this.findings(directory, BOUND, "for (this.f = 0; this.f != 9; this.f++) { m(); }"));
        assertEquals(0, this.findings(directory, BOUND, "for (int i = 0; i < 9; i++) { m(); }"));
        assertEquals(0, this.findings(directory, BOUND, "for (int i = 0; i != 9; m()) { m(); }"));
        assertEquals(0, this.findings(directory, BOUND, "for (m(); f != 9; f++) { m(); }"));
        assertEquals(0, this.findings(directory, BOUND, "for (int i = 0; ; i++) { m(); }"));
        assertEquals(0, this.findings(directory, BOUND, "for (int i = 0; i != 9;) { i++; }"));
        assertEquals(0, this.findings(directory, BOUND, "for (int i = 0; a[0] != 9; i++) { m(); }"));
    }

    private int findings(Path directory, String rule, String loop) {
        String source = """
            class Sample {
                private int f;
                private final int[] a = new int[1];

                void m() {
                    %s
                }
            }
            """.formatted(loop);
        return CheckstyleRule.findings(directory, source, rule);
    }
}
