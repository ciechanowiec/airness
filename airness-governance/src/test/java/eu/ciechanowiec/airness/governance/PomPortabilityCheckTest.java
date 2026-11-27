package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomPortabilityCheckTest {

    private static final String POM = "pom.xml";

    @TempDir
    private Path root;

    @Test
    void acceptsPropertyAndFileActivationPluginDirectoriesAndPathsInsideTheRepository() {
        String pom = project(
            """
                    <parent>
                        <groupId>eu.ciechanowiec</groupId>
                        <artifactId>airness-parent</artifactId>
                        <version>1</version>
                        <relativePath/>
                    </parent>
                    <modules>
                        <module>web</module>
                        <module>tools/../api</module>
                    </modules>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-checkstyle-plugin</artifactId>
                                <configuration>
                                    <sourceDirectories>
                                        <sourceDirectory>src/main/java</sourceDirectory>
                                    </sourceDirectories>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                    <profiles>
                        <profile>
                            <id>fast</id>
                            <activation>
                                <property>
                                    <name>skipTests</name>
                                </property>
                                <file>
                                    <exists>${basedir}/fast.txt</exists>
                                </file>
                            </activation>
                        </profile>
                    </profiles>
                """
        );
        assertTrue(Verdicts.clean(this.findings(POM, pom)));
    }

    @Test
    void refusesEveryActivationThatDependsOnTheMachineOrOnOtherProfiles() {
        String pom = project(
            """
                    <profiles>
                        <profile>
                            <id>default</id>
                            <activation>
                                <activeByDefault>false</activeByDefault>
                            </activation>
                        </profile>
                        <profile>
                            <activation>
                                <os>
                                    <family>mac</family>
                                </os>
                                <jdk>[25,)</jdk>
                            </activation>
                        </profile>
                        <profile>
                            <id>ci</id>
                            <activation>
                                <property>
                                    <name>env.CI</name>
                                </property>
                            </activation>
                        </profile>
                        <profile>
                            <id>local</id>
                            <activation>
                                <property>
                                    <name>!env.CI</name>
                                </property>
                            </activation>
                        </profile>
                    </profiles>
                """
        );
        assertEquals(
            List.of(
                "profile default is active by default", "profile without an id activates by operating system",
                "profile without an id activates by JDK", "profile ci activates by environment variable env.CI",
                "profile local activates by environment variable !env.CI"
            ),
            Verdicts.offences(this.findings(POM, pom), "Activate no profile")
        );
    }

    @Test
    void refusesReportingInTheProjectAndInAProfile() {
        String pom = project(
            """
                    <reporting/>
                    <profiles>
                        <profile>
                            <id>site</id>
                            <reporting/>
                        </profile>
                    </profiles>
                """
        );
        assertEquals(
            List.of("<reporting>", "profile site declares <reporting>"),
            Verdicts.offences(this.findings(POM, pom), "site reporting")
        );
    }

    @Test
    void refusesPrerequisitesOutsideAMavenPlugin() {
        String prerequisites = "<prerequisites><maven>3.10.0</maven></prerequisites>";
        assertEquals(
            List.of("<prerequisites> in a project that is not a Maven plugin"),
            Verdicts.offences(this.findings(POM, project(prerequisites)), "prerequisites")
        );
        assertEquals(
            List.of(),
            Verdicts.offences(
                this.findings(POM, project("<packaging>maven-plugin</packaging>" + prerequisites)),
                "prerequisites"
            )
        );
    }

    @Test
    void refusesADistributionStatusAndARelocationAnywhere() {
        String pom = project(
            """
                    <distributionManagement>
                        <status>deployed</status>
                    </distributionManagement>
                    <profiles>
                        <profile>
                            <id>moved</id>
                            <distributionManagement>
                                <relocation>
                                    <groupId>org.sample.moved</groupId>
                                </relocation>
                            </distributionManagement>
                        </profile>
                    </profiles>
                """
        );
        assertEquals(
            List.of(
                "<distributionManagement><status>",
                "profile moved declares <distributionManagement><relocation>"
            ),
            Verdicts.offences(this.findings(POM, pom), "distribution status")
        );
    }

    @Test
    void refusesEveryMovedSourceAndOutputDirectory() {
        String pom = project(
            """
                    <build>
                        <sourceDirectory>src</sourceDirectory>
                        <scriptSourceDirectory>scripts</scriptSourceDirectory>
                    </build>
                    <profiles>
                        <profile>
                            <id>alt</id>
                            <build>
                                <outputDirectory>out</outputDirectory>
                                <testOutputDirectory>test-out</testOutputDirectory>
                                <testSourceDirectory>test</testSourceDirectory>
                            </build>
                        </profile>
                    </profiles>
                """
        );
        assertEquals(
            List.of(
                "<build> moves scriptSourceDirectory scripts", "<build> moves sourceDirectory src",
                "profile alt declares <build> moves outputDirectory out",
                "profile alt declares <build> moves testOutputDirectory test-out",
                "profile alt declares <build> moves testSourceDirectory test"
            ),
            Verdicts.offences(this.findings(POM, pom), "conventional source")
        );
    }

    @Test
    void refusesEveryModuleAndParentPathThatLeavesTheRepository() {
        String pom = project(
            """
                    <parent>
                        <groupId>org.sample</groupId>
                        <artifactId>company-parent</artifactId>
                        <version>1</version>
                        <relativePath>../../company/pom.xml</relativePath>
                    </parent>
                    <modules>
                        <module>sibling/../../../outside</module>
                        <module>..\\..\\elsewhere</module>
                        <module>/opt/shared</module>
                        <module>C:\\shared</module>
                    </modules>
                    <profiles>
                        <profile>
                            <id>extra</id>
                            <modules>
                                <module>../../extra</module>
                            </modules>
                        </profile>
                    </profiles>
                """
        );
        assertEquals(
            List.of(
                "module ../../extra lies outside the repository",
                "module ..\\..\\elsewhere lies outside the repository",
                "module /opt/shared lies outside the repository", "module C:\\shared lies outside the repository",
                "module sibling/../../../outside lies outside the repository",
                "parent relativePath ../../company/pom.xml lies outside the repository"
            ),
            Verdicts.offences(this.findings("app/pom.xml", pom), "inside the repository").stream().sorted().toList()
        );
    }

    @Test
    void acceptsAModulePomThatReachesItsParentInsideTheRepository() {
        String pom = project(
            """
                    <parent>
                        <groupId>org.sample</groupId>
                        <artifactId>tracker</artifactId>
                        <version>1</version>
                        <relativePath>../pom.xml</relativePath>
                    </parent>
                """
        );
        assertEquals(List.of(), Verdicts.offences(this.findings("app/pom.xml", pom), "inside the repository"));
    }

    @SneakyThrows
    private List<Findings> findings(String relative, String pom) {
        Path file = this.root.resolve(relative);
        Files.createDirectories(file.resolveSibling(""));
        Files.writeString(file, pom);
        return new PomPortabilityCheck(file, this.root).findings();
    }

    private static String project(String body) {
        return "<project>\n    <modelVersion>4.0.0</modelVersion>\n" + body + "\n</project>\n";
    }
}
