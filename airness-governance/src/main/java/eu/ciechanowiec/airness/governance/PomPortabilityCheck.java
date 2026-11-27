package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.w3c.dom.Element;

/**
 * Holds a project file to declarations that build alike on every machine and stay inside the repository.
 *
 * <p>A profile active by default turns itself off as soon as any other profile is selected, so the build depends
 * on which unrelated profile somebody names. A profile activated by the operating system, the JDK or an environment
 * variable builds differently on each machine. Site reporting runs plugins no Airness check governs, beside the
 * compliance report that already says what a release carries. Maven reads prerequisites only in a plugin, and
 * warns about them elsewhere. A distribution status is Maven's to write, and a relocation belongs only in a pom
 * that has moved and builds nothing. A moved source or output directory leaves every analyzer reading the
 * convention looking at nothing. A module or parent pom outside the repository builds with no check reading it.
 *
 * <p>The raw pom is read, profiles included, because an inactive profile is a declaration waiting to be activated.
 * Only the directories written directly under a build are read, so a plugin's own configuration that happens to
 * name a source directory is left alone.
 */
public final class PomPortabilityCheck {

    private static final String PROFILES = "profiles";
    private static final String ACTIVATION = "activation";
    private static final String BUILD = "build";
    private static final String DISTRIBUTION = "distributionManagement";
    private static final Set<String> DIRECTORIES = Set.of(
        "sourceDirectory", "testSourceDirectory", "outputDirectory", "testOutputDirectory", "scriptSourceDirectory"
    );
    private static final Pattern ENVIRONMENT = Pattern.compile("!?env\\..*");
    private static final Pattern ABSOLUTE = Pattern.compile("/.*|[A-Za-z]:.*");

    private final Path pom;
    private final Path repository;

    /**
     * Records the files the check reads.
     *
     * @param pom        the project file
     * @param repository the repository root, which no module or parent pom may leave
     */
    public PomPortabilityCheck(Path pom, Path repository) {
        this.pom = pom;
        this.repository = repository;
    }

    /**
     * Reports every declaration that builds differently by machine or reaches outside the repository.
     *
     * @return one group of findings per rule
     */
    public List<Findings> findings() {
        Element project = Xml.parse(read(this.pom)).getDocumentElement();
        List<Element> profiles = Xml.firstChild(project, PROFILES).stream()
            .flatMap(block -> Xml.children(block, PomPropertyOrder.PROFILE).stream())
            .toList();
        return List.of(
            new Findings(
                "Activate no profile by default, by operating system, by JDK or by an environment variable",
                profiles.stream().flatMap(PomPortabilityCheck::activations).toList()
            ),
            new Findings("Leave site reporting to the compliance report", reporting(project, profiles)),
            new Findings("Declare prerequisites only in a Maven plugin", prerequisites(project)),
            new Findings(
                "Leave the distribution status and relocation to Maven", distribution(project, profiles)
            ),
            new Findings("Keep the conventional source and output directories", directories(project, profiles)),
            new Findings("Keep every module and parent pom inside the repository", this.escapes(project, profiles))
        );
    }

    private static Stream<String> activations(Element profile) {
        String label = label(profile);
        return Xml.firstChild(profile, ACTIVATION).stream().flatMap(
            activation -> Stream.of(
                Xml.firstChild(activation, "activeByDefault").map(_ -> label + " is active by default"),
                Xml.firstChild(activation, "os").map(_ -> label + " activates by operating system"),
                Xml.firstChild(activation, "jdk").map(_ -> label + " activates by JDK"),
                Xml.firstChild(activation, "property")
                    .flatMap(property -> Xml.text(property, "name"))
                    .filter(name -> ENVIRONMENT.matcher(name).matches())
                    .map(name -> label + " activates by environment variable " + name)
            ).flatMap(Optional::stream)
        );
    }

    private static List<String> reporting(Element project, List<Element> profiles) {
        Stream<String> own = Xml.firstChild(project, "reporting").map(_ -> "<reporting>").stream();
        Stream<String> profiled = profiles.stream()
            .filter(profile -> Xml.firstChild(profile, "reporting").isPresent())
            .map(profile -> label(profile) + " declares <reporting>");
        return Stream.concat(own, profiled).toList();
    }

    private static List<String> prerequisites(Element project) {
        boolean plugin = Xml.text(project, "packaging").filter("maven-plugin"::equals).isPresent();
        return Xml.firstChild(project, "prerequisites")
            .filter(_ -> !plugin)
            .map(_ -> "<prerequisites> in a project that is not a Maven plugin")
            .stream().toList();
    }

    private static List<String> distribution(Element project, List<Element> profiles) {
        Stream<Element> owners = Stream.concat(Stream.of(project), profiles.stream());
        return owners.flatMap(
            owner -> Xml.firstChild(owner, DISTRIBUTION).stream().flatMap(
                management -> Stream.of("status", "relocation")
                    .filter(name -> Xml.firstChild(management, name).isPresent())
                    .map(name -> where(project, owner) + "<" + DISTRIBUTION + "><" + name + ">")
            )
        ).toList();
    }

    private static List<String> directories(Element project, List<Element> profiles) {
        Stream<Element> owners = Stream.concat(Stream.of(project), profiles.stream());
        return owners.flatMap(
            owner -> Xml.firstChild(owner, BUILD).stream().flatMap(
                build -> DIRECTORIES.stream().sorted()
                    .flatMap(name -> Xml.text(build, name).map(value -> name + " " + value).stream())
                    .map(setting -> where(project, owner) + "<build> moves " + setting)
            )
        ).toList();
    }

    private List<String> escapes(Element project, List<Element> profiles) {
        Path directory = real(this.pom.toAbsolutePath().resolveSibling(""));
        Path root = real(this.repository.toAbsolutePath());
        Stream<String> modules = Stream.concat(Stream.of(project), profiles.stream())
            .flatMap(owner -> Xml.firstChild(owner, "modules").stream())
            .flatMap(block -> Xml.children(block, "module").stream())
            .map(module -> module.getTextContent().strip())
            .filter(module -> outside(directory, root, module))
            .map(module -> "module " + module + " lies outside the repository");
        Stream<String> parent = Xml.firstChild(project, "parent")
            .flatMap(declared -> Xml.text(declared, "relativePath"))
            .filter(path -> outside(directory, root, path))
            .map(path -> "parent relativePath " + path + " lies outside the repository")
            .stream();
        return Stream.concat(modules, parent).toList();
    }

    private static boolean outside(Path directory, Path root, String declared) {
        if (declared.isEmpty()) {
            return false;
        }
        String path = declared.replace('\\', '/');
        return ABSOLUTE.matcher(path).matches() || !directory.resolve(path).normalize().startsWith(root);
    }

    private static String where(Element project, Element owner) {
        return owner.equals(project) ? "" : label(owner) + " declares ";
    }

    private static String label(Element profile) {
        String id = Xml.idTextOrEmpty(profile);
        return "profile " + (id.isEmpty() ? "without an id" : id);
    }

    // The repository root comes from git, which resolves symbolic links, so the pom's directory is resolved too.
    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException _) {
            return path.normalize();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }
}
