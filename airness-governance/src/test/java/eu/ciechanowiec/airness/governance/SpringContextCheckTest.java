package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class SpringContextCheckTest {

    private static final String APPLICATION = """
        package sample;

        @SpringBootApplication(proxyBeanMethods = false)
        public final class Application {
        }
        """;
    private static final String PLAIN = """
        package sample;

        public final class Plain {
        }
        """;

    @Test
    @SneakyThrows
    void passesOverAModuleWithNoApplicationClass() {
        Path root = this.source("Plain.java", PLAIN);
        SpringContextCheck check = this.check(root, root.resolve("missing"), Long.MAX_VALUE);

        assertTrue(Verdicts.clean(check.findings()), "a Spring library has no application to start");
    }

    @Test
    @SneakyThrows
    void reportsAnApplicationWithNoEvidence() {
        Path root = this.source("Application.java", APPLICATION);
        SpringContextCheck check = this.check(root, root.resolve("missing"), 0);

        assertEquals(
            1, offences(check).size(), "a production application needs one ready run from this build"
        );
    }

    @Test
    @SneakyThrows
    void acceptsCurrentEvidenceNamingTheProductionApplication() {
        Path root = this.source("Application.java", APPLICATION);
        Path evidence = this.evidence(root, "sample.Application\n");
        long started = Files.getLastModifiedTime(evidence).toMillis();

        assertTrue(
            Verdicts.clean(this.check(root, evidence, started).findings()),
            "the production application was a source of a run that reached ready"
        );
    }

    @Test
    @SneakyThrows
    void rejectsAReadyRunUsingOnlyTestConfiguration() {
        Path root = this.source("Application.java", APPLICATION);
        Path evidence = this.evidence(root, "sample.TestConfiguration\n");

        assertEquals(
            1, offences(this.check(root, evidence, 0)).size(),
            "a narrow ready context does not prove the production component scan"
        );
    }

    @Test
    @SneakyThrows
    void rejectsEvidenceFromAnEarlierMavenSession() {
        Path root = this.source("Application.java", APPLICATION);
        Path evidence = this.evidence(root, "sample.Application\n");
        Files.setLastModifiedTime(evidence, FileTime.fromMillis(1));

        assertEquals(
            1, offences(this.check(root, evidence, 2)).size(),
            "an old startup cannot stand in for tests from this build"
        );
    }

    @Test
    @SneakyThrows
    void readsAnApplicationInTheDefaultPackage() {
        Path root = this.source("Application.java", APPLICATION.replace("package sample;\n\n", ""));
        Path evidence = this.evidence(root, "Application\n");

        assertTrue(
            Verdicts.clean(this.check(root, evidence, 0).findings()),
            "the source parser keeps a default-package name whole"
        );
    }

    @SneakyThrows
    private Path source(String name, CharSequence content) {
        Path root = new GitFixture("spring-context-" + name.replace(".java", "")).root();
        Path source = root.resolve("src/main/java/sample").resolve(name);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
        return root;
    }

    @SneakyThrows
    private Path evidence(Path root, CharSequence content) {
        Path evidence = root.resolve("target/airness/spring-context.evidence");
        Files.createDirectories(evidence.getParent());
        return Files.writeString(evidence, content);
    }

    private SpringContextCheck check(Path root, Path evidence, long started) {
        return new SpringContextCheck(
            root, List.of(root.resolve("src/main/java")), evidence, started
        );
    }

    private static List<String> offences(SpringContextCheck check) {
        return Verdicts.offences(check.findings(), "context not started");
    }
}
