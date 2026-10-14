package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Holds a project file to the build practices a current Maven build is written in.
 *
 * <p>The raw pom is read, profiles included, because a declaration is what a reader edits and an inactive
 * profile is a declaration waiting to be activated. Workflow files are read for the Java version they set up.
 */
public final class BuildPracticesCheck {

    private static final int JAVA_RELEASE = 25;
    private static final int WORKFLOW_JAVA_FLOOR = 21;
    private static final String COMPILER_PLUGIN = "maven-compiler-plugin";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String GROUP_ID = "groupId";

    private static final Set<String> VERSION_PROPERTIES = Set.of(
        "maven.compiler.release", "maven.compiler.source", "maven.compiler.target",
        "maven.compiler.testRelease", "maven.compiler.testSource", "maven.compiler.testTarget", "java.version"
    );

    private static final Set<String> COMPILER_SETTINGS = Set.of(
        "release", "source", "target", "testRelease", "testSource", "testTarget"
    );

    private static final List<Superseded> SUPERSEDED = List.of(
        new Superseded("javax.xml.bind", "jaxb-api", "jakarta.xml.bind:jakarta.xml.bind-api"),
        new Superseded("com.sun.xml.bind", "jaxb-core", "nothing; the Jakarta XML Binding runtime carries it"),
        new Superseded("javax.xml.ws", "jaxws-api", "jakarta.xml.ws:jakarta.xml.ws-api"),
        new Superseded("com.sun.xml.ws", "jaxws-ri", "com.sun.xml.ws:jaxws-rt"),
        new Superseded("javax.annotation", "javax.annotation-api", "jakarta.annotation:jakarta.annotation-api"),
        new Superseded("xmlunit", "xmlunit", "org.xmlunit:xmlunit-legacy"),
        new Superseded("org.mockito", "mockito-all", "org.mockito:mockito-core"),
        new Superseded("junit", "junit", "org.junit.jupiter:junit-jupiter"),
        new Superseded("org.junit.vintage", "junit-vintage-engine", "org.junit.jupiter:junit-jupiter-engine"),
        new Superseded("org.junit.jupiter", "junit-jupiter-migrationsupport", "nothing; Jupiter needs no bridge"),
        new Superseded("org.apache.maven.surefire", "surefire-junit*", "nothing; Surefire detects the platform"),
        new Superseded("org.codehaus.mojo", "cobertura-maven-plugin", "org.jacoco:jacoco-maven-plugin"),
        new Superseded("org.jvnet.jax-ws-commons", "jaxws-maven-plugin", "com.sun.xml.ws:jaxws-maven-plugin"),
        new Superseded("com.squareup.okhttp3", "mockwebserver", "com.squareup.okhttp3:mockwebserver3"),
        new Superseded("io.vertx", "vertx-unit", "io.vertx:vertx-junit5"),
        new Superseded("org.bouncycastle", "*-jdk15on", "the -jdk18on artifact of the same name"),
        new Superseded("org.bouncycastle", "*-jdk15to18", "the -jdk18on artifact of the same name"),
        new Superseded("org.bouncycastle", "*-jdk16", "the -jdk18on artifact of the same name"),
        new Superseded("org.bouncycastle", "*-jdk15", "the -jdk18on artifact of the same name"),
        new Superseded("org.bouncycastle", "*-jdk14", "the -jdk18on artifact of the same name"),
        new Superseded("org.bouncycastle", "*-jdk12", "the -jdk18on artifact of the same name")
    );

    private static final Map<String, String> PREFIXLESS = Map.of(
        "${basedir}", "${project.basedir}",
        "${build.timestamp}", "${maven.build.timestamp}",
        "${groupId}", "${project.groupId}",
        "${artifactId}", "${project.artifactId}",
        "${version}", "${project.version}",
        "${pom.version}", "${project.version}"
    );

    private static final List<String> ORDER = List.of(
        "modelVersion", "parent", GROUP_ID, ARTIFACT_ID, "version", "packaging", "name", "description", "url",
        "inceptionYear", "organization", "licenses", "developers", "contributors", "mailingLists", "prerequisites",
        "modules", "scm", "issueManagement", "ciManagement", "distributionManagement", "properties",
        "dependencyManagement", "dependencies", "repositories", "pluginRepositories", "build", "reporting", "profiles"
    );

    private static final Pattern JAVA_VERSION = Pattern.compile("^[ \\t]*java-version:[ \\t]*['\"]?(\\d+)");

    private final Path pom;
    private final List<Path> workflows;

    /**
     * Records the files the check reads.
     *
     * @param pom       the project file
     * @param workflows the workflow files of the repository, read only when this is its root project
     */
    public BuildPracticesCheck(Path pom, List<Path> workflows) {
        this.pom = pom;
        this.workflows = List.copyOf(workflows);
    }

    /**
     * Reports every place the project file or a workflow departs from a current build practice.
     *
     * @return one group of findings per practice
     */
    public List<Findings> findings() {
        Document document = Xml.parse(read(this.pom));
        Element project = document.getDocumentElement();
        return List.of(
            new Findings(
                "Declare Java " + JAVA_RELEASE + " wherever the build states a Java version", javaVersions(project)
            ),
            new Findings("Declare the successor of a superseded coordinate", superseded(project)),
            new Findings("Give every dependencyManagement entry its version", unversionedManagement(project)),
            new Findings("Name the groupId of every plugin", unnamedPluginGroups(project)),
            new Findings("Reach a project value through the project. prefix", this.prefixless()),
            new Findings(
                "Call the dependency plugin's resolve-sources goal rather than sources", sourcesGoals(project)
            ),
            new Findings("Write the top-level pom elements in the order Maven documents them", order(project)),
            new Findings(
                "Set up Java " + WORKFLOW_JAVA_FLOOR + " or later in every workflow", this.workflowJavaVersions()
            )
        );
    }

    private static List<String> javaVersions(Element project) {
        Stream<Element> properties = descendants(project, "properties")
            .flatMap(block -> elements(block).stream())
            .filter(property -> VERSION_PROPERTIES.contains(property.getTagName()));
        Stream<Element> compiler = descendants(project, "plugin")
            .filter(plugin -> Xml.text(plugin, ARTIFACT_ID).filter(COMPILER_PLUGIN::equals).isPresent())
            .flatMap(plugin -> descendants(plugin, "configuration"))
            .flatMap(configuration -> elements(configuration).stream())
            .filter(setting -> COMPILER_SETTINGS.contains(setting.getTagName()));
        return Stream.concat(properties, compiler)
            .filter(BuildPracticesCheck::belowRelease)
            .map(setting -> setting.getTagName() + " = " + setting.getTextContent().strip())
            .toList();
    }

    private static boolean belowRelease(Element setting) {
        return javaRelease(setting.getTextContent().strip()).filter(release -> release < JAVA_RELEASE).isPresent();
    }

    private static String coordinate(Element dependency) {
        return Xml.text(dependency, GROUP_ID).orElse("") + ':' + Xml.text(dependency, ARTIFACT_ID).orElse("");
    }

    private static Stream<String> oldJavaVersions(Path workflow) {
        return read(workflow).lines()
            .map(JAVA_VERSION::matcher)
            .filter(Matcher::find)
            .filter(version -> Integer.parseInt(version.group(1)) < WORKFLOW_JAVA_FLOOR)
            .map(version -> workflow.getFileName() + ": java-version " + version.group(1));
    }

    private static Optional<Integer> javaRelease(String value) {
        Matcher release = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(value);
        return release.matches() ? Optional.of(Integer.parseInt(release.group(1))) : Optional.empty();
    }

    private static List<String> superseded(Element project) {
        return Stream.concat(descendants(project, "dependency"), descendants(project, "plugin"))
            .flatMap(declaration -> successor(declaration).stream())
            .toList();
    }

    private static Optional<String> successor(Element declaration) {
        String group = Xml.text(declaration, GROUP_ID).orElse("org.apache.maven.plugins");
        String artifact = Xml.text(declaration, ARTIFACT_ID).orElse("");
        return SUPERSEDED.stream()
            .filter(entry -> entry.matches(group, artifact))
            .findFirst()
            .map(entry -> group + ':' + artifact + " -> " + entry.successor());
    }

    private static List<String> unversionedManagement(Element project) {
        return descendants(project, "dependencyManagement")
            .flatMap(management -> descendants(management, "dependency"))
            .filter(dependency -> Xml.text(dependency, "version").isEmpty())
            .map(BuildPracticesCheck::coordinate)
            .toList();
    }

    private static List<String> unnamedPluginGroups(Element project) {
        return descendants(project, "plugin")
            .filter(plugin -> Xml.text(plugin, GROUP_ID).isEmpty())
            .flatMap(plugin -> Xml.text(plugin, ARTIFACT_ID).stream())
            .toList();
    }

    private List<String> prefixless() {
        String content = read(this.pom);
        return PREFIXLESS.entrySet().stream()
            .filter(expression -> content.contains(expression.getKey()))
            .map(expression -> expression.getKey() + " -> " + expression.getValue())
            .sorted()
            .toList();
    }

    private static List<String> sourcesGoals(Element project) {
        return descendants(project, "plugin")
            .filter(plugin -> Xml.text(plugin, ARTIFACT_ID).filter("maven-dependency-plugin"::equals).isPresent())
            .flatMap(plugin -> descendants(plugin, "goal"))
            .filter(goal -> "sources".equals(goal.getTextContent().strip()))
            .map(_ -> "maven-dependency-plugin:sources")
            .toList();
    }

    private static List<String> order(Element project) {
        List<String> names = elements(project).stream()
            .map(Element::getTagName)
            .filter(ORDER::contains)
            .toList();
        return IntStream.range(1, names.size())
            .filter(index -> ORDER.indexOf(names.get(index)) < ORDER.indexOf(names.get(index - 1)))
            .mapToObj(index -> names.get(index) + " is written after " + names.get(index - 1))
            .toList();
    }

    private List<String> workflowJavaVersions() {
        return this.workflows.stream()
            .flatMap(BuildPracticesCheck::oldJavaVersions)
            .toList();
    }

    private static Stream<Element> descendants(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item).map(Element.class::cast);
    }

    private static List<Element> elements(Node parent) {
        NodeList nodes = parent.getChildNodes();
        return IntStream.range(0, nodes.getLength())
            .mapToObj(nodes::item)
            .filter(Element.class::isInstance)
            .map(Element.class::cast)
            .toList();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private record Superseded(String group, String artifact, String successor) {

        boolean matches(String declaredGroup, String declaredArtifact) {
            boolean artifactMatches = this.artifact.startsWith("*")
                ? declaredArtifact.endsWith(this.artifact.substring(1))
                : NamePattern.matches(this.artifact, declaredArtifact);
            return artifactMatches && NamePattern.matches(this.group, declaredGroup);
        }
    }
}
