package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenModelPolicyTest {

    @TempDir
    private Path directory;

    @Test
    void rejectsAVerdictBypassInsideAnInactiveProfile() {
        String skipped = """
            <project>
                <profiles>
                    <profile>
                        <properties><skipTests>true</skipTests></properties>
                    </profile>
                </profiles>
            </project>
            """;
        String parameters = """
            <project>
                <profiles><profile><properties>
                    <maven.compiler.parameters>false</maven.compiler.parameters>
                </properties></profile></profiles>
            </project>
            """;
        assertAll(
            () -> assertEquals(
                List.of("Remove child property skipTests; it can bypass the Airness verdict"),
                this.problems(skipped)
            ),
            () -> assertEquals(
                List.of(
                    "Remove child property maven.compiler.parameters; it can bypass the Airness verdict"
                ),
                this.problems(parameters)
            )
        );
    }

    @Test
    void rejectsAChildExecutionThatReplacesAnInheritedOne() {
        String pom = plugin(
            "maven-enforcer-plugin",
            """
                <executions><execution><id>airness-enforce-dependencies</id></execution></executions>
                """
        );
        assertEquals(
            List.of(
                "Remove child execution airness-enforce-dependencies; Airness owns every airness-* execution"
            ),
            this.problems(pom)
        );
    }

    @Test
    void rejectsCompilerConfigurationThatRemovesAnalysis() {
        String arguments = plugin(
            "maven-compiler-plugin",
            """
                <configuration><compilerArgs combine.self="override"/></configuration>
                """
        );
        String parameters = plugin(
            "maven-compiler-plugin",
            """
                <configuration><parameters>false</parameters></configuration>
                """
        );
        assertAll(
            () -> assertTrue(this.problems(arguments).getFirst().contains("compilerArgs")),
            () -> assertTrue(this.problems(parameters).getFirst().contains("parameters"))
        );
    }

    @Test
    void rejectsSurefireConfigurationThatSelectsPartOfTheSuite() {
        String pom = plugin(
            "maven-surefire-plugin",
            """
                <configuration><includes><include>OneTest.java</include></includes></configuration>
                """
        );
        assertTrue(this.problems(pom).getFirst().contains("includes"));
    }

    @Test
    void rejectsSystemScopedDependencies() {
        String pom = """
            <project><dependencies><dependency>
                <groupId>sample</groupId><artifactId>local</artifactId>
                <scope>system</scope><systemPath>/tmp/local.jar</systemPath>
            </dependency></dependencies></project>
            """;
        assertEquals(
            List.of("Remove system-scoped dependency sample:local; use a repository coordinate"),
            this.problems(pom)
        );
    }

    @Test
    void rejectsASystemPathWithoutAnExplicitSystemScope() {
        String pom = """
            <project><dependencies><dependency>
                <groupId>sample</groupId><artifactId>local</artifactId>
                <systemPath>/tmp/local.jar</systemPath>
            </dependency></dependencies></project>
            """;
        assertEquals(
            List.of("Remove system-scoped dependency sample:local; use a repository coordinate"),
            this.problems(pom)
        );
    }

    @Test
    void rejectsAMergeOverrideThatErasesInheritedExecutions() {
        String pom = plugin(
            "maven-enforcer-plugin",
            """
                <executions combine.self="override"/>
                """
        );
        assertTrue(this.problems(pom).getFirst().contains("combine.self=override"));
    }

    @Test
    void rejectsEnforcerConfigurationThatSelectsInheritedRules() {
        String pom = plugin(
            "maven-enforcer-plugin",
            """
                <configuration><rulesToSkip>dependencyConvergence</rulesToSkip></configuration>
                """
        );
        assertTrue(this.problems(pom).getFirst().contains("rulesToSkip"));
    }

    @Test
    void acceptsRulesInAnAdditionalEnforcerExecution() {
        String pom = plugin(
            "maven-enforcer-plugin",
            """
                <executions><execution>
                    <id>enforce-project-dependencies</id>
                    <configuration><rules><bannedDependencies/></rules></configuration>
                </execution></executions>
                """
        );
        assertTrue(this.problems(pom).isEmpty());
    }

    @Test
    void acceptsExtensionConfigurationThatDoesNotWeakenTheHarness() {
        String pom = plugin(
            "maven-surefire-plugin",
            """
                <configuration><argLine>-Duser.timezone=UTC</argLine></configuration>
                """
        );
        assertTrue(this.problems(pom).isEmpty());
    }

    @Test
    void acceptsConfigurationOnAPluginOutsideMavensStandardGroup() {
        String pom = """
            <project><build><plugins><plugin>
                <groupId>sample</groupId><artifactId>maven-surefire-plugin</artifactId>
                <configuration><includes><include>OneTest.java</include></includes></configuration>
            </plugin><plugin>
                <artifactId>sample-plugin</artifactId>
                <configuration><skip>true</skip></configuration>
            </plugin></plugins></build></project>
            """;
        assertTrue(this.problems(pom).isEmpty());
    }

    @Test
    void rejectsAMutationPluginTheHarnessNoLongerRuns() {
        String pom = """
            <project><build><plugins><plugin>
                <groupId>org.pitest</groupId><artifactId>pitest-maven</artifactId>
            </plugin></plugins></build></project>
            """;
        assertEquals(
            List.of("Remove org.pitest:pitest-maven; Airness runs no mutation analysis"),
            this.problems(pom)
        );
    }

    // The ban is by group rather than by artifact, so an engine or a report module nobody thought to
    // name is refused on the same line as the plugin itself.
    @Test
    void rejectsAMutationArtifactThePolicyNeverNames() {
        String pom = """
            <project><dependencies><dependency>
                <groupId>org.pitest</groupId><artifactId>pitest-command-line</artifactId>
            </dependency></dependencies></project>
            """;
        assertEquals(
            List.of("Remove org.pitest:pitest-command-line; Airness runs no mutation analysis"),
            this.problems(pom)
        );
    }

    @Test
    void rejectsAMutationPluginHeldInManagementOrAnInactiveProfile() {
        String managed = """
            <project><build><pluginManagement><plugins><plugin>
                <groupId>org.pitest</groupId><artifactId>pitest-maven</artifactId>
            </plugin></plugins></pluginManagement></build></project>
            """;
        String dormant = """
            <project><profiles><profile><build><plugins><plugin>
                <groupId>org.pitest</groupId><artifactId>pitest-maven</artifactId>
            </plugin></plugins></build></profile></profiles></project>
            """;
        assertEquals(
            List.of("Remove org.pitest:pitest-maven; Airness runs no mutation analysis"),
            this.problems(managed),
            "a managed declaration supplies the version a child then inherits"
        );
        assertEquals(
            List.of("Remove org.pitest:pitest-maven; Airness runs no mutation analysis"),
            this.problems(dormant),
            "a profile nobody activates today is a declaration waiting for the flag that does"
        );
    }

    @Test
    void rejectsAMutationEngineOnAnotherPluginsClasspath() {
        String pom = """
            <project><build><plugins><plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <dependencies><dependency>
                    <groupId>org.pitest</groupId><artifactId>pitest-junit5-plugin</artifactId>
                </dependency></dependencies>
            </plugin></plugins></build></project>
            """;
        assertEquals(
            List.of("Remove org.pitest:pitest-junit5-plugin; Airness runs no mutation analysis"),
            this.problems(pom)
        );
    }

    // Coordinate-shaped XML inside a configuration block is content the surrounding plugin reads, not a
    // declaration Maven resolves, so the ban has to stop at the same boundary every other rule does.
    @Test
    void acceptsAMutationCoordinateNamedInsidePluginConfiguration() {
        String pom = plugin(
            "sample-plugin",
            """
                <configuration><artifacts><artifact>
                    <groupId>org.pitest</groupId><artifactId>pitest-maven</artifactId>
                </artifact></artifacts></configuration>
                """
        );
        assertTrue(this.problems(pom).isEmpty());
    }

    private static String plugin(String artifact, String body) {
        return """
            <project><build><plugins><plugin>
                <artifactId>%s</artifactId>%s
            </plugin></plugins></build></project>
            """.formatted(artifact, body);
    }

    @SneakyThrows
    private List<String> problems(CharSequence pom) {
        Path path = Files.writeString(this.directory.resolve("pom.xml"), pom);
        return MavenModelPolicy.problems(path);
    }

    @Test
    void rejectsAnAirnessSettingTheProjectDoesNotOwn() {
        String pom = """
            <project>
                <properties><airness.suppression.rate>9</airness.suppression.rate></properties>
            </project>
            """;
        assertEquals(
            List.of(
                "Remove child property airness.suppression.rate; it can bypass the Airness verdict"
            ),
            this.problems(pom),
            "the namespace is refused by default, so a setting invented later is refused the day it is written"
        );
    }

    @Test
    void acceptsTheAirnessSettingsTheProjectDoesOwn() {
        String pom = """
            <project>
                <properties>
                    <airness.assets.unmanaged>.gitignore</airness.assets.unmanaged>
                    <airness.package.root>com.example</airness.package.root>
                    <airness.test.timeout>45 s</airness.test.timeout>
                    <airness.typography.excludes>docs</airness.typography.excludes>
                </properties>
            </project>
            """;
        assertEquals(List.of(), this.problems(pom), "these four are documented as the project's own");
    }
}
