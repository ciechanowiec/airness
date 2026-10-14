package eu.ciechanowiec.airness.errorprone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AirnessPropertyOverEnvironmentTest {

    private static final String CHECK = "AirnessPropertyOverEnvironment";

    @ParameterizedTest
    @ValueSource(strings = {"HOME", "USERPROFILE", "USER", "USERNAME", "TEMP", "TMP", "TMPDIR"})
    void reportsAnEnvironmentVariableTheJvmPublishes(String name, @TempDir Path directory) {
        String source = """
            package example;

            class Reader {
                String read() {
                    return System.getenv("%s");
                }
            }
            """.formatted(name);
        assertEquals(List.of(5L), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void reportsANameHeldInAConstant(@TempDir Path directory) {
        String source = """
            package example;

            class Reader {
                private static final String NAME = "HOME";

                String read() {
                    return System.getenv(NAME);
                }
            }
            """;
        assertEquals(List.of(7L), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void acceptsVariablesWithoutAProperty(@TempDir Path directory) {
        String source = """
            package example;

            import java.util.Map;

            class Reader {
                String read(String name) {
                    Map<String, String> all = System.getenv();
                    return System.getenv("PATH") + System.getenv(name) + all.get("HOME");
                }
            }
            """;
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void acceptsAMethodOfTheSameNameOnAnotherType(@TempDir Path directory) {
        String source = """
            package example;

            class Reader {
                static String getenv(String name) {
                    return name;
                }

                String read() {
                    return getenv("HOME");
                }
            }
            """;
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }

    @Test
    void honorsASuppression(@TempDir Path directory) {
        String source = """
            package example;

            class Reader {
                @SuppressWarnings("AirnessPropertyOverEnvironment")
                String read() {
                    return System.getenv("HOME");
                }
            }
            """;
        assertEquals(List.of(), ErrorProneRule.findings(directory, CHECK, source));
    }
}
