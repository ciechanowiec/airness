package eu.ciechanowiec.airness.governance;

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
        String pom = """
            <project>
                <profiles>
                    <profile>
                        <properties><skipTests>true</skipTests></properties>
                    </profile>
                </profiles>
            </project>
            """;
        assertEquals(
            List.of("Remove child property skipTests; it can bypass the Airness verdict"),
            this.problems(pom)
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
        String pom = plugin(
            "maven-compiler-plugin",
            """
                <configuration><compilerArgs combine.self="override"/></configuration>
                """
        );
        assertTrue(this.problems(pom).getFirst().contains("compilerArgs"));
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
}
