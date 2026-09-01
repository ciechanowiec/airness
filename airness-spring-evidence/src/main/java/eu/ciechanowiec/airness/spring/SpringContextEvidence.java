package eu.ciechanowiec.airness.spring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Records the primary source classes of each Spring Boot application that reaches ready during tests.
 *
 * <p>Spring Boot constructs this listener from {@code spring.factories} for every application run. The
 * Spring parent supplies the destination only to test JVMs, so an accidental runtime copy does nothing
 * and the evidence artifact never changes application behaviour outside verification.
 */
public final class SpringContextEvidence implements SpringApplicationRunListener {

    private static final String DESTINATION = "airness.spring.context.evidence.file";
    private static final Lock WRITE_LOCK = new ReentrantLock();

    private final SpringApplication application;

    /**
     * Creates evidence for one Spring application run.
     *
     * @param application the application being run
     * @param arguments   its command-line arguments
     */
    public SpringContextEvidence(SpringApplication application, String... arguments) {
        this.application = Objects.requireNonNull(application, "Spring Boot supplied no application");
        Objects.requireNonNull(arguments, "Spring Boot supplied no application arguments");
    }

    @Override
    public void ready(ConfigurableApplicationContext context, @Nullable Duration timeTaken) {
        Objects.requireNonNull(context, "Spring Boot supplied no ready application context");
        Optional.ofNullable(System.getProperty(DESTINATION))
            .filter(configured -> !configured.isBlank())
            .map(Path::of)
            .ifPresent(destination -> write(destination, this.sources()));
    }

    private List<String> sources() {
        return this.application.getAllSources().stream()
            .filter(Class.class::isInstance)
            .map(Class.class::cast)
            .map(Class::getName)
            .distinct()
            .sorted()
            .toList();
    }

    private static void write(Path destination, Collection<String> sources) {
        if (sources.isEmpty()) {
            return;
        }
        String content = String.join("\n", sources) + '\n';
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(content);
        WRITE_LOCK.lock();
        try {
            append(destination, bytes);
        } finally {
            WRITE_LOCK.unlock();
        }
    }

    private static void append(Path destination, ByteBuffer bytes) {
        try {
            Files.createDirectories(
                Objects.requireNonNull(destination.toAbsolutePath().getParent(), "Evidence path has no parent")
            );
            appendLocked(destination, bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write Spring context evidence " + destination, exception);
        }
    }

    private static void appendLocked(Path destination, ByteBuffer bytes) throws IOException {
        try (
            FileChannel channel = FileChannel.open(
                destination,
                StandardOpenOption.APPEND,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            );
            FileLock _ = channel.lock()
        ) {
            writeFully(channel, bytes);
        }
    }

    static void writeFully(WritableByteChannel channel, ByteBuffer bytes) throws IOException {
        while (bytes.hasRemaining()) {
            if (channel.write(bytes) == 0) {
                throw new IOException("Evidence channel made no progress");
            }
        }
    }
}
