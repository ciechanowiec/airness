package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

/**
 * A project profile never reuses an id an Airness parent owns, since Maven would activate it beside the harness's
 * own, or in place of the invocation's choice when it is active by default.
 */
class MavenModelProfilesTest {

    @TempDir
    private Path directory;

    @Test
    void rejectsAProfileActiveByDefaultThatReusesTheFormatId() {
        String pom = """
            <project>
                <profiles>
                    <profile>
                        <id>format</id>
                        <activation><activeByDefault>true</activeByDefault></activation>
                    </profile>
                </profiles>
            </project>
            """;
        assertEquals(
            List.of("Remove child profile format; Airness owns every profile of that id, so name yours apart"),
            this.problems(pom),
            "a format profile active by default would switch the tree comparisons off without -Pformat"
        );
    }

    @Test
    void rejectsEveryProfileIdTheHarnessOwnsWhateverItsActivation() {
        String pom = """
            <project>
                <profiles>
                    <profile><id>extended</id></profile>
                    <profile><id> delta </id><activation><property><name>!never</name></property></activation></profile>
                    <profile><id>release</id></profile>
                    <profile><id>airness-report-only</id></profile>
                    <profile><id>airness-anything-later</id></profile>
                    <profile><id>tree-drift</id></profile>
                    <profile><id>formatting</id></profile>
                </profiles>
            </project>
            """;
        assertEquals(
            List.of("airness-anything-later", "airness-report-only", "delta", "extended", "release"),
            this.problems(pom).stream()
                .map(problem -> problem.substring("Remove child profile ".length(), problem.indexOf(';')))
                .toList(),
            "an id of the project's own stays, and every harness id is refused, however it would activate"
        );
    }

    @Test
    void refusesEveryProfileIdTheAirnessParentsDeclare() {
        Path repository = SelfModules.repository();
        List<String> declared = parents(repository).stream()
            .flatMap(MavenModelProfilesTest::profileIds)
            .distinct()
            .toList();
        assertTrue(declared.contains("format"), "a guard that read no parent profile proves nothing");
        String pom = declared.stream()
            .map(id -> "<profile><id>" + id + "</id></profile>")
            .collect(Collectors.joining("", "<project><profiles>", "</profiles></project>"));
        assertEquals(
            declared.size(), this.problems(pom).size(),
            "a profile a parent adds is refused in a project the day it is added: " + declared
        );
    }

    @SneakyThrows
    private static List<Path> parents(Path repository) {
        try (Stream<Path> modules = Files.list(repository)) {
            return modules.filter(module -> module.getFileName().toString().startsWith("airness-parent"))
                .map(module -> module.resolve("pom.xml"))
                .filter(Files::isRegularFile)
                .sorted()
                .toList();
        }
    }

    @SneakyThrows
    private static Stream<String> profileIds(Path pom) {
        Element project = Xml.parse(Files.readString(pom)).getDocumentElement();
        return Xml.firstChild(project, "profiles").stream()
            .flatMap(profiles -> Xml.children(profiles, "profile").stream())
            .map(Xml::idTextOrEmpty);
    }

    @SneakyThrows
    private List<String> problems(String pom) {
        Path path = Files.writeString(this.directory.resolve("pom.xml"), pom);
        return MavenModelPolicy.problems(path);
    }
}
