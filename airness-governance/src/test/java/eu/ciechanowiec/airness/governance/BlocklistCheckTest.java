package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The module half of the blocklist refuses a declared coordinate where it is written, a resolved one
 * by naming the resolved set, and an image a Testcontainers literal names by its line.
 */
class BlocklistCheckTest {

    private static final List<Path> ROOTS = List.of(Path.of("src/test/java"));
    private static final String HEADLINE = "module reaches";
    private static final String CLEAN_POM = """
        <project>
            <dependencies>
                <dependency>
                    <groupId>org.postgresql</groupId>
                    <artifactId>postgresql</artifactId>
                    <version>42.7.4</version>
                </dependency>
            </dependencies>
        </project>
        """;
    private static final String PROFILED_POM = """
        <project>
            <properties>
                <mongo.version>5.3.0</mongo.version>
            </properties>
            <build>
                <extensions>
                    <extension>
                        <groupId>com.gradle</groupId>
                        <artifactId>develocity-maven-extension</artifactId>
                        <version>1.23</version>
                    </extension>
                </extensions>
            </build>
            <profiles>
                <profile>
                    <id>dormant</id>
                    <dependencies>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-sync</artifactId>
                            <version>${mongo.version}</version>
                        </dependency>
                    </dependencies>
                </profile>
            </profiles>
        </project>
        """;
    private static final String IMAGE_TEST = """
        package sample;

        import org.testcontainers.utility.DockerImageName;

        class ImageTest {
            static final DockerImageName STORE = DockerImageName.parse("mongo:7");
            static final DockerImageName DATABASE = DockerImageName.parse("postgres");
        }
        """;

    private static BlocklistCheck check(Path root, List<DeclaredCoordinate> resolved) {
        Path pom = root.resolve("pom.xml");
        return new BlocklistCheck(root, pom, ROOTS, ModuleCoordinates.of(pom, Map.of(), resolved));
    }

    @Test
    void passesAModuleThatReachesNothingRefused() {
        Path root = new GitFixture("blocklist-clean").write("pom.xml", CLEAN_POM).root();
        BlocklistCheck check = check(root, List.of(new DeclaredCoordinate("org.postgresql", "postgresql", "42.7.4")));
        assertTrue(Verdicts.clean(check.findings()), "PostgreSQL and its driver are open");
        assertEquals(2, check.scanned(), "the declaration and the resolved artifact were both read");
    }

    @Test
    void refusesADeclarationInADormantProfileAndABuildExtension() {
        Path root = new GitFixture("blocklist-profile").write("pom.xml", PROFILED_POM).root();
        List<String> offences = Verdicts.offences(check(root, List.of()).findings(), HEADLINE);
        assertEquals(2, offences.size(), "the dormant driver and the extension: " + offences);
        assertTrue(
            offences.getFirst().startsWith("pom.xml: org.mongodb:mongodb-driver-sync:5.3.0 - "), offences.getFirst()
        );
        assertTrue(offences.getLast().contains("develocity-maven-extension"), offences.getLast());
    }

    @Test
    void refusesAResolvedArtifactByNamingTheResolvedSet() {
        Path root = new GitFixture("blocklist-resolved").write("pom.xml", CLEAN_POM).root();
        List<DeclaredCoordinate> resolved = List.of(new DeclaredCoordinate("org.mongodb", "bson", "5.3.0"));
        List<String> offences = Verdicts.offences(check(root, resolved).findings(), HEADLINE);
        assertEquals(1, offences.size());
        assertTrue(
            offences.getFirst().startsWith("pom.xml (resolved set): org.mongodb:bson:5.3.0 - "), offences.getFirst()
        );
    }

    @Test
    void refusesAnImageATestNamesByItsLine() {
        Path root = new GitFixture("blocklist-literal")
            .write("pom.xml", CLEAN_POM)
            .write("src/test/java/sample/ImageTest.java", IMAGE_TEST)
            .root();
        List<String> offences = Verdicts.offences(check(root, List.of()).findings(), HEADLINE);
        assertEquals(2, offences.size(), "the refused image and the unpinned one: " + offences);
        assertTrue(offences.getFirst().startsWith("src/test/java/sample/ImageTest.java:6: mongo:7 - "));
        assertTrue(offences.getLast().contains("ImageTest.java:7: postgres - nothing pins"));
    }
}
