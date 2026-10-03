package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EarlyAnalysisTest {

    @Test
    void findsTheViolationsAndAcceptsTheControls(@TempDir Path directory) {
        List<EarlyAnalysisCase> samples = EarlyAnalysisCase.all();
        assertFalse(samples.isEmpty(), "the early rules have executable examples");
        assertAll(
            samples.stream().map(
                sample -> () -> assertEquals(
                    sample.expected(),
                    assertDoesNotThrow(
                        () -> CheckstyleRule.findings(directory, sample.source(), sample.identifier()), sample
                            .identifier()
                    ),
                    () -> sample.identifier() + " over " + sample.source()
                )
            )
        );
    }

    @Test
    void recognizesTheOriginalSuppressionIdentifiers(@TempDir Path directory) {
        for (EarlyAnalysisCase sample : EarlyAnalysisCase.all()) {
            String suppressed = sample.source().replaceFirst(
                "(?m)^(?=class |abstract class |final class |interface |@Entity)",
                "@SuppressWarnings(\"" + sample.identifier() + "\") "
            );
            boolean declarationWasAnnotated = !suppressed.equals(sample.source());
            if (declarationWasAnnotated) {
                assertEquals(
                    0, CheckstyleRule.findings(directory, suppressed, sample.identifier()),
                    () -> sample.identifier() + " honors its existing declaration suppression"
                );
            }
        }
    }
}
