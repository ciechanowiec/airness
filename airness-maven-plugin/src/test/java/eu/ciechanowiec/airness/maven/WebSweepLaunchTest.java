package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The e2e gate of a web interface is handed the packaged artifact and the routes the application serves, or is
 * told why the interface stays unassessed.
 */
class WebSweepLaunchTest {

    private static final String RESOURCES = "src/main/resources";
    private static final String EVIDENCE = "target/airness/spring-context.evidence";
    private static final String INVENTORY = "target/airness/web-routes.tsv";
    private static final long STARTED = 1_000_000L;

    @TempDir
    private Path module;

    @SneakyThrows
    private Path write(String file, String content) {
        Path path = this.module.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        Files.setLastModifiedTime(path, FileTime.fromMillis(STARTED + 1));
        return path;
    }

    private WebSweepLaunch prepare(@Nullable Path artifact) {
        return WebSweepLaunch.prepare(
            "shop", new WebSweepLaunch.Packaged(Optional.ofNullable(artifact), this.module.resolve("target"), STARTED),
            List.of(this.module.resolve(RESOURCES)), "/opt/java"
        );
    }

    @Test
    @SneakyThrows
    void writesTheRoutesTheEvidenceAndTheStaticPagesDeclare() {
        Path artifact = this.write("target/shop.jar", "jar");
        this.write(
            EVIDENCE,
            "app.Shop\nhtml-route\t/products/{id}\tapp.Products#show\nopen-mapping\t/x\nhtml-route\t/\tapp.Home#index\n"
        );
        this.write(RESOURCES + "/static/index.html", "<html></html>\n");
        this.write(RESOURCES + "/static/help/faq.html", "<html></html>\n");
        this.write(RESOURCES + "/static/css/site.css", "a {}\n");
        WebSweepLaunch launch = this.prepare(artifact);
        assertEquals(List.of(), launch.problems());
        assertEquals(
            List.of(
                "/products/{id}\tapp.Products#show", "/\tapp.Home#index", "/help/faq.html\tstatic/help/faq.html",
                "/\tstatic/index.html"
            ),
            Files.readAllLines(this.module.resolve(INVENTORY))
        );
        assertEquals(
            Map.of(
                WebSweepLaunch.INVENTORY, this.module.resolve(INVENTORY).toString(),
                WebInterface.ARTIFACT, artifact.toAbsolutePath().toString(), "JAVA_HOME", "/opt/java"
            ),
            launch.environment()
        );
    }

    @Test
    @SneakyThrows
    void leavesTheInterfaceUnassessedWithoutAPackagedArtifactOrRoutes() {
        Path evidence = this.write(EVIDENCE, "html-route\t/\tapp.Home#index\n");
        Files.setLastModifiedTime(evidence, FileTime.from(Instant.ofEpochMilli(STARTED - 1)));
        WebSweepLaunch launch = this.prepare(this.module.resolve("target/missing.jar"));
        assertEquals(2, launch.problems().size(), "evidence an earlier invocation left is not this run's routes");
        assertTrue(launch.problems().getFirst().contains("it packaged none"), launch.problems().getFirst());
        assertTrue(launch.problems().getLast().contains("its route inventory is empty"), launch.problems().getLast());
        assertEquals(Map.of(), launch.environment());
        assertFalse(Files.exists(this.module.resolve(INVENTORY)), "nothing is handed to a sweep that cannot run");
    }

    @Test
    void failsAWebInterfaceNoNodeProjectJudged() {
        WebInterface web = new WebInterface(List.of("Pages.java declares a @Controller"), "");
        NodeGateResult skipped = new NodeGateResult(new Findings("e2e", List.of()), "not applicable", false);
        NodeGateResult swept = new NodeGateResult(new Findings("e2e", List.of()), "passed", true);
        Optional<String> unassessed = WebSweepLaunch.unassessed("shop", web, List.of(skipped));
        assertTrue(unassessed.orElseThrow().contains("the interface is unassessed"), unassessed.orElseThrow());
        assertEquals(Optional.empty(), WebSweepLaunch.unassessed("shop", web, List.of(skipped, swept)));
        assertEquals(
            Optional.empty(), WebSweepLaunch.unassessed("api", new WebInterface(List.of(), "none"), List.of(skipped)),
            "a module serving no page has nothing to assess"
        );
    }
}
