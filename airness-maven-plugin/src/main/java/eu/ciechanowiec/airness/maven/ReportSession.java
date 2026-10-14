package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.eclipse.aether.SessionData;

/**
 * Immutable evidence receipts held for one Maven invocation, including parallel reactors.
 */
@UtilityClass
final class ReportSession {

    private static final String KEY = ReportSession.class.getName();
    private static final String SOURCE = KEY + ".source";
    private static final Lock LOCK = new ReentrantLock();

    static void begin(SessionData data, Path root) {
        LOCK.lock();
        try {
            if (!(data.get(SOURCE) instanceof ReportSource)) {
                data.set(SOURCE, ReportSource.read(root, Instant.now().toString()));
            }
        } finally {
            LOCK.unlock();
        }
    }

    static ReportSource source(SessionData data) {
        if (data.get(SOURCE) instanceof ReportSource source) {
            return source;
        }
        throw new IllegalStateException("Compliance input preparation did not run in this Maven invocation");
    }

    static Optional<List<ReportModule>> collect(
        SessionData data, Collection<ReportModule> incoming,
        Collection<String> required
    ) {
        LOCK.lock();
        try {
            Receipt receipt = data.get(KEY) instanceof Receipt stored ? stored : new Receipt(Map.of(), false);
            Map<String, ReportModule> modules = Stream.concat(receipt.modules().values().stream(), incoming.stream())
                .collect(
                    Collectors.toUnmodifiableMap(ReportModule::coordinate, module -> module, (_, latest) -> latest)
                );
            boolean complete = modules.keySet().containsAll(required);
            data.set(KEY, new Receipt(modules, complete));
            return complete && !receipt.published() ? Optional.of(
                modules.values().stream()
                    .sorted(Comparator.comparing(ReportModule::coordinate)).toList()
            ) : Optional.empty();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * One immutable session update.
     *
     * @param modules   collected evidence by module coordinate
     * @param published whether a complete set has already been claimed
     */
    private record Receipt(Map<String, ReportModule> modules, boolean published) {
    }
}
