package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Finding;
import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Fixture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The variants of the packaged Checkstyle rules, decided in process against the checked-in configuration.
 *
 * <p>Each of these used to cost a consumer build in {@code airness-it}, which proved nothing a rule
 * reading one file could not prove here. The integration suite keeps the wiring: one batched consumer
 * shows that the installed parent selects these rules, at their files, and that their findings fail a
 * build. Which input a rule accepts and which it refuses is a question about the rule.
 */
class CheckstyleVariantTest {

    private static final String PACKAGE = "example";
    private static final String PACKAGE_INFO = """
        /**
         * Fixtures.
         */
        package example;
        """;
    private static final int DOT_LINE = 14;
    private static final int NAMED_LINE = 15;
    private static final int WHOLE_LINE = 20;
    private static final String DISTANCE = "VariableDeclarationUsageDistance";
    private static final String FINAL_ADVICE = "making that variable final";
    private static final String FINAL_CLASS = "RequireFinalClass";
    private static final String REQUIREDNESS = "AirnessSpringWebParameterDeclaresRequiredness";
    private static final String DISTANT = """
        package example;

        /**
         * Holds a value across several operations to exercise the distance diagnostic.
         */
        final class Reading {

            String before(StringBuilder content) {
                %sString remembered = content.toString();
                content.append('a');
                content.append('b');
                content.append('c');
                content.append('d');
                return remembered;
            }
        }
        """;
    private static final String JOINED = """
        package example;

        import java.util.List;

        /**
         * Joins the supplied values into one readable line.
         */
        final class JoinedValues {

            String read(List<String> values) {
                return String.join(
                    ",", values
                )%s.strip();
            }
        }
        """;

    @TempDir
    private Path directory;

    @Test
    void reportsADistantLocalWithoutAdvisingFinal() {
        List<Finding> findings = this.inspect("Reading.java", DISTANT.formatted(""), false);
        assertTrue(
            findings.stream().anyMatch(finding -> at(finding, DISTANCE, 9) && finding.message().contains("remembered")),
            () -> "the distant local is reported at its declaration, but reported " + findings
        );
        assertTrue(findings.stream().noneMatch(finding -> finding.message().contains(FINAL_ADVICE)));
    }

    @Test
    void keepsFinalLocalsForbiddenAndStillMeasuresTheirDistance() {
        List<Finding> findings = this.inspect("Reading.java", DISTANT.formatted("final "), false);
        assertTrue(
            findings.stream().anyMatch(
                finding -> finding.message().contains("Local variables must not be declared final")
            ),
            () -> "a final local is refused in its own words, but reported " + findings
        );
        assertTrue(
            findings.stream().anyMatch(finding -> at(finding, DISTANCE, 9) && finding.message().contains("remembered")),
            () -> "final does not exempt a distant local from its check, but reported " + findings
        );
        assertTrue(findings.stream().noneMatch(finding -> finding.message().contains(FINAL_ADVICE)));
    }

    @Test
    void passesALocalDeclaredBesideItsUse() {
        String declaration = "        String remembered = content.toString();\n";
        String use = "        return remembered;";
        String nearby = DISTANT.formatted("").replace(declaration, "").replace(use, declaration + use);
        List<Finding> findings = this.inspect("Reading.java", nearby, false);
        assertEquals(List.of(), findings, "a local declared beside its use passes the whole configuration");
    }

    @Test
    void refusesADetachedChainedDotAndPassesTheJoinedOne() {
        List<Finding> detached = this.inspect("JoinedValues.java", JOINED.formatted("\n        "), false);
        assertTrue(
            detached.stream().anyMatch(
                finding -> at(finding, "AirnessDetachedChainDot", DOT_LINE)
                    && finding.message().contains("Join this dot")
            ),
            () -> "the detached dot is reported with its repair, but reported " + detached
        );
        List<Finding> joined = this.inspect("JoinedValues.java", JOINED.formatted(""), false);
        assertEquals(List.of(), joined, "joining the dot to the closing parenthesis passes the whole configuration");
    }

    @Test
    void keepsAnOrdinarySpringComponentFinal() {
        String component = """
            package example;

            import org.springframework.stereotype.Component;

            /**
             * A component the container never subclasses.
             */
            @Component
            public class Catalogue {
            }
            """;
        List<Finding> findings = this.inspect("Catalogue.java", component, true);
        assertTrue(
            findings.stream().anyMatch(finding -> at(finding, FINAL_CLASS, 8)),
            () -> "the repository exemption does not reach an ordinary component, but reported " + findings
        );
    }

    @Test
    void asksANamedRequestMapForItsRequiredness() {
        String parameters = """
            package example;

            import java.util.Map;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestParam;
            import org.springframework.web.bind.annotation.RestController;

            /**
             * Named and whole-request maps side by side.
             */
            @RestController
            public final class Parameters {

                @GetMapping("/named")
                public String named(@RequestParam(name = "payload") Map<String, String> payload) {
                    return payload.toString();
                }

                @GetMapping("/whole")
                public String whole(@RequestParam Map<String, String> values) {
                    return values.toString();
                }
            }
            """;
        List<Finding> findings = this.inspect("Parameters.java", parameters, true);
        assertTrue(
            findings.stream().anyMatch(finding -> at(finding, REQUIREDNESS, NAMED_LINE)),
            () -> "a named map is an individual value and states its requiredness, but reported " + findings
        );
        assertTrue(
            findings.stream().noneMatch(finding -> at(finding, REQUIREDNESS, WHOLE_LINE)),
            () -> "a whole-request map carries no individual requiredness, but reported " + findings
        );
    }

    // The fixture sits in the directory its package names, beside a package-info, so the findings left are
    // the ones its own source earns rather than the ones a file outside any package always would.
    @SneakyThrows
    private List<Finding> inspect(String file, String source, boolean spring) {
        Path pack = Files.createDirectories(this.directory.resolve(PACKAGE));
        Files.writeString(pack.resolve("package-info.java"), PACKAGE_INFO);
        return CheckstyleConfigurationTest.inspect(
            this.directory, new Fixture(PACKAGE + "/" + file, source, "", 0), spring
        );
    }

    private static boolean at(Finding finding, String rule, int line) {
        return finding.line() == line && (finding.is(rule) || finding.message().contains(rule));
    }
}
