package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A module serving user-facing HTML is told apart from a JSON service and a library, and must declare how the
 * browser sweep launches it.
 */
class WebInterfaceTest {

    private static final String JAVA = "src/main/java/app/";
    private static final String RESOURCES = "src/main/resources";
    private static final String TOOLING = "frontend";
    private static final String LAUNCHING = "{\"airness\": {\"serve\": \"serve\"}, \"scripts\": {\"serve\": "
        + "\"\\\"$JAVA_HOME/bin/java\\\" -jar \\\"$AIRNESS_APPLICATION_ARTIFACT\\\" --server.port=$PORT\"}}\n";

    @TempDir
    private Path module;

    @SneakyThrows
    private void write(String file, String content) {
        Path path = this.module.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private WebInterface web(boolean serving) {
        return WebInterface.of(
            this.module, serving, List.of(this.module.resolve("src/main/java")), List.of(this.module.resolve(RESOURCES))
        );
    }

    @Test
    void detectsAControllerThatRendersViews() {
        this.write(JAVA + "Pages.java", "package app;\n\n@Controller\nclass Pages {}\n");
        WebInterface web = this.web(true);
        assertEquals(List.of(JAVA + "Pages.java declares a @Controller"), web.evidence());
    }

    @Test
    void detectsViewControllersAndStaticPages() {
        this.write(JAVA + "Views.java", "class Views { void addViewControllers(Object registry) {} }\n");
        this.write(RESOURCES + "/static/index.html", "<html></html>\n");
        assertEquals(
            List.of(JAVA + "Views.java declares view controllers", RESOURCES + "/static/index.html is a static page"),
            this.web(true).evidence()
        );
    }

    @Test
    void readsAJsonServiceAsNoWebInterface() {
        this.write(JAVA + "Api.java", "@RestController\n@ControllerAdvice\nclass Api {}\n");
        this.write(RESOURCES + "/templates/mail/welcome.html", "<p>Welcome</p>\n");
        WebInterface web = this.web(true);
        assertFalse(web.detected(), "a REST service rendering e-mail serves no pages: " + web.evidence());
        assertTrue(web.summary().startsWith("it serves no markup"), web.summary());
    }

    @Test
    void readsALibraryWithoutAServingStackAsNoWebInterface() {
        this.write(JAVA + "Pages.java", "@Controller\nclass Pages {}\n");
        WebInterface web = this.web(false);
        assertFalse(web.detected(), "nothing serves a page without a serving stack");
        assertEquals(List.of(), web.integrationProblems("library", NodeProjects.of(this.module, null)));
    }

    @Test
    void requiresANodeProjectThatServesTheInterface() {
        this.write(JAVA + "Pages.java", "@Controller\nclass Pages {}\n");
        List<String> problems = this.web(true).integrationProblems("shop", NodeProjects.of(this.module, null));
        assertEquals(1, problems.size());
        assertTrue(
            problems.getFirst().startsWith(
                "shop serves HTML (" + JAVA + "Pages.java declares a @Controller), but no declared node project "
                    + "serves it for the browser sweep. Declare a frontend tooling project in airness.node.projects"
            ),
            problems.getFirst()
        );
    }

    @Test
    void requiresTheServeScriptToLaunchThePackagedArtifact() {
        this.write(JAVA + "Pages.java", "@Controller\nclass Pages {}\n");
        this.write(
            TOOLING + "/package.json",
            "{\"airness\": {\"serve\": \"serve\"}, \"scripts\": {\"serve\": \"java -jar old.jar\"}}\n"
        );
        List<String> problems = this.web(true).integrationProblems("shop", NodeProjects.of(this.module, TOOLING));
        assertTrue(
            problems.getFirst().contains(
                "frontend serves through the script serve, which does not launch $AIRNESS_APPLICATION_ARTIFACT"
            ),
            "a stale or hand-picked jar is not the application this build packaged: " + problems
        );
    }

    @Test
    void acceptsAServeScriptLaunchingThePackagedArtifact() {
        this.write(JAVA + "Pages.java", "@Controller\nclass Pages {}\n");
        this.write(TOOLING + "/package.json", LAUNCHING);
        assertEquals(List.of(), this.web(true).integrationProblems("shop", NodeProjects.of(this.module, TOOLING)));
    }

    @Test
    void namesAServeScriptThePackageDoesNotDeclare() {
        this.write(RESOURCES + "/public/about.html", "<html></html>\n");
        this.write(TOOLING + "/package.json", "{\"airness\": {\"serve\": \"start\"}}\n");
        List<String> problems = this.web(true).integrationProblems("site", NodeProjects.of(this.module, TOOLING));
        assertTrue(
            problems.getFirst().contains("frontend names the serve script start, which its package.json does not"),
            problems.getFirst()
        );
    }
}
