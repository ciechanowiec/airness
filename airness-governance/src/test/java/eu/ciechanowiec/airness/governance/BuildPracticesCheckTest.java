package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildPracticesCheckTest {

    private static final String CLEAN = """
        <project>
            <modelVersion>4.0.0</modelVersion>
            <parent>
                <groupId>eu.ciechanowiec</groupId>
                <artifactId>airness-parent</artifactId>
                <version>1</version>
            </parent>
            <artifactId>consumer</artifactId>
            <modules>
                <module>a</module>
            </modules>
            <scm>
                <url>https://example.com</url>
            </scm>
            <properties>
                <maven.compiler.release>25</maven.compiler.release>
                <java.version>${project.version}</java.version>
            </properties>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>org.bouncycastle</groupId>
                        <artifactId>bcprov-jdk18on</artifactId>
                        <version>1.80</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
            <build>
                <finalName>${project.artifactId}-${project.version}</finalName>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-dependency-plugin</artifactId>
                        <executions>
                            <execution>
                                <goals>
                                    <goal>resolve-sources</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </project>
        """;

    @Test
    void acceptsAProjectWrittenToCurrentPractice(@TempDir Path directory) {
        assertEquals(List.of(), offences(directory, CLEAN, List.of()));
    }

    @Test
    void refusesAJavaVersionBelowTheParentsRelease(@TempDir Path directory) {
        String pom = CLEAN.replace(
            "<maven.compiler.release>25</maven.compiler.release>",
            "<maven.compiler.release>17</maven.compiler.release><maven.compiler.source>1.8</maven.compiler.source>"
        ).replace(
            "<artifactId>maven-dependency-plugin</artifactId>",
            "<artifactId>maven-dependency-plugin</artifactId></plugin><plugin><groupId>x</groupId>"
                + "<artifactId>maven-compiler-plugin</artifactId><configuration><release>21</release>"
                + "<source>${project.version}</source></configuration>"
        );
        assertEquals(
            List.of("maven.compiler.release = 17", "maven.compiler.source = 1.8", "release = 21"),
            offences(directory, pom, List.of())
        );
    }

    @Test
    void refusesSupersededCoordinatesWhereverTheyAreDeclared(@TempDir Path directory) {
        String pom = CLEAN.replace("bcprov-jdk18on", "bcprov-jdk15on").replace(
            "<build>",
            "<profiles><profile><dependencies><dependency><groupId>junit</groupId><artifactId>junit</artifactId>"
                + "</dependency></dependencies></profile></profiles><build>"
        );
        assertEquals(
            List.of(
                "org.bouncycastle:bcprov-jdk15on -> the -jdk18on artifact of the same name",
                "junit:junit -> org.junit.jupiter:junit-jupiter",
                "build is written after profiles"
            ),
            offences(directory, pom, List.of())
        );
    }

    @Test
    void refusesUnversionedManagementAndAnonymousPluginGroups(@TempDir Path directory) {
        String pom = CLEAN.replace("<version>1.80</version>", "").replace(
            "<groupId>org.apache.maven.plugins</groupId>", ""
        );
        assertEquals(
            List.of("org.bouncycastle:bcprov-jdk18on", "maven-dependency-plugin"), offences(directory, pom, List.of())
        );
    }

    @Test
    void refusesPrefixlessExpressionsTheRetiredGoalAndElementOrder(@TempDir Path directory) {
        String pom = CLEAN.replace("${project.artifactId}-${project.version}", "${artifactId}-${pom.version}")
            .replace("<goal>resolve-sources</goal>", "<goal>sources</goal>")
            .replace("<scm>\n        <url>https://example.com</url>\n    </scm>\n    ", "")
            .replace("<modules>", "<scm><url>https://example.com</url></scm><modules>");
        assertEquals(
            List.of(
                "${artifactId} -> ${project.artifactId}", "${pom.version} -> ${project.version}",
                "maven-dependency-plugin:sources", "modules is written after scm"
            ),
            offences(directory, pom, List.of())
        );
    }

    @Test
    @SneakyThrows
    void refusesAWorkflowSettingUpAnOldJava(@TempDir Path directory) {
        String content = """
            steps:
              - uses: actions/setup-java@v4
                with:
                  java-version: '17'
              - uses: actions/setup-java@v4
                with:
                  java-version: 25
            """;
        Path workflow = Files.writeString(directory.resolve("verify.yml"), content);
        assertEquals(List.of("verify.yml: java-version 17"), offences(directory, CLEAN, List.of(workflow)));
    }

    @SneakyThrows
    private static List<String> offences(Path directory, String pom, List<Path> workflows) {
        Path file = Files.writeString(directory.resolve("pom.xml"), pom);
        return new BuildPracticesCheck(file, workflows).findings().stream()
            .flatMap(findings -> findings.offences().stream())
            .toList();
    }
}
