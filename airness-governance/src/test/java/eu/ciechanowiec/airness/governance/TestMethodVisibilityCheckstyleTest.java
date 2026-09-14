package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestMethodVisibilityCheckstyleTest {

    private static final String RULE = "AirnessTestMethodsAreNotPublic";

    @Test
    void reportsAPublicTestMethod(@TempDir Path directory) {
        String source = """
            import org.junit.jupiter.api.Test;
            class Sample {
                @Test
                public void readsWhatWasWritten() {}
            }
            """;

        assertEquals(1, findings(directory, source), "public states a reach the engine does not use");
    }

    @Test
    void reportsAPrivateTestMethod(@TempDir Path directory) {
        String source = """
            import org.junit.jupiter.api.Test;
            class Sample {
                @Test
                private void readsWhatWasWritten() {}
            }
            """;

        assertEquals(1, findings(directory, source), "a private test is one the engine cannot call at all");
    }

    @Test
    void acceptsAPackagePrivateTestMethod(@TempDir Path directory) {
        String source = """
            import org.junit.jupiter.api.Test;
            class Sample {
                @Test
                void readsWhatWasWritten() {}
            }
            """;

        assertEquals(0, findings(directory, source), "package-private is the reach a test method has");
    }

    @Test
    void leavesAMethodWithoutATestAnnotationAlone(@TempDir Path directory) {
        String source = """
            import org.junit.jupiter.api.Test;
            class Sample {
                public void helps() {}
            }
            """;

        assertEquals(0, findings(directory, source), "the rule answers to the annotations it is given");
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE);
    }
}
