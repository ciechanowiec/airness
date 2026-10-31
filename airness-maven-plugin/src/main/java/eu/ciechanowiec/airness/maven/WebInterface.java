package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Whether a module serves user-facing HTML, and the repository evidence that says so.
 *
 * <p>A module is a web interface when it carries a serving stack ({@code spring-webmvc} or
 * {@code spring-webflux}) and something that serves markup through it: a {@code @Controller} handler, which
 * Spring renders as a view unless it writes the body itself, a view controller registration, or an HTML file
 * under one of the locations Spring Boot serves statically. A library without a serving stack is not one,
 * and neither is a JSON service, including one that renders templates only into e-mail. The verdict follows
 * the evidence alone, so no setting can relabel a module that serves pages.
 *
 * <p>Detection reads source text rather than compiled classes, so it runs before anything is built. A
 * controller written in another JVM language, or HTML served from a location the module configures itself,
 * is beyond it.
 *
 * @param evidence what makes the module a web interface, or nothing when it is none
 * @param absence  why the module is no web interface, read only when there is no evidence
 */
record WebInterface(List<String> evidence, String absence) {

    /**
     * The variable the {@code e2e} gate exports with the module's packaged artifact, which the serve script of a
     * Java web interface launches.
     */
    static final String ARTIFACT = "AIRNESS_APPLICATION_ARTIFACT";

    private static final Pattern CONTROLLER = Pattern.compile(
        "^\\s*@(?:org\\.springframework\\.stereotype\\.)?Controller\\b", Pattern.MULTILINE
    );
    private static final Pattern VIEW_CONTROLLERS = Pattern.compile("\\baddViewControllers\\s*\\(");
    private static final List<Path> STATIC_LOCATIONS = Stream.of("static", "public", "resources", "META-INF/resources")
        .map(Path::of).toList();
    private static final String HTML = ".html";
    private static final String SCRIPTS = "scripts";
    private static final int SHOWN = 3;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LAUNCH = "\"$JAVA_HOME/bin/java\" -jar \"$" + ARTIFACT + "\" --server.port=$PORT";

    /**
     * Copies the evidence, so the verdict cannot change after it was read.
     *
     * @param evidence what makes the module a web interface
     * @param absence  why the module is none
     */
    WebInterface {
        evidence = List.copyOf(evidence);
    }

    /**
     * Reads one module.
     *
     * @param module        the module directory, which the evidence is stated relative to
     * @param serving       whether the module resolves a serving stack outside the test scope
     * @param sourceRoots   the module's production Java source roots
     * @param resourceRoots the module's main resource roots
     * @return the verdict
     */
    static WebInterface of(Path module, boolean serving, Collection<Path> sourceRoots, Collection<Path> resourceRoots) {
        if (!serving) {
            return new WebInterface(List.of(), "it resolves no serving stack (spring-webmvc or spring-webflux)");
        }
        List<String> evidence = Stream.concat(
            files(sourceRoots).filter(file -> file.toString().endsWith(".java"))
                .flatMap(file -> rendering(file).map(what -> relative(module, file) + " declares " + what).stream()),
            resourceRoots.stream().flatMap(root -> pages(module, root))
        ).toList();
        return new WebInterface(
            evidence, "it serves no markup: no @Controller handler, view controller or static HTML page"
        );
    }

    /**
     * Whether the module serves user-facing HTML.
     *
     * @return whether there is evidence of a web interface
     */
    boolean detected() {
        return !this.evidence.isEmpty();
    }

    /**
     * The evidence or the absence, shortened for one log line or one offence.
     *
     * @return the summary
     */
    String summary() {
        if (!this.detected()) {
            return this.absence;
        }
        String shown = String.join(", ", this.evidence.subList(0, Math.min(SHOWN, this.evidence.size())));
        int more = this.evidence.size() - SHOWN;
        return more > 0 ? shown + " and " + more + " more" : shown;
    }

    /**
     * What keeps the browser sweep from reaching this web interface: no declared node project names a serve
     * script that launches the artifact this build packaged.
     *
     * @param moduleName the module, as the offence names it
     * @param declared   the node projects the module declares
     * @return one offence when nothing serves the interface for the sweep, otherwise nothing
     */
    List<String> integrationProblems(String moduleName, NodeProjects declared) {
        return this.detected() ? this.unlaunched(moduleName, declared) : List.of();
    }

    // The offence of a detected interface that no declared project launches.
    private List<String> unlaunched(String moduleName, NodeProjects declared) {
        List<NodeProject> projects = declared.projects().stream()
            .filter(project -> !project.outside() && project.hasManifest()).toList();
        List<String> serving = projects.stream().flatMap(project -> serveProblem(project).stream()).toList();
        boolean launched = projects.stream()
            .anyMatch(project -> serveScript(project).filter(WebInterface::launches).isPresent());
        if (launched) {
            return List.of();
        }
        String reached = serving.isEmpty() ? "no declared node project serves it for the browser sweep"
            : String.join("; ", serving);
        return List.of(
            moduleName + " serves HTML (" + this.summary() + "), but " + reached + ". Declare a frontend tooling "
                + "project in airness.node.projects whose package.json sets \"airness\": {\"serve\": \"<script>\"}, "
                + "naming a script that launches this module's packaged artifact, such as " + LAUNCH + ", and start "
                + "the services it needs through airness.testWrapper. The application keeps its Java runtime"
        );
    }

    private static boolean launches(String script) {
        return script.contains(ARTIFACT);
    }

    // What a declared serve setting gets wrong, read from the project's manifest.
    private static Optional<String> serveProblem(NodeProject project) {
        JsonNode manifest = manifest(project);
        String serve = manifest.path("airness").path("serve").asText("");
        JsonNode scripts = manifest.path(SCRIPTS);
        Optional<String> undeclared = Optional.of(
            project.key() + " names the serve script " + serve + ", which its package.json does not declare"
        ).filter(_ -> !scripts.has(serve));
        Optional<String> unlaunching = Optional.of(
            project.key() + " serves through the script " + serve + ", which does not launch $" + ARTIFACT
                + ", the artifact this build packaged"
        ).filter(_ -> !launches(scripts.path(serve).asText("")));
        return serve.isEmpty() ? Optional.empty() : undeclared.or(() -> unlaunching);
    }

    private static Optional<String> serveScript(NodeProject project) {
        JsonNode manifest = manifest(project);
        String serve = manifest.path("airness").path("serve").asText("");
        JsonNode script = manifest.path(SCRIPTS).path(serve);
        return serve.isEmpty() || !script.isTextual() ? Optional.empty() : Optional.of(script.asText());
    }

    private static JsonNode manifest(NodeProject project) {
        try {
            return JSON.readTree(project.directory().resolve("package.json").toFile());
        } catch (IOException _) {
            return JSON.missingNode();
        }
    }

    private static Optional<String> rendering(Path file) {
        String text = read(file);
        if (CONTROLLER.matcher(text).find()) {
            return Optional.of("a @Controller");
        }
        return Optional.of("view controllers").filter(_ -> VIEW_CONTROLLERS.matcher(text).find());
    }

    private static Stream<String> pages(Path module, Path root) {
        return STATIC_LOCATIONS.stream()
            .map(root::resolve)
            .flatMap(location -> files(List.of(location)))
            .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(HTML))
            .map(file -> relative(module, file) + " is a static page");
    }

    private static Stream<Path> files(Collection<Path> roots) {
        return roots.stream().filter(Files::isDirectory).flatMap(WebInterface::walk).sorted();
    }

    private static Stream<Path> walk(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).toList().stream();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + root, exception);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private static String relative(Path module, Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Path base = module.toAbsolutePath().normalize();
        return (absolute.startsWith(base) ? base.relativize(absolute) : absolute).toString().replace('\\', '/');
    }
}
