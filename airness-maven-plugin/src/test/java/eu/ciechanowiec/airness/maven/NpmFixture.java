package eu.ciechanowiec.airness.maven;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;

/**
 * A module declaring one node project, with the license check configuration it inherits and the npm evidence
 * its gates left in its build directory.
 */
@UtilityClass
final class NpmFixture {

    static final String INVENTORY = """
        {"contract": 1, "packages": [
          {"name": "left-pad", "version": "1.3.0", "license": "WTFPL", "direct": true, "installed": true},
          {"name": "@scope/kit", "version": "2.0.0", "license": "(MIT OR Apache-2.0)", "direct": false,
           "installed": true},
          {"name": "@esbuild/linux-x64", "version": "0.25.0", "license": "", "direct": false, "installed": false}
        ]}
        """;

    static final String SBOM = """
        {"bomFormat": "CycloneDX", "specVersion": "1.6", "version": 1,
         "metadata": {"component": {"type": "application", "bom-ref": "pkg:npm/web@1.0.0", "name": "web",
           "version": "1.0.0"}},
         "components": [
           {"type": "library", "bom-ref": "pkg:npm/left-pad@1.3.0", "name": "left-pad", "version": "1.3.0",
            "purl": "pkg:npm/left-pad@1.3.0", "licenses": [{"expression": "WTFPL"}], "scope": "required"},
           {"type": "library", "bom-ref": "pkg:npm/%40scope/kit@2.0.0", "group": "@scope", "name": "kit",
            "version": "2.0.0", "purl": "pkg:npm/%40scope/kit@2.0.0", "licenses": [{"expression": "MIT OR Apache-2.0"}],
            "scope": "required"}
         ],
         "dependencies": [
           {"ref": "pkg:npm/web@1.0.0", "dependsOn": ["pkg:npm/left-pad@1.3.0"]},
           {"ref": "pkg:npm/left-pad@1.3.0", "dependsOn": ["pkg:npm/%40scope/kit@2.0.0"]}
         ]}
        """;

    /**
     * An OSV API that lists one advisory for the first of the three packages the inventory resolves, and one
     * withdrawn advisory that the scan passes over.
     *
     * @return the server, which the caller closes
     */
    static ArchiveServer osv() {
        ArchiveServer server = new ArchiveServer();
        server.serve(
            "/v1/querybatch",
            ("{\"results\": [{}, {\"vulns\": [{\"id\": \"GHSA-0000-0000-0001\"}, {\"id\": \"GHSA-0000-0000-0002\"}]},"
                + " {}]}").getBytes(StandardCharsets.UTF_8)
        );
        server.serve(
            "/v1/vulns/GHSA-0000-0000-0001",
            ("{\"id\": \"GHSA-0000-0000-0001\", \"summary\": \"Prototype pollution\","
                + " \"database_specific\": {\"severity\": \"HIGH\"}}").getBytes(StandardCharsets.UTF_8)
        );
        server.serve(
            "/v1/vulns/GHSA-0000-0000-0002",
            "{\"id\": \"GHSA-0000-0000-0002\", \"withdrawn\": \"2026-01-01T00:00:00Z\"}".getBytes(
                StandardCharsets.UTF_8
            )
        );
        return server;
    }

    /**
     * A module whose node project is {@code web}, with evidence written when {@code evidence} is set.
     *
     * @param directory the module directory
     * @param evidence  whether the gates left their evidence
     * @return the module
     */
    @SneakyThrows
    static MavenProject module(Path directory, boolean evidence) {
        Files.createDirectories(directory.resolve("web"));
        Files.writeString(directory.resolve("web/package.json"), "{\"name\": \"web\", \"version\": \"1.0.0\"}\n");
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("shop");
        model.setVersion("1");
        model.setPackaging("pom");
        model.addProperty("airness.node.projects", "web");
        Build build = new Build();
        build.setDirectory(directory.resolve("target").toString());
        Plugin license = new Plugin();
        license.setGroupId("org.codehaus.mojo");
        license.setArtifactId("license-maven-plugin");
        license.setConfiguration(configuration());
        build.addPlugin(license);
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        project.setFile(directory.resolve("pom.xml").toFile());
        if (evidence) {
            Path results = Files.createDirectories(directory.resolve("target/airness/node/projects/web"));
            Files.writeString(results.resolve("dependencies.json"), INVENTORY);
            Files.writeString(results.resolve("bom.cdx.json"), SBOM);
        }
        return project;
    }

    @SneakyThrows
    private static Xpp3Dom configuration() {
        return Xpp3DomBuilder.build(
            new StringReader(
                "<configuration><includedLicenses><includedLicense>MIT</includedLicense>"
                    + "<includedLicense>Apache-2.0</includedLicense></includedLicenses>"
                    + "<licenseMerges><licenseMerge>MIT|The MIT License</licenseMerge></licenseMerges></configuration>"
            )
        );
    }
}
