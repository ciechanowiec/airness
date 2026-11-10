package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeclaredExclusionsTest {

    private static final String PROPERTY = "airness.coverage.excluded.classes";

    @TempDir
    private Path root;

    @Test
    void countsEachPatternOfTheProjectFileThatDeclaresIt() {
        Model declaring = new Model();
        declaring.addProperty(PROPERTY, "com.example.Application -- the launcher; com.example.Shell -- a wrapper");
        Profile profile = new Profile();
        profile.addProperty(PROPERTY, "com.example.Probe -- read by the container alone");
        declaring.addProfile(profile);

        assertEquals(
            List.of(
                "pom.xml: coverage exclusion com.example.Application", "pom.xml: coverage exclusion com.example.Shell",
                "pom.xml: coverage exclusion com.example.Probe"
            ),
            DeclaredExclusions.java(this.root, List.of(this.project(declaring, "pom.xml"))),
            "the reasons are prose, so each entry is named by its pattern, profiles included"
        );
    }

    @Test
    void leavesAnInheritingModuleOutOfTheCount() {
        Model declaring = new Model();
        declaring.addProperty(PROPERTY, "com.example.Application -- the launcher");

        assertEquals(
            List.of("pom.xml: coverage exclusion com.example.Application"),
            DeclaredExclusions.java(
                this.root, List.of(this.project(declaring, "pom.xml"), this.project(new Model(), "web/pom.xml"))
            ),
            "the raw model of the module that inherits the setting declares nothing"
        );
    }

    @Test
    void namesAProjectWithoutAFileByItsId() {
        Model declaring = new Model();
        declaring.setGroupId("com.example");
        declaring.setArtifactId("orders");
        declaring.setVersion("1.0.0");
        declaring.addProperty(PROPERTY, "com.example.Application -- the launcher");
        MavenProject project = new MavenProject(declaring);
        project.setOriginalModel(declaring);

        assertEquals(
            List.of("com.example:orders:jar:1.0.0: coverage exclusion com.example.Application"),
            DeclaredExclusions.java(this.root, List.of(project))
        );
    }

    @Test
    @SneakyThrows
    void countsEachCoverageExcludeOfEveryNodeManifest() {
        Path web = Files.createDirectories(this.root.resolve("web"));
        Files.writeString(
            web.resolve("package.json"),
            """
                {"airness": {"coverageExclude": [
                  {"pattern": "src/cli.ts", "reason": "a shell"},
                  {"pattern": " ", "reason": "names nothing"},
                  {"pattern": "src/main.ts", "reason": "the entry"}
                ]}}
                """
        );
        Path member = Files.createDirectories(web.resolve("packages/ui"));
        Files.writeString(
            member.resolve("package.json"),
            "{\"airness\": {\"coverageExclude\": [{\"pattern\": \"src/boot.ts\", \"reason\": \"glue\"}]}}"
        );

        assertEquals(
            List.of(
                "web/package.json: coverageExclude src/cli.ts", "web/package.json: coverageExclude src/main.ts",
                "web/packages/ui/package.json: coverageExclude src/boot.ts"
            ),
            DeclaredExclusions.node(this.root, List.of(web, web))
        );
    }

    @Test
    @SneakyThrows
    void readsNothingFromAManifestThatIsNoJson() {
        Path web = Files.createDirectories(this.root.resolve("web"));
        Files.writeString(web.resolve("package.json"), "{ not json");

        assertEquals(List.of(), DeclaredExclusions.node(this.root, List.of(web)));
    }

    private MavenProject project(Model original, String file) {
        MavenProject project = new MavenProject(new Model());
        project.setOriginalModel(original);
        project.setFile(new File(this.root.toFile(), file));
        return project;
    }
}
