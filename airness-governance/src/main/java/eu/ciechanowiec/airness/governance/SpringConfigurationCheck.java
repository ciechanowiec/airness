package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Reads the application configuration of one module for the settings that decide how it behaves in
 * production.
 *
 * <p>This is the one Airness check that reads something other than Java, Git, or the built archive. It
 * has to, because the defects it names are not in the source at all: a connection held for the length of
 * a request, an actuator publishing the environment, and a schema Hibernate is free to alter are each a
 * line of YAML, or the absence of one.
 *
 * <p>The goal that runs this is bound by {@code airness-parent-spring-boot} alone, and a module that
 * carries no configuration file is passed over rather than refused, because having none is an ordinary
 * state for a library module of a Spring Boot project.
 */
public final class SpringConfigurationCheck {

    private static final String SETTINGS = "Runtime settings that decide the wrong thing";
    private static final String UNREADABLE = "Configuration lines the reader could not account for";
    private static final String UNBOUND = "Settings the container has stopped binding";
    private static final String DEPRECATED = "Settings their supplier has deprecated";
    private static final String UNKNOWN = "Settings nothing on the classpath declares";
    private static final String UNREAD = "Configuration nothing on the classpath could account for";
    private static final String UNDECLARED_PLACEHOLDERS
        = "Placeholders production code reads that the base configuration does not declare";
    private static final List<String> NAMES = List.of(".yml", ".yaml", ".properties");
    private static final String PREFIX = "application";
    private static final Set<String> BASE = Set.of(
        "application.yml", "application.yaml", "application.properties"
    );

    private final Path root;
    private final List<Path> files;
    private final List<Path> sources;
    private final SpringMetadata metadata;

    /**
     * Creates a check over the configuration files one module holds.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     * @param sourceRoots   production source directories of the module, whose placeholders are read
     * @param published     the configuration metadata the compile classpath publishes
     */
    public SpringConfigurationCheck(
        Path root, Collection<Path> resourceRoots, Collection<Path> sourceRoots,
        Collection<ConfigurationProperty> published
    ) {
        this.root = root;
        this.files = configurations(root, resourceRoots);
        this.sources = JavaSources.under(root, sourceRoots);
        this.metadata = new SpringMetadata(published);
    }

    /**
     * How many configuration files the check read.
     *
     * @return the number of files in scope
     */
    public int scanned() {
        return this.files.size();
    }

    /**
     * Every rule and the settings that break it.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        List<Parsed> read = this.files.stream().map(this::parse).flatMap(List::stream).toList();
        return List.of(
            new Findings(
                SETTINGS,
                read.stream()
                    .flatMap(file -> SpringConfigurationRules.offences(file.path(), file.parsed()).stream())
                    .toList()
            ),
            new Findings(
                UNREADABLE,
                read.stream()
                    .flatMap(file -> file.parsed().unreadable().stream().map(line -> file.path() + ": " + line))
                    .toList()
            ),
            new Findings(UNBOUND, this.stated(read, SpringMetadataRules::unbound)),
            new Findings(DEPRECATED, this.stated(read, SpringMetadataRules::deprecated)),
            new Findings(UNKNOWN, this.stated(read, SpringMetadataRules::unknown)),
            new Findings(UNREAD, this.stated(read, SpringMetadataRules::unread)),
            new Findings(UNDECLARED_PLACEHOLDERS, this.placeholders(read))
        );
    }

    /*
     * The base files are the ones named with no profile, and a module holding none is passed over: a
     * library reads a key the application declares, and that is the application module's file to hold.
     */
    private List<String> placeholders(Collection<Parsed> read) {
        List<SpringConfiguration> base = read.stream()
            .filter(file -> BASE.contains(Path.of(file.path()).getFileName().toString()))
            .map(Parsed::parsed)
            .toList();
        return base.isEmpty()
            ? List.of()
            : this.sources.stream()
                .flatMap(
                    source -> Repository.readText(source).stream()
                        .flatMap(
                            text -> SpringPlaceholderRules.undeclaredPlaceholders(text, base, this.metadata)
                                .stream()
                        )
                        .map(offence -> this.root.relativize(source) + ": " + offence)
                )
                .toList();
    }

    private List<String> stated(Collection<Parsed> read, MetadataRule rule) {
        return read.stream()
            .flatMap(file -> rule.offences(file.path(), file.parsed(), this.metadata).stream())
            .toList();
    }

    private List<Parsed> parse(Path file) {
        return Repository.readText(file)
            .map(
                text -> List.of(
                    new Parsed(
                        this.root.relativize(file).toString(),
                        new SpringConfiguration(file.getFileName().toString(), text)
                    )
                )
            )
            .orElseGet(List::of);
    }

    private static List<Path> configurations(Path root, Collection<Path> resourceRoots) {
        List<Path> resolved = resourceRoots.stream().map(root::resolve).map(Path::normalize).toList();
        return Repository.trackedFiles(root).stream()
            .filter(file -> named(file.getFileName().toString()))
            .filter(file -> resolved.stream().anyMatch(file::startsWith))
            .toList();
    }

    private static boolean named(String file) {
        return file.startsWith(PREFIX) && NAMES.stream().anyMatch(file::endsWith);
    }

    /**
     * One configuration file, by the path an offence names it with and by what the reader made of it.
     *
     * @param path   the repository-relative path
     * @param parsed the settings and the lines the reader could not account for
     */
    private record Parsed(String path, SpringConfiguration parsed) {
    }

    /**
     * One rule read against the metadata, so the four of them are applied by naming them rather than by
     * repeating the walk over the files for each.
     */
    @FunctionalInterface
    private interface MetadataRule {

        List<String> offences(String path, SpringConfiguration configuration, SpringMetadata metadata);
    }
}
