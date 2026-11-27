package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class MavenModelReactiveTest {

    private static final String DECLARED = """
        <project><dependencies><dependency>
            <groupId>%s</groupId><artifactId>%s</artifactId>
        </dependency></dependencies></project>
        """;

    private static final String BOOT = "org.springframework.boot";
    private static final String CLOUD = "org.springframework.cloud";

    @TempDir
    private Path directory;

    @Test
    void rejectsADeclaredReactiveArtifactAndNamesItsReplacement() {
        List<List<String>> refused = List.of(
            List.of("org.springframework", "spring-webflux", "spring-boot-starter-web"),
            List.of(BOOT, "spring-boot-starter-webflux", "spring-boot-starter-web"),
            List.of(BOOT, "spring-boot-starter-webflux-test", "spring-boot-starter-web"),
            List.of(BOOT, "spring-boot-webclient", "RestClient"),
            List.of(BOOT, "spring-boot-starter-webtestclient", "RestTestClient"),
            List.of("io.projectreactor.netty", "reactor-netty-http", "Tomcat"),
            List.of("io.projectreactor.kafka", "reactor-kafka", "blocking call"),
            List.of(BOOT, "spring-boot-starter-data-r2dbc", "spring-boot-starter-jdbc"),
            List.of("org.springframework.data", "spring-data-r2dbc", "spring-boot-starter-jdbc"),
            List.of("io.r2dbc", "r2dbc-spi", "spring-boot-starter-jdbc"),
            List.of("org.postgresql", "r2dbc-postgresql", "spring-boot-starter-jdbc"),
            List.of(BOOT, "spring-boot-starter-data-redis-reactive", "blocking starter"),
            List.of(BOOT, "spring-boot-starter-rsocket", "WebSocket"),
            List.of("io.rsocket", "rsocket-core", "WebSocket"),
            List.of(CLOUD, "spring-cloud-starter-gateway", "gateway-server-webmvc"),
            List.of(CLOUD, "spring-cloud-starter-gateway-server-webflux", "gateway-server-webmvc"),
            List.of("io.reactivex.rxjava3", "rxjava", "blocking call"),
            List.of("io.smallrye.reactive", "mutiny", "blocking call")
        );
        assertAll(
            refused.stream()
                .map(row -> (Executable) () -> this.assertRefused(row.getFirst(), row.get(1), row.getLast()))
        );
    }

    private void assertRefused(String group, String artifact, String replacement) {
        List<String> problems = this.problems(DECLARED.formatted(group, artifact));
        assertEquals(1, problems.size(), problems::toString);
        assertTrue(
            problems.getFirst().startsWith("Remove " + group + ':' + artifact + "; Airness runs work on virtual"),
            problems::toString
        );
        assertTrue(problems.getFirst().contains(replacement), problems::toString);
    }

    @Test
    void rejectsAReactiveStarterHeldInManagementAPluginClasspathOrAnInactiveProfile() {
        String managed = """
            <project><dependencyManagement><dependencies><dependency>
                <groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webflux</artifactId>
                <version>4.1.2</version>
            </dependency></dependencies></dependencyManagement></project>
            """;
        String classpath = """
            <project><build><plugins><plugin>
                <artifactId>sample-plugin</artifactId>
                <dependencies><dependency>
                    <groupId>io.r2dbc</groupId><artifactId>r2dbc-spi</artifactId><version>1.0.0.RELEASE</version>
                </dependency></dependencies>
            </plugin></plugins></build></project>
            """;
        String dormant = """
            <project><profiles><profile><dependencies><dependency>
                <groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webflux</artifactId>
            </dependency></dependencies></profile></profiles></project>
            """;
        assertEquals(1, this.problems(managed).size(), "a managed declaration is inherited by every child");
        assertEquals(1, this.problems(classpath).size(), "a plugin classpath is declared by the project");
        assertEquals(1, this.problems(dormant).size(), "a profile nobody activates today is still a declaration");
    }

    // Reactor reaches a project beneath libraries that talk to their servers reactively, and a project that
    // receives such a library's publisher declares it to wait on it, so naming it would refuse the library.
    @Test
    void acceptsTheServletStackItsGatewayAndReactorItself() {
        List<String> accepted = List.of(
            DECLARED.formatted(BOOT, "spring-boot-starter-web"),
            DECLARED.formatted(BOOT, "spring-boot-starter-webmvc"),
            DECLARED.formatted(CLOUD, "spring-cloud-starter-gateway-server-webmvc"),
            DECLARED.formatted(CLOUD, "spring-cloud-gateway-server-mvc"),
            DECLARED.formatted("io.projectreactor", "reactor-core"),
            DECLARED.formatted(BOOT, "spring-boot-starter-data-redis")
        );
        accepted.forEach(pom -> assertEquals(List.of(), this.problems(pom), pom));
    }

    @Test
    void acceptsAReactiveCoordinateNamedInsidePluginConfiguration() {
        String pom = """
            <project><build><plugins><plugin>
                <artifactId>sample-plugin</artifactId>
                <configuration><artifacts><artifact>
                    <groupId>org.springframework</groupId><artifactId>spring-webflux</artifactId>
                </artifact></artifacts></configuration>
            </plugin></plugins></build></project>
            """;
        assertEquals(List.of(), this.problems(pom));
    }

    @SneakyThrows
    private List<String> problems(CharSequence pom) {
        Path path = Files.writeString(this.directory.resolve("pom.xml"), pom);
        return MavenModelPolicy.problems(path);
    }
}
