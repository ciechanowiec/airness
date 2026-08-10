package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class PomPropertyOrderTest {

    @Test
    void acceptsPropertiesOrderedByLocalUseAfterAlphabeticalUnreferencedProperties() {
        String pom = """
            <project>
                <properties>
                    <alpha>1</alpha>
                    <zeta>1</zeta>
                    <early>1</early>
                    <later>1</later>
                </properties>
                <first>${early}</first>
                <second>${later}</second>
            </project>
            """;
        assertTrue(problems(pom).isEmpty());
    }

    @Test
    void reportsTheCompleteExpectedPropertyOrder() {
        String pom = """
            <project>
                <properties>
                    <zeta>1</zeta>
                    <later>1</later>
                    <alpha>1</alpha>
                    <early>1</early>
                </properties>
                <first>${early}</first>
                <second>${later}</second>
            </project>
            """;
        assertEquals(
            List.of(
                "Order project properties as alpha, zeta, early, later; properties not locally referenced "
                    + "come first alphabetically, then properties follow first use"
            ),
            problems(pom)
        );
    }

    @Test
    void ignoresReferencesInsidePropertyDeclarations() {
        String pom = """
            <project>
                <properties>
                    <zeta>${alpha}</zeta>
                    <alpha>1</alpha>
                    <local>1</local>
                </properties>
                <use>${local}</use>
            </project>
            """;
        assertTrue(problems(pom).getFirst().contains("alpha, zeta, local"));
    }

    @Test
    void ordersReferencesFromOneElementAlphabetically() {
        String pom = """
            <project>
                <properties>
                    <orphan>1</orphan>
                    <alpha>1</alpha>
                    <beta>1</beta>
                    <gamma>1</gamma>
                </properties>
                <use value="${beta}">${alpha}<![CDATA[${gamma}]]></use>
            </project>
            """;
        assertTrue(problems(pom).isEmpty());
    }

    @Test
    void countsAReferenceInsidePluginConfigurationProperties() {
        String pom = """
            <project>
                <properties>
                    <zeta>1</zeta>
                    <alpha>1</alpha>
                </properties>
                <build><plugins><plugin><configuration><properties>
                    <entry>${alpha}</entry>
                </properties></configuration></plugin></plugins></build>
            </project>
            """;
        assertTrue(problems(pom).isEmpty());
    }

    @Test
    void ordersEachProfilePropertyBlockIndependently() {
        String pom = """
            <project><profiles><profile>
                <id>analysis</id>
                <properties>
                    <later>1</later>
                    <early>1</early>
                </properties>
                <first>${early}</first>
                <second>${later}</second>
            </profile></profiles></project>
            """;
        assertTrue(problems(pom).getFirst().startsWith("Order profile analysis properties as early, later"));
    }

    @Test
    void doesNotCountPropertyReferencesInComments() {
        String pom = """
            <project>
                <properties>
                    <zeta>1</zeta>
                    <alpha>1</alpha>
                </properties>
                <!-- ${alpha} is documentation, not a Maven use. -->
            </project>
            """;
        assertTrue(problems(pom).getFirst().contains("alpha, zeta"));
    }

    @Test
    void repositoryPomsFollowThePropertyOrder() {
        Path root = repository();
        List<String> findings = Repository.trackedFiles(root).stream()
            .filter(path -> "pom.xml".equals(path.getFileName().toString()))
            .flatMap(
                path -> PomPropertyOrder.problems(read(path)).stream()
                    .map(problem -> root.relativize(path) + ": " + problem)
            )
            .sorted()
            .toList();
        assertEquals(List.of(), findings);
    }

    private static List<String> problems(CharSequence pom) {
        return PomPropertyOrder.problems(Xml.parse(pom.toString()).getDocumentElement());
    }

    private static Path repository() {
        Path current = Path.of("").toAbsolutePath();
        return Stream.of(current, current.getParent())
            .filter(path -> Files.exists(path.resolve("airness-parent/pom.xml")))
            .findFirst()
            .orElseThrow();
    }

    @SneakyThrows
    private static Element read(Path path) {
        return Xml.parse(Files.readString(path)).getDocumentElement();
    }
}
