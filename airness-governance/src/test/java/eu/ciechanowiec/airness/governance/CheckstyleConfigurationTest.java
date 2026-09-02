package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckstyleConfigurationTest {

    private static final String CONFIGURATION
        = "airness-config/src/main/resources/eu/ciechanowiec/airness/static_code_analysis/checkstyle.xml";
    private static final String SPRING_RELAXED = "airness.checkstyle.spring.relaxed";
    private static final String SPRING_SUPPRESSED = "airness.checkstyle.spring.suppressed";
    private static final List<Fixture> SEMANTIC_FIXTURES = List.of(
        new Fixture(
            "Qualified.java",
            """
                package example;
                final class Qualified {
                    String value() {
                        return java.util.Locale.ROOT.toString();
                    }
                }
                """,
            "Unnecessary fully-qualified type name",
            4
        ),
        new Fixture(
            "Local.java",
            """
                package example;
                final class Local {
                    String value() {
                        var value = "text";
                        return value;
                    }
                }
                """,
            "RequireWrittenType",
            4
        ),
        new Fixture(
            "Loop.java",
            """
                package example;
                final class Loop {
                    void read(java.util.List<String> values) {
                        for (var value : values) {
                            IO.println(value);
                        }
                    }
                }
                """,
            "RequireWrittenType",
            4
        ),
        new Fixture(
            "Resource.java",
            """
                package example;
                final class Resource {
                    void read(java.io.InputStream input) throws java.io.IOException {
                        try (var resource = input) {
                            resource.read();
                        }
                    }
                }
                """,
            "RequireWrittenType",
            4
        ),
        new Fixture(
            "Lambda.java",
            """
                package example;
                final class Lambda {
                    java.util.function.Function<String, String> value() {
                        return (var value) -> value;
                    }
                }
                """,
            "RequireWrittenType",
            4
        ),
        new Fixture(
            "PathUtils.java",
            """
                package example;
                final class PathUtils {
                }
                """,
            "TypeName",
            2
        ),
        new Fixture(
            "Quantity.java",
            """
                package example;
                final class Quantity {
                    int value() {
                        return 50;
                    }
                }
                """,
            "MagicNumber",
            4
        ),
        new Fixture(
            "Mutable.java",
            """
                package example;
                final class Mutable {
                    private String value;
                }
                """,
            "RequireFinalField",
            3
        ),
        new Fixture(
            "UnusedLambda.java",
            """
                package example;
                final class UnusedLambda {
                    Runnable value(String ignored) {
                        return () -> IO.println(ignored);
                    }
                    java.util.function.Function<String, String> unused() {
                        return value -> "fixed";
                    }
                }
                """,
            "UnusedLambdaParameterShouldBeUnnamed",
            7
        ),
        new Fixture(
            "Constructors.java",
            """
                package example;
                final class Constructors {
                    Constructors(String first, String second) {
                    }
                    Constructors(String first) {
                    }
                }
                """,
            "ConstructorsDeclarationGrouping",
            5
        ),
        new Fixture(
            "Wide.java",
            """
                package example;
                interface Wide {
                    void carry(String first, String second, String third, String fourth, String fifth);
                }
                """,
            "ParameterNumber",
            3
        ),
        new Fixture(
            "Branched.java",
            """
                package example;
                final class Branched {
                    int count(int value) {
                        int total = value;
                        if (value > 1) { total++; }
                        if (value > 2) { total++; }
                        if (value > 3) { total++; }
                        if (value > 4) { total++; }
                        if (value > 5) { total++; }
                        if (value > 6) { total++; }
                        if (value > 7) { total++; }
                        if (value > 8) { total++; }
                        return total;
                    }
                }
                """,
            "CyclomaticComplexity",
            3
        )
    );

    @Test
    @SneakyThrows
    void reportsEveryConsumerSemanticFixtureAtItsLocation(@TempDir Path directory) {
        for (Fixture fixture : SEMANTIC_FIXTURES) {
            List<Finding> findings = inspect(directory, fixture, false);
            assertTrue(
                findings.stream().anyMatch(finding -> finding.matches(fixture)),
                () -> "%s:%d must report %s, but reported %s"
                    .formatted(fixture.file(), fixture.line(), fixture.rule(), findings)
            );
        }
    }

    @Test
    @SneakyThrows
    void reportsEveryAirnessAstBanWithoutReadingNearMissText(@TempDir Path directory) {
        Fixture banned = new Fixture(
            "Generated.java",
            """
                package example;
                import lombok.Builder;
                import lombok.Data;
                import lombok.Locked;
                import lombok.NonNull;
                import lombok.Setter;
                import lombok.Synchronized;
                import lombok.With;
                import lombok.experimental.SuperBuilder;
                @Data
                @Setter
                @Builder
                @With
                @SuperBuilder
                final class Generated {
                    @Builder.Default private String named = "x";
                    @NonNull private String required;
                    @Inject(injectionStrategy = DefaultInjectionStrategy.OPTIONAL) private String injected;
                    @Synchronized void locks() { }
                    @Locked.Read void alsoLocks() { }
                    @PostConstruct void initialises() { }
                }
                """,
            "AirnessObjectIsWholeWhenConstructed",
            10
        );
        List<Finding> findings = inspect(directory, banned, false);
        List<String> expected = List.of(
            "AirnessObjectIsWholeWhenConstructed",
            "AirnessConstructorStatesWhatAnInstanceNeeds",
            "AirnessNullnessIsStatedOnce",
            "AirnessLockIsVisible",
            "AirnessObjectNeedsNoSecondInitialisation",
            "AirnessInjectionIsNotOptional"
        );
        assertTrue(
            expected.stream().allMatch(rule -> findings.stream().anyMatch(finding -> finding.is(rule))),
            () -> "every Airness AST ban must report, but reported " + findings
        );
    }

    @Test
    @SneakyThrows
    void reportsEveryNullAstShapeAndLeavesTextAlone(@TempDir Path directory) {
        Fixture absence = new Fixture(
            "Absence.java",
            """
                package example;
                final class Absence {
                    Object tests(Object left, Object right) {
                        boolean first = left == null;
                        boolean second = left != null;
                        boolean third = null == right;
                        boolean fourth = null != right;
                        if (first || second || third || fourth) {
                            return null;
                        }
                        return (null);
                    }
                }
                """,
            "AirnessAbsenceIsModelled",
            4
        );
        List<Finding> findings = inspect(directory, absence, false);
        long comparisons = findings.stream().filter(finding -> finding.is("AirnessAbsenceIsModelled")).count();
        long returns = findings.stream().filter(finding -> finding.is("AirnessNullIsNeverReturned")).count();
        assertEquals(4, comparisons, "all four null comparison directions are reported");
        assertEquals(2, returns, "bare and parenthesised null returns are reported");
    }

    @Test
    @SneakyThrows
    void appliesOrdinaryTestAndSpringSpecificScopes(@TempDir Path directory) {
        Fixture testSource = new Fixture(
            "src/test/java/example/UndocumentedTest.java",
            "package example;\npublic class UndocumentedTest {\n}\n",
            "MissingJavadocType",
            2
        );
        Fixture entity = new Fixture(
            "Entity.java",
            "package example;\n@Entity\nfinal class Entity {\n    private String value;\n}\n",
            "RequireFinalField",
            4
        );
        List<Finding> testFindings = inspect(directory, testSource, false);
        List<Finding> ordinary = inspect(directory, entity, false);
        List<Finding> spring = inspect(directory, entity, true);
        assertTrue(testFindings.stream().noneMatch(finding -> finding.is("MissingJavadocType")));
        assertTrue(ordinary.stream().anyMatch(finding -> finding.is("RequireFinalField")));
        assertTrue(spring.stream().noneMatch(finding -> finding.is("RequireFinalField")));
    }

    @Test
    @SneakyThrows
    void loadsTheCheckedInConfigurationAndCustomQuliceChecks(@TempDir Path directory) {
        Fixture fixture = new Fixture(
            "Custom.java",
            "package example;\nfinal class Custom {\n    private Custom() {\n    }\n}\n",
            "EmptyLineBeforeFirstMember",
            3
        );
        List<Finding> findings = inspect(directory, fixture, false);
        assertTrue(
            findings.stream().anyMatch(finding -> finding.is("EmptyLineBeforeFirstMember")),
            "the QuLice check from the plugin classpath is configured and executes"
        );
    }

    @SneakyThrows
    private static List<Finding> inspect(Path directory, Fixture fixture, boolean spring) {
        Path source = directory.resolve(fixture.file());
        Files.createDirectories(source.getParent());
        Files.writeString(source, fixture.source());
        EventCollector collector = new EventCollector();
        Checker checker = new Checker();
        checker.setModuleClassLoader(Thread.currentThread().getContextClassLoader());
        checker.addListener(collector);
        checker.configure(configuration(spring));
        checker.process(List.of(source.toFile()));
        checker.destroy();
        assertTrue(collector.exceptions().isEmpty(), () -> "Checkstyle exceptions: " + collector.exceptions());
        return collector.findings();
    }

    @SneakyThrows
    private static Configuration configuration(boolean spring) {
        Path resource = SelfModules.repository().resolve(CONFIGURATION);
        assertTrue(Files.isRegularFile(resource), "the checked-in Checkstyle configuration exists");
        Properties properties = new Properties();
        properties.setProperty(SPRING_RELAXED, spring ? ".*" : "^$");
        properties.setProperty(SPRING_SUPPRESSED, spring ? "^$" : ".*");
        return ConfigurationLoader.loadConfiguration(
            resource.toUri().toString(),
            new PropertiesExpander(properties)
        );
    }

    private record Fixture(String file, String source, String rule, int line) {
    }

    private record Finding(String rule, int line, String message) {

        boolean is(String expected) {
            return this.rule.equals(expected);
        }

        boolean matches(Fixture fixture) {
            return this.line == fixture.line()
                && (this.rule.equals(fixture.rule()) || this.message.contains(fixture.rule()));
        }
    }

    private static final class EventCollector implements AuditListener {

        private final List<Finding> findings;
        private final List<Throwable> exceptions;

        private EventCollector() {
            this.findings = new ArrayList<>();
            this.exceptions = new ArrayList<>();
        }

        @Override
        public void auditStarted(AuditEvent event) {
            // Nothing is collected before the audit.
        }

        @Override
        public void auditFinished(AuditEvent event) {
            // Nothing is collected after the audit.
        }

        @Override
        public void fileStarted(AuditEvent event) {
            // Findings are collected from error events alone.
        }

        @Override
        public void fileFinished(AuditEvent event) {
            // Findings are collected from error events alone.
        }

        @Override
        public void addError(AuditEvent event) {
            String source = event.getSourceName();
            String simple = source.substring(source.lastIndexOf('.') + 1).replaceFirst("Check$", "");
            String rule = Optional.ofNullable(event.getModuleId()).orElse(simple);
            this.findings.add(new Finding(rule, event.getLine(), event.getMessage()));
        }

        @Override
        public void addException(AuditEvent event, Throwable throwable) {
            this.exceptions.add(throwable);
        }

        List<Finding> findings() {
            return List.copyOf(this.findings);
        }

        List<Throwable> exceptions() {
            return List.copyOf(this.exceptions);
        }
    }
}
