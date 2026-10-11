package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

class IntegrationPluginsPolicyTest {

    private static final String FAILSAFE = "maven-failsafe-plugin";
    private static final List<String> OPTIONAL_PLUGINS = List.of(
        FAILSAFE, "maven-assembly-plugin", "build-helper-maven-plugin"
    );

    @TempDir
    private Path directory;

    @Test
    void acceptsUnversionedOptionalPlugins() {
        for (String artifact : OPTIONAL_PLUGINS) {
            assertTrue(ManagedVersions.problems(this.pom(plugin(artifact, ""))).isEmpty());
        }
    }

    @Test
    void refusesConsumerVersionsOfManagedPlugins() {
        for (String artifact : OPTIONAL_PLUGINS) {
            List<String> problems = ManagedVersions.problems(this.pom(plugin(artifact, "<version>1</version>")));
            assertEquals(1, problems.size());
            assertTrue(problems.getFirst().contains(artifact));
        }
    }

    @Test
    void refusesChangesToInheritedIntegrationTestInputs() {
        List<String> settings = List.of(
            "skip", "skipITs", "skipTests", "testFailureIgnore", "test", "includes", "excludes", "groups",
            "excludedGroups", "configurationParameters", "summaryFile", "summaryFiles", "includesFile", "excludesFile",
            "failIfNoTests", "skipAfterFailureCount", "airness.spring.context.evidence.file"
        );
        for (String setting : settings) {
            String configuration = "<configuration><%s>true</%s></configuration>".formatted(setting, setting);
            List<String> problems = MavenModelPolicy.problems(this.pom(plugin(FAILSAFE, configuration)));
            assertEquals(1, problems.size());
            assertTrue(problems.getFirst().contains("configuration " + setting));
        }
    }

    @Test
    void refusesIntegrationTestBypassPropertiesInInactiveProfiles() {
        for (String setting : List.of("skipITs", "it.test")) {
            String declaration = """
                <project><profiles><profile><id>inactive</id>
                    <properties><%s>true</%s></properties>
                </profile></profiles></project>
                """.formatted(setting, setting);
            assertEquals(
                List.of("Remove child property " + setting + "; it can bypass the Airness verdict"),
                MavenModelPolicy.problems(this.pom(declaration))
            );
        }
    }

    @Test
    void refusesRemovalOfTheIntegrationVerificationExecution() {
        String execution = """
            <executions><execution><id>airness-integration-verify</id><phase>none</phase></execution></executions>
            """;
        assertTrue(
            MavenModelPolicy.problems(this.pom(plugin(FAILSAFE, execution))).getFirst()
                .contains("Remove child execution airness-integration-verify")
        );
    }

    @Test
    void refusesDiscardingTheIntegrationPluginConfiguration() {
        String configuration = "<configuration combine.self=\"override\"/>";
        assertTrue(
            MavenModelPolicy.problems(this.pom(plugin(FAILSAFE, configuration))).getFirst()
                .contains("discards inherited Airness configuration")
        );
    }

    @Test
    void permitsRuntimeSettingsForIntegrationTests() {
        String configuration = """
            <configuration><systemPropertyVariables><user.timezone>UTC</user.timezone></systemPropertyVariables>
                <argLine>@{argLine} -Dfile.encoding=UTF-8</argLine>
            </configuration>
            """;
        assertTrue(MavenModelPolicy.problems(this.pom(plugin(FAILSAFE, configuration))).isEmpty());
    }

    @Test
    void usesTheSameJunitSettingsForBothTestRunners() {
        Element unit = ProjectFiles.child(
            ProjectFiles.managed(ProjectFiles.rootPom(), "maven-surefire-plugin"),
            "configuration", "properties", "configurationParameters"
        );
        Element integration = ProjectFiles.child(
            ProjectFiles.managed(ProjectFiles.rootPom(), FAILSAFE),
            "configuration", "properties", "configurationParameters"
        );
        assertEquals(unit.getTextContent().strip(), integration.getTextContent().strip());
    }

    @Test
    void finalizesEvidenceAfterIntegrationTests() {
        List<String> executions = List.of(
            "airness-coverage-report", "airness-coverage-check", "airness-coverage-evidence", "airness-governance"
        );
        for (String execution : executions) {
            assertEquals(
                "verify", Xml.text(ProjectFiles.execution(ProjectFiles.parentPom(), execution), "phase").orElseThrow()
            );
        }
    }

    private static String plugin(String artifact, String contents) {
        String group = "build-helper-maven-plugin".equals(artifact)
            ? "org.codehaus.mojo" : "org.apache.maven.plugins";
        return "<project><build><plugins><plugin><groupId>%s</groupId><artifactId>%s</artifactId>%s"
            .formatted(group, artifact, contents) + "</plugin></plugins></build></project>";
    }

    @SneakyThrows
    private Path pom(String content) {
        return Files.writeString(this.directory.resolve("pom.xml"), content);
    }
}
