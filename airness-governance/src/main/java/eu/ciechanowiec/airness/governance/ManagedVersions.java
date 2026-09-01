package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Enforces the dependency and plugin ownership policy on a raw child pom.
 *
 * <p>The raw pom is read instead of Maven's effective model because the effective model preserves the
 * value but not its owner. Direct declarations in inactive profiles count too: an inactive escape hatch
 * is still a second source of truth waiting for a command to activate it.
 *
 * <p>Which of the two policies a coordinate falls under is decided by whether {@code airness-parent}
 * declares it, rather than by how central the plugin feels. A plugin the parent declares is supplied, and
 * a child repeating it would be running the harness twice under two configurations. A plugin the parent
 * only pins a version for is an extension: the child is the only one who can bind it to anything, so
 * forbidding the declaration would leave a pinned version nobody is allowed to use.
 */
@UtilityClass
public final class ManagedVersions {

    private static final String MAVEN_PLUGIN_GROUP = "org.apache.maven.plugins";

    private static final List<Coordinate> COORDINATES = List.of(
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-clean-plugin", "maven-clean-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-resources-plugin", "maven-resources-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-compiler-plugin", "maven-compiler-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-jar-plugin", "maven-jar-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-install-plugin", "maven-install-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-deploy-plugin", "maven-deploy-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-shade-plugin", "maven-shade-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-source-plugin", "maven-source-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-javadoc-plugin", "maven-javadoc-plugin.version"),
        allowedPlugin("org.codehaus.mojo", "exec-maven-plugin", "exec-maven-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-gpg-plugin", "maven-gpg-plugin.version"),
        allowedPlugin(
            "org.sonatype.central",
            "central-publishing-maven-plugin",
            "central-publishing-maven-plugin.version"
        ),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-dependency-plugin", "maven-dependency-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-surefire-plugin", "maven-surefire-plugin.version"),
        allowedPlugin(MAVEN_PLUGIN_GROUP, "maven-enforcer-plugin", "maven-enforcer-plugin.version"),
        allowedPlugin("org.codehaus.mojo", "versions-maven-plugin", "versions-maven-plugin.version"),
        suppliedPlugin("org.jacoco", "jacoco-maven-plugin", "jacoco-maven-plugin.version"),
        suppliedPlugin("org.ec4j.maven", "editorconfig-maven-plugin", "editorconfig-maven-plugin.version"),
        suppliedPlugin(
            "com.hubspot.maven.plugins",
            "prettier-maven-plugin",
            "prettier-maven-plugin.version"
        ),
        suppliedPlugin("org.codehaus.mojo", "license-maven-plugin", "license-maven-plugin.version"),
        suppliedPlugin("org.owasp", "dependency-check-maven", "dependency-check-maven.version"),
        suppliedPlugin("org.openrewrite.maven", "rewrite-maven-plugin", "rewrite-maven-plugin.version"),
        suppliedPlugin("eu.ciechanowiec", "airness-maven-plugin", "airness.version"),
        suppliedPlugin(MAVEN_PLUGIN_GROUP, "maven-checkstyle-plugin", "maven-checkstyle-plugin.version"),
        suppliedPlugin(MAVEN_PLUGIN_GROUP, "maven-pmd-plugin", "maven-pmd-plugin.version"),
        suppliedPlugin(
            "com.github.spotbugs",
            "spotbugs-maven-plugin",
            "spotbugs-maven-plugin.version"
        ),
        suppliedDependency("org.projectlombok", "lombok", "lombok.version"),
        suppliedDependency("org.junit.jupiter", "junit-jupiter", "junit.version"),
        suppliedDependency("org.junit.jupiter", "junit-jupiter-api", "junit.version"),
        suppliedDependency("org.junit.jupiter", "junit-jupiter-engine", "junit.version"),
        suppliedDependency("org.junit.jupiter", "junit-jupiter-params", "junit.version"),
        suppliedDependency("eu.ciechanowiec", "airness-annotations", "airness.version"),
        suppliedDependency("eu.ciechanowiec", "airness-config", "airness.version"),
        suppliedDependency("eu.ciechanowiec", "airness-spring-evidence", "airness.version"),
        suppliedDependency("org.apache.maven", "maven-artifact", "maven-artifact.version"),
        suppliedDependency("org.attoparser", "attoparser", "attoparser.version"),
        suppliedDependency("org.jspecify", "jspecify", "jspecify.version"),
        suppliedDependency(
            "com.github.spotbugs",
            "spotbugs-annotations",
            "spotbugs-annotations.version"
        ),
        suppliedDependency("com.google.errorprone", "error_prone_core", "error-prone.version"),
        suppliedDependency("com.uber.nullaway", "nullaway", "nullaway.version"),
        suppliedDependency("com.puppycrawl.tools", "checkstyle", "checkstyle.version"),
        suppliedDependency("net.sourceforge.pmd", "pmd-core", "pmd.version"),
        suppliedDependency("net.sourceforge.pmd", "pmd-java", "pmd.version"),
        suppliedDependency("net.sourceforge.pmd", "pmd-javascript", "pmd.version"),
        suppliedDependency("net.sourceforge.pmd", "pmd-jsp", "pmd.version"),
        suppliedDependency("com.qulice", "qulice-maven-plugin", "qulice-maven-plugin.version"),
        suppliedDependency("org.openrewrite.recipe", "rewrite-apache", "rewrite-apache.version"),
        suppliedDependency(
            "org.openrewrite.recipe",
            "rewrite-logging-frameworks",
            "rewrite-logging-frameworks.version"
        ),
        suppliedDependency(
            "org.openrewrite.recipe",
            "rewrite-migrate-java",
            "rewrite-migrate-java.version"
        ),
        suppliedDependency(
            "org.openrewrite.recipe",
            "rewrite-static-analysis",
            "rewrite-static-analysis.version"
        ),
        suppliedDependency(
            "org.openrewrite.recipe",
            "rewrite-testing-frameworks",
            "rewrite-testing-frameworks.version"
        ),
        suppliedDependency(
            "org.codehaus.mojo", "extra-enforcer-rules", "extra-enforcer-rules.version"
        ),
        /*
         * The Spring platform, owned by airness-parent-spring-boot rather than by the pom above it. The
         * bill of materials is supplied, because a project importing a second one would manage the same
         * coordinates at a version the harness never read. The plugin is only allowed a version, because
         * repackaging turns one module of a reactor into an executable archive and which module that is
         * belongs to the project.
         */
        suppliedDependency("org.junit", "junit-bom", "junit.version"),
        allowedPlugin(
            "org.springframework.boot", "spring-boot-maven-plugin", "spring-boot.version"
        ),
        suppliedDependency(
            "org.springframework.boot", "spring-boot-dependencies", "spring-boot.version"
        )
    );

    /**
     * The version properties of every coordinate above, and the values that are not versions but decide
     * what a check reads. The two Docker images belong here for the same reason a threshold does: an image
     * a project can repoint is a check a project can replace with one that finds nothing, and the digest
     * pin in the parent would then be advice rather than a rule.
     */
    private static final List<String> IMAGE_PROPERTIES = List.of(
        "gitleaks.image",
        "qodana.image"
    );
    private static final Set<String> PROTECTED_PROPERTIES = Stream.concat(
        Stream.concat(
            COORDINATES.stream().map(Coordinate::property),
            IMAGE_PROPERTIES.stream()
        ),
        Stream.of("maven.compiler.release", "airness.dependency-check.fail-build-on-cvss")
    ).collect(Collectors.toUnmodifiableSet());

    /**
     * Every child declaration that tries to replace or redeclare an Airness-owned coordinate.
     *
     * @param pom raw child pom
     * @return problems ordered by their message
     */
    public static List<String> problems(Path pom) {
        Element root = Xml.parse(read(pom)).getDocumentElement();
        return Stream.concat(coordinateProblems(root), propertyProblems(root)).sorted().toList();
    }

    /**
     * The root properties that declare Airness-owned container images.
     *
     * @return immutable image-property names
     */
    public static List<String> imageProperties() {
        return List.copyOf(IMAGE_PROPERTIES);
    }

    static List<Coordinate> coordinates() {
        return COORDINATES;
    }

    static Set<String> protectedProperties() {
        return PROTECTED_PROPERTIES;
    }

    private static Stream<String> coordinateProblems(Element root) {
        return COORDINATES.stream().flatMap(coordinate -> coordinateProblems(root, coordinate));
    }

    private static Stream<String> coordinateProblems(Element root, Coordinate coordinate) {
        List<Node> declarations = declarations(root, coordinate).toList();
        return declarations.isEmpty() ? Stream.empty() : declarationProblems(coordinate, declarations);
    }

    private static Stream<String> declarationProblems(Coordinate coordinate, Collection<Node> declarations) {
        if (coordinate.supplied()) {
            return Stream.of(coordinate.declarationProblem());
        }
        return declarations.stream().anyMatch(ManagedVersions::versioned)
            ? Stream.of(coordinate.versionProblem())
            : Stream.empty();
    }

    private static Stream<Node> declarations(Element root, Coordinate coordinate) {
        Stream<Node> declarations = switch (coordinate.kind()) {
            case DEPENDENCY -> Stream.concat(
                DeclaredCoordinates.dependencies(root), DeclaredCoordinates.paths(root)
            );
            case PLUGIN -> DeclaredCoordinates.plugins(root);
        };
        return declarations.filter(coordinate::matches);
    }

    private static boolean versioned(Node declaration) {
        return Xml.firstChild(declaration, "version").isPresent();
    }

    private static Stream<String> propertyProblems(Element root) {
        return DeclaredCoordinates.propertyBlocks(root)
            .flatMap(ManagedVersions::declaredProtectedProperties)
            .distinct();
    }

    private static Stream<String> declaredProtectedProperties(Node properties) {
        return PROTECTED_PROPERTIES.stream()
            .filter(property -> Xml.firstChild(properties, property).isPresent())
            .map(property -> "Remove child property " + property + "; Airness owns this value");
    }

    private static Coordinate allowedPlugin(String group, String artifact, String property) {
        return new Coordinate(Kind.PLUGIN, group, artifact, new Ownership(property, false));
    }

    private static Coordinate suppliedPlugin(String group, String artifact, String property) {
        return new Coordinate(Kind.PLUGIN, group, artifact, new Ownership(property, true));
    }

    private static Coordinate suppliedDependency(String group, String artifact, String property) {
        return new Coordinate(Kind.DEPENDENCY, group, artifact, new Ownership(property, true));
    }

    private static String read(Path pom) {
        try {
            return Files.readString(pom);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + pom, exception);
        }
    }

    /**
     * The two Maven declaration forms governed independently.
     */
    public enum Kind {

        /**
         * A project, plugin, or annotation-processor dependency.
         */
        DEPENDENCY,
        /**
         * A build or reporting plugin.
         */
        PLUGIN

    }

    /**
     * A coordinate, its root property, and whether the parent supplies its declaration.
     *
     * @param kind      coordinate kind
     * @param group     group identifier
     * @param artifact  artifact identifier
     * @param ownership version ownership
     */
    public record Coordinate(Kind kind, String group, String artifact, Ownership ownership) {

        String property() {
            return this.ownership.property();
        }

        boolean supplied() {
            return this.ownership.supplied();
        }

        boolean matches(Node declaration) {
            String defaultGroup = this.kind == Kind.PLUGIN ? MAVEN_PLUGIN_GROUP : "";
            String declaredGroup = Xml.text(declaration, "groupId").orElse(defaultGroup);
            String declaredArtifact = Xml.text(declaration, "artifactId").orElse("");
            return this.group.equals(declaredGroup) && this.artifact.equals(declaredArtifact);
        }

        String key() {
            return this.kind + ":" + this.group + ':' + this.artifact;
        }

        String declarationProblem() {
            return "Remove child declaration of " + this.group + ':' + this.artifact
                + "; Airness supplies this " + this.kind.name().toLowerCase(Locale.ROOT);
        }

        String versionProblem() {
            return "Remove child <version> from " + this.group + ':' + this.artifact
                + "; Airness owns this version";
        }
    }

    /**
     * The property that owns a managed version and whether the parent supplies the declaration.
     *
     * @param property root version property
     * @param supplied whether the parent supplies the declaration
     */
    public record Ownership(String property, boolean supplied) {
    }
}
