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
import java.util.stream.Collectors;

/**
 * Requires current-build evidence that Spring Boot made the production application ready.
 *
 * <p>The evidence comes from Spring Boot's own run lifecycle rather than from a test annotation. A
 * composed annotation, an explicit production application class, and a direct call to
 * {@code SpringApplication.run} therefore mean the same thing here: each counts only when the actual
 * production application was one of the primary sources of a run that reached ready.
 */
public final class SpringContextCheck {

    private static final Pattern APPLICATION = Pattern.compile("@SpringBootApplication\\b");
    private static final Pattern PACKAGE = Pattern.compile(
        "\\bpackage\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;"
    );
    private static final String MISSING = "Spring application context not started by this build";

    private final Path evidence;
    private final long started;
    private final List<String> applications;
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
        this.applications = SpringTypes.over(root, found).carrying(APPLICATION).stream()
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
     * The startup-evidence rule and every application class for which evidence is absent.
     *
     * @return the verdict
     */
    public List<Findings> findings() {
        return List.of(new Findings(MISSING, this.missing()));
    }

    private List<String> missing() {
        if (this.applications.isEmpty()) {
            return List.of();
        }
        Set<String> ready = this.current() ? this.ready() : Set.of();
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

    private Set<String> ready() {
        try {
            return Files.readAllLines(this.evidence).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
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
