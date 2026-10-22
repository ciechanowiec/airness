package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.SpringContextCheck;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import lombok.SneakyThrows;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.SessionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InvocationEvidenceTest {

    private static final String BUILD = "target";
    private static final String COVERAGE = "jacoco.exec";
    private static final String SPRING = "airness/spring-context.evidence";
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void clearsEvidenceAtTheStartOfEveryInvocation() {
        Path build = this.directory.resolve(BUILD);
        Files.createDirectories(build.resolve("airness"));
        Path coverage = Files.writeString(build.resolve(COVERAGE), "previous probes");
        Path spring = Files.writeString(build.resolve(SPRING), "sample.Application\n");
        InvocationEvidence.prepare(new DefaultSessionData(), build, coverage);
        assertFalse(Files.exists(coverage));
        assertFalse(Files.exists(spring));
        Files.writeString(coverage, "new probes");
        InvocationEvidence.prepare(new DefaultSessionData(), build, coverage);
        assertFalse(Files.exists(coverage));
    }

    @Test
    @SneakyThrows
    void preservesEvidenceBetweenTestPhasesAndForkedLifecycles() {
        Path build = this.directory.resolve(BUILD);
        Files.createDirectories(build.resolve("airness"));
        Path coverage = build.resolve(COVERAGE);
        Path spring = build.resolve(SPRING);
        SessionData session = new DefaultSessionData();
        InvocationEvidence.prepare(session, build, coverage);
        Files.writeString(coverage, "unit probes");
        Files.writeString(spring, "unit context\n");
        InvocationEvidence.prepare(session, build.resolve("."), coverage);
        Files.writeString(coverage, " and integration probes", StandardOpenOption.APPEND);
        Files.writeString(spring, "integration context\n", StandardOpenOption.APPEND);
        assertEquals("unit probes and integration probes", Files.readString(coverage));
        assertEquals("unit context\nintegration context\n", Files.readString(spring));
    }

    @Test
    @SneakyThrows
    void resetsEachModuleIndependently() {
        SessionData session = new DefaultSessionData();
        for (String name : List.of("first", "second")) {
            Path build = Files.createDirectory(this.directory.resolve(name));
            Path coverage = Files.writeString(build.resolve(COVERAGE), "old probes");
            InvocationEvidence.prepare(session, build, coverage);
            assertFalse(Files.exists(coverage));
        }
    }

    @Test
    @SneakyThrows
    void failsWhenOldEvidenceCannotBeRemoved() {
        Path build = Files.createDirectory(this.directory.resolve(BUILD));
        Path coverage = Files.createDirectory(build.resolve(COVERAGE));
        Files.writeString(coverage.resolve("entry"), "cannot delete a nonempty directory");
        assertThrows(
            UncheckedIOException.class, () -> InvocationEvidence.prepare(new DefaultSessionData(), build, coverage)
        );
    }

    @Test
    @SneakyThrows
    void aTestOnlyAppendCannotRefreshAnOldProductionStartup() {
        Path spring = this.applicationEvidence("sample.Application\n");
        Path build = this.directory.resolve(BUILD);
        InvocationEvidence.prepare(new DefaultSessionData(), build, build.resolve(COVERAGE));
        Files.writeString(spring, "sample.TestOnlyApplication\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        assertFalse(this.context(spring).findings().getFirst().clean());
    }

    @Test
    @SneakyThrows
    void dropsOldSecurityFindingsWhenTheNewContextIsClean() {
        Path spring = this.applicationEvidence("sample.Application\nguard-bean missing sample.Application#call\n");
        Path build = this.directory.resolve(BUILD);
        InvocationEvidence.prepare(new DefaultSessionData(), build, build.resolve(COVERAGE));
        Files.writeString(spring, "sample.Application\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        assertTrue(this.context(spring).findings().stream().allMatch(Findings::clean));
    }

    private SpringContextCheck context(Path evidence) {
        return new SpringContextCheck(this.directory, List.of(Path.of("src/main/java")), evidence, 0);
    }

    @SneakyThrows
    private Path applicationEvidence(String content) {
        Path log = this.directory.resolve("git.log");
        Process git = new ProcessBuilder("git", "init", "--quiet", this.directory.toString())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertEquals(0, ScannerCommand.await(git, log, GIT_TIMEOUT));
        Path source = Files.createDirectories(this.directory.resolve("src/main/java/sample"));
        Files.writeString(
            source.resolve("Application.java"), """
                package sample;
                @SpringBootApplication
                public final class Application {}
                """
        );
        Path build = this.directory.resolve(BUILD);
        Files.createDirectories(build.resolve("airness"));
        return Files.writeString(build.resolve(SPRING), content);
    }
}
