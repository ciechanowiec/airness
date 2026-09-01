package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ModuleOutput;
import eu.ciechanowiec.airness.governance.ParameterMetadataCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Airness retains parameter names in its own compiler output before requiring them from consumers.
 */
class SelfParameterMetadataTest {

    private static final String MAIN = "src/main/java";
    private static final String TEST = "src/test/java";

    @Test
    void everyHarnessModuleRetainsFormalParameterNames() {
        List<String> broken = SelfModules.withProductionJava().stream()
            .map(SelfParameterMetadataTest::findings)
            .filter(finding -> !finding.clean())
            .map(Findings::report)
            .toList();
        assertEquals(
            List.of(), broken,
            "the harness answers to the compiler parameter contract it publishes for consumers"
        );
    }

    private static Findings findings(Path module) {
        List<Path> testSources = Stream.of(module.resolve(TEST))
            .filter(Files::isDirectory)
            .toList();
        return new ParameterMetadataCheck(
            List.of(module.resolve(MAIN)),
            testSources,
            new ModuleOutput(module.resolve("target/classes"), module.resolve("target/test-classes"))
        ).findings();
    }
}
