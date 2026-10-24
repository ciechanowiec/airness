package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A build step that names a Node executable is refused wherever the pom writes it, and a step that runs
 * anything else is left alone, since what an allowed process starts is outside the guarantee.
 */
class NodeExecutionCheckTest {

    private static final String EXEC_POM = """
        <project>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.codehaus.mojo</groupId>
                        <artifactId>exec-maven-plugin</artifactId>
                        <version>3.6.4</version>
                        <executions>
                            <execution>
                                <id>build-ui</id>
                                <configuration>
                                    <executable>/usr/local/bin/NPM.cmd</executable>
                                </configuration>
                            </execution>
                            <execution>
                                <id>build-script</id>
                                <configuration>
                                    <executable>sh</executable>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </project>
        """;
    private static final String PROFILED_POM = """
        <project>
            <profiles>
                <profile>
                    <id>dormant</id>
                    <build>
                        <pluginManagement>
                            <plugins>
                                <plugin>
                                    <groupId>org.codehaus.mojo</groupId>
                                    <artifactId>exec-maven-plugin</artifactId>
                                    <configuration>
                                        <executable>${frontend.runner}</executable>
                                    </configuration>
                                </plugin>
                            </plugins>
                        </pluginManagement>
                    </build>
                </profile>
            </profiles>
        </project>
        """;
    private static final String ANTRUN_POM = """
        <project>
            <build>
                <plugins>
                    <plugin>
                        <artifactId>maven-antrun-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>bundle</id>
                                <configuration>
                                    <target>
                                        <echo message="bundling"/>
                                        <exec executable="pnpm" dir="ui"><arg value="build"/></exec>
                                        <exec executable="make"/>
                                    </target>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                    <plugin>
                        <groupId>org.example</groupId>
                        <artifactId>other-plugin</artifactId>
                        <configuration>
                            <executable>node</executable>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </project>
        """;

    @TempDir
    private Path root;

    @SneakyThrows
    private List<String> offences(String pom, Map<String, String> properties) {
        Path file = Files.writeString(this.root.resolve("pom.xml"), pom);
        return new NodeExecutionCheck(this.root, file, properties).findings().getFirst().offences();
    }

    @Test
    @SneakyThrows
    void refusesAnExecExecutionNamingANodeToolWhateverItsPathOrSuffix() {
        List<String> offences = offences(EXEC_POM, Map.of());
        assertEquals(1, offences.size(), "only the npm step is refused, the shell step is not: " + offences);
        String offence = offences.getFirst();
        assertTrue(offence.startsWith("pom.xml: exec-maven-plugin execution build-ui runs /usr/local/bin/NPM.cmd"));
        assertTrue(offence.contains("airness:node-run"), "the refusal names the replacement: " + offence);
    }

    @Test
    @SneakyThrows
    void readsAnExecutableHeldInAPropertyInsideAnInactiveProfile() {
        assertEquals(
            List.of(), offences(PROFILED_POM, Map.of()), "a property nothing resolves names no tool"
        );
        List<String> offences = offences(PROFILED_POM, Map.of("frontend.runner", "yarn"));
        assertEquals(1, offences.size(), "the property resolves to yarn: " + offences);
        assertTrue(offences.getFirst().contains("exec-maven-plugin configuration runs ${frontend.runner}"));
    }

    @Test
    @SneakyThrows
    void refusesAnAntExecTaskAndLeavesOtherPluginsAndToolsAlone() {
        List<String> offences = offences(ANTRUN_POM, Map.of());
        assertEquals(1, offences.size(), "only the pnpm task is refused: " + offences);
        assertTrue(offences.getFirst().startsWith("pom.xml: maven-antrun-plugin execution bundle runs pnpm"));
    }

    @Test
    @SneakyThrows
    void refusesTheExecExecutablePropertyWrittenInAProfileOrInherited() {
        String profiled = """
            <project><profiles><profile><id>ui</id>
                <properties><exec.executable>npx</exec.executable></properties>
            </profile></profiles></project>
            """;
        List<String> offences = offences(profiled, Map.of());
        assertEquals(1, offences.size(), String.valueOf(offences));
        assertTrue(offences.getFirst().startsWith("pom.xml: the property exec.executable runs npx"));
        assertEquals(1, offences("<project/>", Map.of("exec.executable", "/usr/bin/node")).size(), "inherited");
        assertEquals(List.of(), offences("<project/>", Map.of("exec.executable", "make")));
    }
}
