package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Refuses the reactive stack wherever a project's own model declares it.
 *
 * <p>Work runs on virtual threads, which serve the concurrency the reactive stack exists for while keeping the
 * blocking servlet stack, its transactions and its stack traces. What is judged is what the raw model names, in
 * every profile, under dependency management and on a plugin's classpath, with or without a version, because a
 * starter is written without one beneath the Spring Boot import. What a dependency brings in on its own is never
 * judged: a library that talks to its server reactively, such as an AI client or a Redis driver, still serves a
 * blocking caller, and refusing it would refuse the library rather than the project's choice. Reactor itself is
 * therefore not named, since a project that receives a library's publisher declares it to wait on it.
 */
@UtilityClass
final class ReactiveDeclarations {

    private static final String BOOT = SpringModelRules.BOOT;
    private static final String CLOUD = SpringModelRules.CLOUD;
    private static final String SPRING = "org.springframework";
    private static final String ANY = "*";
    private static final String SERVLET = "the servlet stack, spring-boot-starter-web, serves a request on a virtual"
        + " thread";
    private static final String REST_CLIENT = "RestClient calls and MockMvcTester or RestTestClient tests a servlet"
        + " application";
    private static final String NETTY = "Tomcat serves the servlet stack and RestClient calls through the JDK client";
    private static final String JDBC = "JDBC, spring-boot-starter-jdbc or spring-boot-starter-data-jpa, blocks a"
        + " virtual thread only";
    private static final String BLOCKING_DATA = "the blocking starter of the same store blocks a virtual thread only";
    private static final String RSOCKET = "HTTP on the servlet stack or a WebSocket endpoint of Spring MVC serves the"
        + " same exchange";
    private static final String GATEWAY = "Spring Cloud Gateway Server MVC,"
        + " spring-cloud-starter-gateway-server-webmvc, runs on the servlet stack";
    private static final String STREAMS = "a blocking call on a virtual thread does the work a reactive pipeline"
        + " describes";

    private static final List<Entry> ENTRIES = List.of(
        new Entry(SPRING, "spring-webflux", SERVLET),
        new Entry(BOOT, "spring-boot-starter-webflux*", SERVLET),
        new Entry(BOOT, "spring-boot-webflux*", SERVLET),
        new Entry(BOOT, "spring-boot-starter-webclient*", REST_CLIENT),
        new Entry(BOOT, "spring-boot-webclient*", REST_CLIENT),
        new Entry(BOOT, "spring-boot-starter-webtestclient*", REST_CLIENT),
        new Entry(BOOT, "spring-boot-webtestclient*", REST_CLIENT),
        new Entry(BOOT, "spring-boot-starter-reactor-netty*", NETTY),
        new Entry(BOOT, "spring-boot-reactor-netty*", NETTY),
        new Entry("io.projectreactor.netty", ANY, NETTY),
        new Entry("io.projectreactor.kafka", ANY, STREAMS),
        new Entry(BOOT, "spring-boot-starter-data-r2dbc*", JDBC),
        new Entry(BOOT, "spring-boot-starter-r2dbc*", JDBC),
        new Entry(BOOT, "spring-boot-r2dbc*", JDBC),
        new Entry(BOOT, "spring-boot-data-r2dbc*", JDBC),
        new Entry(SPRING, "spring-r2dbc", JDBC),
        new Entry("org.springframework.data", "spring-data-r2dbc", JDBC),
        new Entry("io.r2dbc", ANY, JDBC),
        new Entry(ANY, "r2dbc-*", JDBC),
        new Entry(BOOT, "spring-boot-starter-data-redis-reactive*", BLOCKING_DATA),
        new Entry(BOOT, "spring-boot-starter-data-cassandra-reactive*", BLOCKING_DATA),
        new Entry(BOOT, "spring-boot-starter-rsocket*", RSOCKET),
        new Entry(BOOT, "spring-boot-rsocket*", RSOCKET),
        new Entry("io.rsocket", ANY, RSOCKET),
        new Entry(CLOUD, "spring-cloud-starter-gateway", GATEWAY),
        new Entry(CLOUD, "spring-cloud-starter-gateway-server-webflux*", GATEWAY),
        new Entry(CLOUD, "spring-cloud-gateway-server", GATEWAY),
        new Entry(CLOUD, "spring-cloud-gateway-server-webflux*", GATEWAY),
        new Entry("io.reactivex", ANY, STREAMS),
        new Entry("io.reactivex.rxjava2", ANY, STREAMS),
        new Entry("io.reactivex.rxjava3", ANY, STREAMS),
        new Entry("io.smallrye.reactive", "mutiny*", STREAMS)
    );

    /**
     * Every reactive artifact the raw model declares.
     *
     * @param root the raw model
     * @return one problem per declared coordinate, naming what replaces it
     */
    static Stream<String> problems(Element root) {
        return Stream.of(
            DeclaredCoordinates.plugins(root),
            DeclaredCoordinates.dependencies(root),
            DeclaredCoordinates.paths(root)
        ).flatMap(stream -> stream)
            .map(ReactiveDeclarations::refusal)
            .flatMap(Optional::stream)
            .distinct();
    }

    private static Optional<String> refusal(Node declaration) {
        String group = Xml.text(declaration, "groupId").orElse("");
        String artifact = Xml.text(declaration, "artifactId").orElse("");
        return ENTRIES.stream()
            .filter(entry -> entry.covers(group, artifact))
            .findFirst()
            .map(
                entry -> "Remove " + group + ':' + artifact + "; Airness runs work on virtual threads, and "
                    + entry.replacement()
            );
    }

    private record Entry(String group, String artifact, String replacement) {

        private boolean covers(String declaredGroup, String declaredArtifact) {
            return NamePattern.matches(this.group, declaredGroup)
                && NamePattern.matches(this.artifact, declaredArtifact);
        }
    }
}
