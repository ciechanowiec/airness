package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What the {@code e2e} gate is handed to sweep a web interface the module serves itself: the artifact this
 * invocation packaged, the Java runtime of the build, and the inventory of the HTML routes it serves.
 *
 * <p>The routes come from the Spring context evidence the module's tests record when the application starts,
 * one {@code html-route} line per mapping that renders a view, and from the HTML files under the locations
 * Spring Boot serves statically. Evidence an earlier invocation left is not read, so a sweep is accounted
 * against routes of the application this build tested. A web interface whose artifact or routes this
 * invocation cannot supply is unassessed, and unassessed is a failure rather than a pass.
 *
 * @param environment the variables the gate runs with, empty when there are problems
 * @param problems    why the sweep cannot be accounted, empty when it can
 */
record WebSweepLaunch(Map<String, String> environment, List<String> problems) {

    /**
     * The variable naming the route inventory file, one {@code pattern<TAB>source} line per route.
     */
    static final String INVENTORY = "AIRNESS_ROUTE_INVENTORY";

    private static final String ROUTE = "html-route";
    private static final String TAB = "\t";
    private static final int FIELDS = 3;
    private static final List<Path> STATIC_LOCATIONS = Stream.of("static", "public", "resources", "META-INF/resources")
        .map(Path::of).toList();
    private static final String INDEX = "index.html";
    private static final String HTML = ".html";

    /**
     * Copies both, so the launch cannot change after it was prepared.
     *
     * @param environment the variables the gate runs with
     * @param problems    why the sweep cannot be accounted
     */
    WebSweepLaunch {
        environment = Map.copyOf(environment);
        problems = List.copyOf(problems);
    }

    /**
     * Prepares the sweep of one web interface, writing its route inventory.
     *
     * @param moduleName    the module, as a problem names it
     * @param build         what this invocation built, and where
     * @param resourceRoots the module's main resource roots
     * @param javaHome      the Java runtime the build runs on
     * @return the variables, or the problems that leave the interface unassessed
     */
    static WebSweepLaunch prepare(String moduleName, Packaged build, Collection<Path> resourceRoots, String javaHome) {
        Path evidence = build.evidence();
        Path inventory = build.inventory();
        long started = build.started();
        Optional<Path> packaged = build.artifact().filter(Files::isRegularFile);
        List<String> routes = Stream.concat(evidenced(evidence, started), pages(resourceRoots)).distinct().toList();
        List<String> problems = Stream.of(
            Optional.of(
                moduleName + " serves HTML, so the browser sweep launches the artifact this invocation packaged, and "
                    + "it packaged none; run the build through the package phase"
            ).filter(_ -> packaged.isEmpty()),
            Optional.of(
                moduleName + " serves HTML, but its route inventory is empty: no test of this invocation started the "
                    + "application to record its HTML routes in " + evidence + ", and it ships no static page; start "
                    + "the application in a @SpringBootTest so the routes the sweep must cover are known"
            ).filter(_ -> routes.isEmpty())
        ).flatMap(Optional::stream).toList();
        if (!problems.isEmpty()) {
            return new WebSweepLaunch(Map.of(), problems);
        }
        write(inventory, routes);
        return new WebSweepLaunch(
            Map.of(
                INVENTORY, inventory.toString(),
                WebInterface.ARTIFACT, packaged.orElseThrow().toAbsolutePath().toString(),
                "JAVA_HOME", javaHome
            ),
            List.of()
        );
    }

    /**
     * Why a web interface stayed unassessed although every node project ran the gate: none of them judged it.
     *
     * @param moduleName the module, as the problem names it
     * @param web        the module's web interface
     * @param results    what the gate reported in each node project
     * @return the problem, or nothing when some project judged the interface
     */
    static Optional<String> unassessed(String moduleName, WebInterface web, Collection<NodeGateResult> results) {
        return Optional.of(
            moduleName + " serves HTML (" + web.summary() + "), but the e2e gate applied in none of its node "
                + "projects, so the interface is unassessed; name the script that launches it in airness.serve"
        ).filter(_ -> web.detected() && results.stream().noneMatch(NodeGateResult::applicable));
    }

    private static Stream<String> evidenced(Path evidence, long started) {
        if (!Files.isRegularFile(evidence) || modified(evidence) < started) {
            return Stream.empty();
        }
        return read(evidence).lines()
            .map(line -> line.split(TAB, -1))
            .filter(fields -> fields.length == FIELDS && ROUTE.equals(fields[0]) && !fields[1].isBlank())
            .map(fields -> fields[1].strip() + TAB + fields[2].strip());
    }

    private static Stream<String> pages(Collection<Path> resourceRoots) {
        return resourceRoots.stream().flatMap(
            root -> STATIC_LOCATIONS.stream().map(root::resolve).filter(Files::isDirectory)
                .flatMap(location -> walk(location).map(file -> page(root, location, file)).flatMap(Optional::stream))
        );
    }

    // The address Spring Boot serves a static file at: its path below the location, and the root for the
    // welcome page.
    private static Optional<String> page(Path root, Path location, Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(HTML)) {
            return Optional.empty();
        }
        String address = '/' + location.relativize(file).toString().replace('\\', '/');
        String route = ('/' + INDEX).equals(address) ? "/" : address;
        return Optional.of(route + TAB + root.relativize(file).toString().replace('\\', '/'));
    }

    private static Stream<Path> walk(Path location) {
        try (Stream<Path> files = Files.walk(location)) {
            return files.filter(Files::isRegularFile).sorted().toList().stream();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + location, exception);
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private static void write(Path file, List<String> routes) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, String.join("\n", routes) + '\n');
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write the route inventory " + file, exception);
        }
    }

    /**
     * What one invocation built for a module, and where its evidence lies.
     *
     * @param artifact  the artifact this invocation packaged, or nothing when it packaged none
     * @param directory the module's build directory
     * @param started   when this invocation started, in epoch milliseconds
     */
    record Packaged(Optional<Path> artifact, Path directory, long started) {

        /**
         * The Spring context evidence the module's startup tests write.
         *
         * @return the evidence file
         */
        Path evidence() {
            return this.directory.resolve("airness").resolve("spring-context.evidence");
        }

        /**
         * Where the route inventory is written for the sweep.
         *
         * @return the inventory file
         */
        Path inventory() {
            return this.directory.resolve("airness").resolve("web-routes.tsv");
        }
    }
}
