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
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Records what each Spring Boot application that reaches ready during tests turned out to be.
 *
 * <p>Spring Boot constructs this listener from {@code spring.factories} for every application run. The
 * Spring parent supplies the destination only to test JVMs, so an accidental runtime copy does nothing
 * and the evidence artifact never changes application behaviour outside verification.
 *
 * <p>Two facts are written. The primary source classes of the run, which say that the production
 * application was started at all, and the mappings its security chain leaves open to an anonymous
 * caller, which say what starting it exposed. Each open mapping is written on its own {@code open}
 * line, so a reader looking for an application class still finds one bare line per source and nothing
 * that could be mistaken for one.
 *
 * <p>The second fact exists only where a servlet stack and a security chain are on the classpath, and
 * asking for it anywhere else would link classes that are not there. The question is therefore put
 * behind a presence test, and the class that puts it is loaded only once that test has passed.
 */
public final class SpringContextEvidence implements SpringApplicationRunListener {

    private static final String DESTINATION = "airness.spring.context.evidence.file";
    private static final Lock WRITE_LOCK = new ReentrantLock();

    /**
     * Whether this JVM has the stack whose decisions the open-mapping evidence reads. A project that
     * builds no servlet web application, and one that builds a web application with no security chain,
     * both answer no and neither loads a class that would not resolve.
     */
    private static final boolean SERVLET_SECURITY = present(
        "org.springframework.security.web.FilterChainProxy",
        "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping",
        "org.springframework.mock.web.MockHttpServletRequest"
    );

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
            .ifPresent(destination -> write(destination, this.evidence(context)));
    }

    /**
     * Everything this run proves, in the order a reader of the file meets it.
     *
     * @param context the ready context
     * @return the source lines followed by the open-mapping lines, and nothing for a run that names
     *         no class, which proves no production application and is therefore not evidence of anything
     */
    private List<String> evidence(ConfigurableApplicationContext context) {
        List<String> sources = this.sources();
        return sources.isEmpty()
            ? sources
            : Stream.concat(sources.stream(), this.open(context, sources).stream()).toList();
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

    /**
     * The mappings the ready context leaves open, asked for only where the stack that has them is on
     * the classpath.
     *
     * @param context the ready context
     * @param sources the primary source class names of the run
     * @return the evidence lines, and nothing where this application builds no web security chain
     */
    private List<String> open(ConfigurableApplicationContext context, Collection<String> sources) {
        return SERVLET_SECURITY ? SpringOpenEndpoints.reached(context, roots(sources)) : List.of();
    }

    /**
     * The package roots the run declares, which the handlers the application owns sit under.
     *
     * @param sources the primary source class names of the run
     * @return one root per source that has one, a source in the default package naming no root and
     *         therefore claiming no handler
     */
    static Set<String> roots(Collection<String> sources) {
        return sources.stream()
            .filter(source -> source.lastIndexOf('.') > 0)
            .map(source -> source.substring(0, source.lastIndexOf('.')))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean present(String... types) {
        ClassLoader loader = SpringContextEvidence.class.getClassLoader();
        return Stream.of(types).allMatch(type -> ClassUtils.isPresent(type, loader));
    }

    private static void write(Path destination, Collection<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        String content = String.join("\n", lines) + '\n';
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
