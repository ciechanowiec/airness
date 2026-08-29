package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Reports the defects that are settled before a line of the module is compiled.
 *
 * <p>Every other Spring rule Airness states reads something a developer wrote in Java or in YAML. These
 * read what the module declares it is built from, which is where a whole class of defect is decided: an
 * application with no probes, a schema nothing creates, two web stacks of which one silently wins.
 * None of them can be seen in a source file, because in each case the defect is a declaration that is
 * absent or a pairing that is present, rather than anything written down.
 *
 * <p>Four of the five ask only of a module the Boot plugin repackages. A library module of a Spring Boot
 * project legitimately maps a schema it does not create and legitimately publishes no probes, since it
 * is not the thing that gets deployed. Repackaging is what says which module is.
 */
@UtilityClass
final class SpringModelRules {

    private static final String BOOT = "org.springframework.boot";
    private static final String DEVTOOLS = "spring-boot-devtools";
    private static final String DATA_JPA = "spring-boot-starter-data-jpa";
    private static final String ACTUATOR = "spring-boot-starter-actuator";
    /*
     * Boot 4 renamed the servlet starter and kept the old name beside it, so a project on this platform
     * may be written either way and a rule that knew one spelling would pass over half of them.
     */
    private static final List<String> SERVLET
        = List.of("spring-boot-starter-web", "spring-boot-starter-webmvc");
    private static final String REACTIVE = "spring-boot-starter-webflux";
    private static final List<String> MIGRATION_TOOLS = List.of("flyway", "liquibase");

    /**
     * Development tooling declared in a way that lets it travel.
     *
     * @param pom          the module's pom, which the offence names
     * @param dependencies the module's effective dependencies
     * @return one offence per declaration that is not optional
     */
    static List<String> shippedTooling(String pom, Collection<SpringDependency> dependencies) {
        return dependencies.stream()
            .filter(dependency -> boot(dependency, DEVTOOLS))
            .filter(dependency -> !dependency.optional())
            .map(
                _ -> offence(
                    pom,
                    "spring-boot-devtools is declared without <optional>true</optional>, so the restart"
                        + " classloader, the live-reload server and the remote debug endpoint it installs"
                        + " travel into the artifact this module publishes and into everything that"
                        + " depends on it"
                )
            )
            .toList();
    }

    /**
     * A deployable module that maps a schema and declares nothing that would create one.
     *
     * @param pom          the module's pom, which the offence names
     * @param dependencies the module's effective dependencies
     * @param repackaged   whether the Boot plugin turns this module into a deployable archive
     * @return the offence, or none
     */
    static List<String> unmigratedSchema(
        String pom, Collection<SpringDependency> dependencies, boolean repackaged
    ) {
        boolean unmigrated = declares(dependencies, DATA_JPA) && !migrated(dependencies);
        return repackaged && unmigrated
            ? List.of(
                offence(
                    pom,
                    "spring-boot-starter-data-jpa maps a schema that nothing here creates: Hibernate is"
                        + " forbidden from generating one, and neither Flyway nor Liquibase is declared,"
                        + " so the schema is applied by hand at deploy time and drifts from the mapping"
                        + " it was written against"
                )
            )
            : List.of();
    }

    /**
     * A deployable module publishing none of the endpoints an orchestrator reads.
     *
     * @param pom          the module's pom, which the offence names
     * @param dependencies the module's effective dependencies
     * @param repackaged   whether the Boot plugin turns this module into a deployable archive
     * @return the offence, or none
     */
    static List<String> missingActuator(
        String pom, Collection<SpringDependency> dependencies, boolean repackaged
    ) {
        return repackaged && !declares(dependencies, ACTUATOR)
            ? List.of(
                offence(
                    pom,
                    "the module is repackaged into a deployable archive and declares no"
                        + " spring-boot-starter-actuator, so it publishes no liveness probe, no readiness"
                        + " probe and no metrics, and an orchestrator cannot tell a process that started"
                        + " from one that works"
                )
            )
            : List.of();
    }

    /**
     * Both web stacks declared where Boot will start only one of them.
     *
     * @param pom          the module's pom, which the offence names
     * @param dependencies the module's effective dependencies
     * @return the offence, or none
     */
    static List<String> doubledWebStack(String pom, Collection<SpringDependency> dependencies) {
        boolean servlet = SERVLET.stream().anyMatch(artifact -> declares(dependencies, artifact));
        return servlet && declares(dependencies, REACTIVE)
            ? List.of(
                offence(
                    pom,
                    "a servlet starter and spring-boot-starter-webflux are declared together, and Boot"
                        + " settles that by starting the servlet stack and no reactive server, so every"
                        + " handler written against WebFlux is mapped by nothing while the pair reads as"
                        + " though the choice between them had been made"
                )
            )
            : List.of();
    }

    /**
     * Auto-configuration declared by the application rather than by a starter it imports.
     *
     * @param declarations the registration files found under the module's resource roots
     * @param repackaged   whether the Boot plugin turns this module into a deployable archive
     * @return one offence per file
     */
    static List<String> ownAutoConfiguration(Collection<String> declarations, boolean repackaged) {
        return repackaged
            ? declarations.stream().map(SpringModelRules::imported).toList()
            : List.of();
    }

    private static String imported(String declaration) {
        return offence(
            declaration,
            "auto-configuration is the mechanism a starter publishes for the applications that import"
                + " it, so declaring it inside the application subjects its own beans to ordering,"
                + " conditions and overrides that a plain @Bean would have settled where it was written"
        );
    }

    private static boolean migrated(Collection<SpringDependency> dependencies) {
        return dependencies.stream()
            .map(SpringDependency::artifactId)
            .anyMatch(artifact -> MIGRATION_TOOLS.stream().anyMatch(artifact::startsWith));
    }

    private static boolean declares(Collection<SpringDependency> dependencies, String artifact) {
        return dependencies.stream().anyMatch(dependency -> boot(dependency, artifact));
    }

    private static boolean boot(SpringDependency dependency, String artifact) {
        return BOOT.equals(dependency.groupId()) && artifact.equals(dependency.artifactId());
    }

    private static String offence(String where, String consequence) {
        return where + ": " + consequence;
    }
}
