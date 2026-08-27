package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Inspects the bytes a JAR actually ships rather than inferring its contents from source roots.
 *
 * <p>Packaging plugins can add material that no source check reads. This check therefore opens the
 * finished archive and rejects ambiguous names, development-only material, test output, machine-local
 * paths, and recognizable secret material. Each finding names the archive entry that carried it.
 *
 * <p>The finished archive of a project that repackages holds two populations of bytes, and two rules
 * here read only one of them. What the module itself put into the archive, whether from its own build
 * output or from a packaging plugin that wrote into the archive afterwards, is this module's to answer
 * for. What a dependency published inside its own archive and a packaging plugin then copied across is
 * that dependency's own artifact-content question, answered in that dependency's own build, and no
 * change to this project would settle it. The development rule and the secret rule are therefore read
 * against the entries no vendored archive supplies. Both rules were written for first-party build
 * output and neither states anything a project can act on when it fires on a dependency's published
 * bytes: a library that ships its sources under {@code OSGI-OPT/src} and a library whose TLS code
 * carries a throwaway test key in a string constant are each reporting on the library.
 *
 * <p>The remaining three rules read every entry whatever supplied it. An entry name that escapes the
 * extraction directory is dangerous whoever wrote it, an absolute path out of the machine that built
 * this archive cannot have reached a published dependency, and a duplicate name is a property of this
 * archive rather than of any one contributor to it.
 */
public final class ArtifactContentCheck {

    private static final Set<String> DEVELOPMENT_NAMES = Set.of(
        ".classpath", ".ds_store", ".project", "thumbs.db"
    );
    private static final Set<String> DEVELOPMENT_DIRECTORIES = Set.of(
        ".git", ".github", ".idea", ".vscode"
    );
    private final Path artifact;
    private final ModuleOutput output;
    private final String repositoryPath;
    private final List<Path> vendored;

    /**
     * Creates an inspection of one finished module artifact.
     *
     * @param artifact       JAR to inspect
     * @param output         compiled output directories of the module that produced the JAR
     * @param repositoryRoot repository root whose absolute path must not leak
     * @param vendored       archives of the resolved dependencies a packaging plugin can copy into the
     *                       JAR, which is what lets the two first-party rules tell the module's own
     *                       bytes from a dependency's published ones
     */
    public ArtifactContentCheck(
        Path artifact, ModuleOutput output, Path repositoryRoot, Collection<Path> vendored
    ) {
        this.artifact = artifact;
        this.output = output;
        byte[] encoded = repositoryRoot.toAbsolutePath().normalize().toString()
            .getBytes(StandardCharsets.UTF_8);
        this.repositoryPath = new String(encoded, StandardCharsets.ISO_8859_1);
        this.vendored = List.copyOf(vendored);
    }

    /**
     * Every artifact-content rule and the entries that break it.
     *
     * @return findings for the finished JAR
     */
    public List<Findings> findings() {
        Map<Kind, List<String>> offences = this.scan();
        return List.of(
            new Findings("Duplicate or unsafe JAR entries", entries(offences, Kind.UNSAFE)),
            new Findings(
                "Source or development files packaged in the JAR",
                entries(offences, Kind.DEVELOPMENT)
            ),
            new Findings("Test-only output packaged in the JAR", entries(offences, Kind.TEST)),
            new Findings(
                "Machine-local repository paths packaged in the JAR",
                entries(offences, Kind.LOCAL_PATH)
            ),
            new Findings(
                "Recognizable secret material packaged in the JAR",
                entries(offences, Kind.SECRET)
            )
        );
    }

    private static List<String> entries(Map<Kind, List<String>> offences, Kind kind) {
        return Objects.requireNonNull(offences.get(kind));
    }

    private Map<Kind, List<String>> scan() {
        Set<String> main = relativeFiles(this.output.main());
        Set<String> test = relativeFiles(this.output.test());
        Set<String> supplied = this.vendoredEntries();
        try (JarFile jar = new JarFile(this.artifact.toFile())) {
            List<Content> entries = jar.stream().map(entry -> content(jar, entry)).toList();
            List<String> names = entries.stream().map(Content::name).toList();
            Stream<Content> files = entries.stream().filter(entry -> !entry.directory());
            return Map.of(
                Kind.UNSAFE, IntStream.range(0, names.size())
                    .filter(index -> unsafeEntry(names, index))
                    .mapToObj(names::get)
                    .toList(),
                Kind.DEVELOPMENT, names.stream()
                    .filter(name -> !supplied.contains(name))
                    .filter(ArtifactContentCheck::development)
                    .toList(),
                Kind.TEST, files.filter(entry -> testOnly(entry, main, test))
                    .map(Content::name)
                    .toList(),
                Kind.LOCAL_PATH, entries.stream()
                    .filter(Content::file)
                    .filter(entry -> entry.content().contains(this.repositoryPath))
                    .map(Content::name)
                    .toList(),
                Kind.SECRET, entries.stream()
                    .filter(Content::file)
                    .filter(entry -> !supplied.contains(entry.name()))
                    .filter(entry -> SensitiveContent.secret(entry.content()))
                    .map(Content::name)
                    .toList()
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect artifact " + this.artifact, exception);
        }
    }

    /**
     * Every entry name the resolved dependency archives carry.
     *
     * @return the names a packaging plugin can copy into this archive without this module writing one
     */
    private Set<String> vendoredEntries() {
        return this.vendored.stream()
            .flatMap(ArtifactContentCheck::archiveNames)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<String> archiveNames(Path archive) {
        try (JarFile jar = new JarFile(archive.toFile())) {
            return jar.stream().map(JarEntry::getName).toList().stream();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read dependency archive " + archive, exception);
        }
    }

    private static Content content(JarFile jar, JarEntry entry) {
        String held = entry.isDirectory()
            ? ""
            : new String(bytes(jar, entry), StandardCharsets.ISO_8859_1);
        return new Content(entry.getName(), entry.isDirectory(), held);
    }

    private static boolean unsafeEntry(List<String> names, int index) {
        return names.indexOf(names.get(index)) != index || unsafe(names.get(index));
    }

    private static boolean testOnly(
        Content entry, Collection<String> main, Collection<String> test
    ) {
        return test.contains(entry.name()) && !main.contains(entry.name());
    }

    private static boolean unsafe(String name) {
        return absolute(name) || parentTraversal(name) || windowsPath(name);
    }

    private static boolean absolute(String name) {
        return name.startsWith("/") || name.startsWith("\\");
    }

    private static boolean parentTraversal(String name) {
        return name.contains("../") || name.contains("..\\");
    }

    private static boolean windowsPath(String name) {
        return name.contains("\\") || name.matches("^[A-Za-z]:.*");
    }

    private static boolean development(String name) {
        String lowered = name.toLowerCase(Locale.ROOT);
        int separator = lowered.lastIndexOf('/');
        String filename = lowered.substring(separator + 1);
        boolean directory = Stream.of(lowered.split("/"))
            .anyMatch(DEVELOPMENT_DIRECTORIES::contains);
        return lowered.endsWith(".java")
            || lowered.endsWith(".iml")
            || DEVELOPMENT_NAMES.contains(filename)
            || directory;
    }

    private static Set<String> relativeFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .map(root::relativize)
                .map(Path::toString)
                .map(name -> name.replace('\\', '/'))
                .collect(Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect build output " + root, exception);
        }
    }

    private static byte[] bytes(JarFile jar, JarEntry entry) {
        try (InputStream input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read JAR entry " + entry.getName(), exception);
        }
    }

    private enum Kind {

        DEVELOPMENT,
        LOCAL_PATH,
        SECRET,
        TEST,
        UNSAFE
    }

    private record Content(String name, boolean directory, String content) {

        boolean file() {
            return !this.directory;
        }
    }
}
