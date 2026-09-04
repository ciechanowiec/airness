package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Requires current-build evidence that Spring Boot made the production application ready.
 *
 * <p>The evidence comes from Spring Boot's own run lifecycle rather than from a test annotation. A
 * composed annotation, an explicit production application class, and a direct call to
 * {@code SpringApplication.run} therefore mean the same thing here: each counts only when the actual
 * production application was one of the primary sources of a run that reached ready.
 *
 * <p>Requiring the application to start is what makes a second question askable. Once a build must
 * produce a ready context, what that context decided is available to be read, and the mappings it
 * left open to an anonymous caller are read here against the patterns the module says it meant to
 * open. The two rules share a file because they share a guarantee: evidence is only worth judging
 * where its absence is already a failure.
 */
public final class SpringContextCheck {

    private static final Pattern APPLICATION = Pattern.compile("@SpringBootApplication\\b");
    private static final Pattern PACKAGE = Pattern.compile(
        "\\bpackage\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;"
    );
    private static final String MISSING = "Spring application context not started by this build";
    private static final String OPEN = "Endpoints the security chain admits to an unauthenticated caller";
    private static final String OPENED = "open ";

    private final Path evidence;
    private final long started;
    private final List<String> applications;
    private final SpringTypes types;
    private final int sources;

    /**
     * Reads the production sources and the evidence emitted by the test JVM.
     *
     * @param root        repository root used to read source paths consistently
     * @param sourceRoots production source roots of one module
     * @param evidence    file written when Spring Boot reaches ready
     * @param started     Maven session start time in epoch milliseconds
     */
    public SpringContextCheck(
        Path root, Collection<Path> sourceRoots, Path evidence, long started
    ) {
        List<Path> found = JavaSources.under(root, sourceRoots);
        this.evidence = evidence;
        this.started = started;
        this.sources = found.size();
        this.types = SpringTypes.over(root, found);
        this.applications = this.types.carrying(APPLICATION).stream()
            .filter(SpringTypes.Declared::production)
            .map(SpringContextCheck::qualified)
            .toList();
    }

    /**
     * How many production Java sources were read.
     *
     * @return source count
     */
    public int scanned() {
        return this.sources;
    }

    /**
     * How many production application classes the module declares.
     *
     * @return application class count
     */
    public int applications() {
        return this.applications.size();
    }

    /**
     * The startup-evidence rule, the open-endpoint rule, and what each of them found.
     *
     * @return the verdict
     */
    public List<Findings> findings() {
        List<String> recorded = this.recorded();
        return List.of(
            new Findings(MISSING, this.missing(recorded)),
            new Findings(OPEN, SpringEndpointRules.undeclared(opened(recorded), this.types))
        );
    }

    /**
     * The lines this build's evidence holds, and nothing where the evidence is absent or stale.
     *
     * @return the recorded lines
     */
    private List<String> recorded() {
        return this.current() ? this.read() : List.of();
    }

    private static List<String> opened(Collection<String> recorded) {
        return recorded.stream().filter(line -> line.startsWith(OPENED)).toList();
    }

    private List<String> missing(Collection<String> recorded) {
        if (this.applications.isEmpty()) {
            return List.of();
        }
        Set<String> ready = Set.copyOf(recorded);
        return this.applications.stream()
            .filter(application -> !ready.contains(application))
            .map(
                application -> application + ": " + this.evidence
                    + " contains no current run that reached ready with this production application"
            )
            .toList();
    }

    private boolean current() {
        return Files.isRegularFile(this.evidence) && modified(this.evidence) >= this.started;
    }

    private List<String> read() {
        try {
            return Files.readAllLines(this.evidence).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read Spring context evidence " + this.evidence, exception);
        }
    }

    private static String qualified(SpringTypes.Declared type) {
        Matcher declaration = PACKAGE.matcher(type.code());
        return declaration.find() ? declaration.group(1) + '.' + type.name() : type.name();
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the age of " + file, exception);
        }
    }
}
