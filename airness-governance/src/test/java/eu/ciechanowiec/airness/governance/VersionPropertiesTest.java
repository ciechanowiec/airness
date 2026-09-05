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

class VersionPropertiesTest {

    private static final String TOMCAT = "Reference ${tomcat.version} from a <version> in this pom "
        + "or remove the property; a version property nothing in the pom reads changes no version "
        + "the build resolves";

    @Test
    void refusesAVersionPropertyNoVersionReferences() {
        String pom = """
            <project>
                <properties>
                    <tomcat.version>11.0.25</tomcat.version>
                </properties>
            </project>
            """;
        assertEquals(List.of(TOMCAT), problems(pom));
    }

    // The shape both consumer projects reached on their own, and the one the message asks for.
    @Test
    void acceptsAVersionPropertyAManagedDependencyReferences() {
        String pom = """
            <project>
                <properties>
                    <tomcat.version>11.0.25</tomcat.version>
                </properties>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.tomcat.embed</groupId>
                            <artifactId>tomcat-embed-core</artifactId>
                            <version>${tomcat.version}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
            """;
        assertTrue(problems(pom).isEmpty(), "a version reading the property is what makes it a pin");
    }

    @Test
    void acceptsAVersionPropertyAPluginReferences() {
        String pom = """
            <project>
                <properties>
                    <axe-core.version>4.13.0</axe-core.version>
                </properties>
                <build>
                    <plugins>
                        <plugin>
                            <artifactId>maven-surefire-plugin</artifactId>
                            <version>${axe-core.version}</version>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;
        assertTrue(problems(pom).isEmpty());
    }

    @Test
    void acceptsAVersionPropertyAnAttributeReferences() {
        String pom = """
            <project>
                <properties>
                    <tomcat.version>11.0.25</tomcat.version>
                </properties>
                <use value="${tomcat.version}"/>
            </project>
            """;
        assertTrue(problems(pom).isEmpty());
    }

    // The trap is not a Spring one. A java.version under this parent is read by nothing either.
    @Test
    void refusesAnUnreferencedJavaVersion() {
        String pom = """
            <project>
                <properties>
                    <java.version>25</java.version>
                </properties>
            </project>
            """;
        assertEquals(1, problems(pom).size());
    }

    @Test
    void ignoresAPropertyWhoseNameDoesNotEndInVersion() {
        String pom = """
            <project>
                <properties>
                    <tomcat>11.0.25</tomcat>
                </properties>
            </project>
            """;
        assertTrue(problems(pom).isEmpty());
    }

    // The shape of this harness's own root pom, which declares a version for the parent below it.
    @Test
    void ignoresAnAirnessOwnedVersionProperty() {
        String pom = """
            <project>
                <properties>
                    <spring-boot.version>4.1.1</spring-boot.version>
                </properties>
            </project>
            """;
        assertTrue(problems(pom).isEmpty(), "another rule refuses that name with a message of its own");
    }

    @Test
    void doesNotCountAReferenceInAComment() {
        String pom = """
            <project>
                <properties>
                    <tomcat.version>11.0.25</tomcat.version>
                </properties>
                <!-- ${tomcat.version} is documentation, not a Maven use. -->
            </project>
            """;
        assertEquals(List.of(TOMCAT), problems(pom));
    }

    @Test
    void countsAReferenceFromAnotherProperty() {
        String pom = """
            <project>
                <properties>
                    <tomcat.version>11.0.25</tomcat.version>
                    <server.version>${tomcat.version}</server.version>
                </properties>
                <use>${server.version}</use>
            </project>
            """;
        assertTrue(problems(pom).isEmpty(), "Maven resolves a property value into a property value");
    }

    @Test
    void readsAProfilePropertyBlock() {
        String pom = """
            <project>
                <profiles>
                    <profile>
                        <id>release</id>
                        <properties>
                            <tomcat.version>11.0.25</tomcat.version>
                        </properties>
                    </profile>
                </profiles>
            </project>
            """;
        assertEquals(List.of(TOMCAT), problems(pom));
    }

    @Test
    void reportsOnceForANameTwoBlocksDeclare() {
        String pom = """
            <project>
                <properties>
                    <tomcat.version>11.0.25</tomcat.version>
                </properties>
                <profiles>
                    <profile>
                        <id>release</id>
                        <properties>
                            <tomcat.version>11.0.24</tomcat.version>
                        </properties>
                    </profile>
                </profiles>
            </project>
            """;
        assertEquals(List.of(TOMCAT), problems(pom));
    }

    @Test
    void repositoryPomsReferenceEveryVersionPropertyTheyDeclare() {
        Path root = repository();
        List<String> findings = Repository.trackedFiles(root).stream()
            .filter(path -> "pom.xml".equals(path.getFileName().toString()))
            .flatMap(
                path -> VersionProperties.problems(read(path))
                    .map(problem -> root.relativize(path) + ": " + problem)
            )
            .sorted()
            .toList();
        assertEquals(List.of(), findings);
    }

    private static List<String> problems(CharSequence pom) {
        return VersionProperties.problems(Xml.parse(pom.toString()).getDocumentElement()).toList();
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
