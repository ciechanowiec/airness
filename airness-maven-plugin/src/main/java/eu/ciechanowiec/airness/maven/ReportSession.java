package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.eclipse.aether.SessionData;

/**
 * The source identity a compliance report describes, captured once at the start of one Maven invocation.
 */
@UtilityClass
final class ReportSession {

    private static final String SOURCE = ReportSession.class.getName() + ".source";
    private static final int TIMESTAMP = 2;
    private static final int REASONS = 3;

    static void begin(SessionData data, Path root) {
        data.computeIfAbsent(SOURCE, () -> capture(root));
    }

    private static List<String> capture(Path root) {
        ReportSource source = ReportSource.read(root, Instant.now().toString());
        return Stream.concat(
            Stream.of(source.commit(), source.fingerprint(), source.generatedAt()), source.draftReasons().stream()
        ).toList();
    }

    static ReportSource source(SessionData data) {
        if (data.get(SOURCE) instanceof List<?> stored) {
            List<String> values = stored.stream().map(String.class::cast).toList();
            return new ReportSource(
                values.getFirst(), values.get(1), values.get(TIMESTAMP), values.subList(REASONS, values.size())
            );
        }
        throw new IllegalStateException("Compliance input preparation did not run in this Maven invocation");
    }
}
