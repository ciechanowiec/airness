package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Inspects the bytes a JAR actually ships rather than inferring its contents from source roots.
 *
 * <p>Packaging plugins can add material that no source check reads. This check therefore opens the
 * finished archive and rejects ambiguous names, development-only material, test output, machine-local
 * paths, and recognizable secret material. Each finding names the archive entry that carried it.
 */
public final class ArtifactContentCheck {

    private static final List<Pattern> SECRET_PATTERNS = List.of(
        Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
        Pattern.compile("AKIA[0-9A-Z]{16}"),
        Pattern.compile("gh[pousr]_[A-Za-z0-9]{20,}")
    );
    private static final Set<String> DEVELOPMENT_NAMES = Set.of(
        ".classpath", ".ds_store", ".project", "thumbs.db"
    );
    private static final Set<String> DEVELOPMENT_DIRECTORIES = Set.of(
        ".git", ".github", ".idea", ".vscode"
    );
    private final Path artifact;
    private final Path mainOutput;
    private final Path testOutput;
    private final byte[] repositoryPath;

    /**
     * Creates an inspection of one finished module artifact.
     *
     * @param artifact       JAR to inspect
     * @param mainOutput     compiled production-output directory
     * @param testOutput     compiled test-output directory
     * @param repositoryRoot repository root whose absolute path must not leak
     */
    public ArtifactContentCheck(
        Path artifact, Path mainOutput, Path testOutput, Path repositoryRoot
    ) {
        this.artifact = artifact;
        this.mainOutput = mainOutput;
        this.testOutput = testOutput;
        this.repositoryPath = repositoryRoot.toAbsolutePath().normalize().toString()
            .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Every artifact-content rule and the entries that break it.
     *
     * @return findings for the finished JAR
     */
    public List<Findings> findings() {
        Sets offences = this.scan();
        return List.of(
            new Findings("Duplicate or unsafe JAR entries", offences.offences(Kind.UNSAFE)),
            new Findings(
                "Source or development files packaged in the JAR",
                offences.offences(Kind.DEVELOPMENT)
            ),
            new Findings("Test-only output packaged in the JAR", offences.offences(Kind.TEST)),
            new Findings(
                "Machine-local repository paths packaged in the JAR",
                offences.offences(Kind.LOCAL_PATH)
            ),
            new Findings(
                "Recognizable secret material packaged in the JAR",
                offences.offences(Kind.SECRET)
            )
        );
    }

    private Sets scan() {
        Sets sets = new Sets(relativeFiles(this.mainOutput), relativeFiles(this.testOutput));
        try (JarFile jar = new JarFile(this.artifact.toFile())) {
            jar.entries().asIterator().forEachRemaining(entry -> this.inspect(jar, entry, sets));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect artifact " + this.artifact, exception);
        }
        return sets;
    }

    private void inspect(JarFile jar, JarEntry entry, Sets sets) {
        String name = entry.getName();
        inspectName(name, sets);
        if (!entry.isDirectory()) {
            this.inspectFile(jar, entry, sets);
        }
    }

    private static void inspectName(String name, Sets sets) {
        if (sets.duplicate(name) || unsafe(name)) {
            sets.add(Kind.UNSAFE, name);
        }
        if (development(name)) {
            sets.add(Kind.DEVELOPMENT, name);
        }
    }

    private void inspectFile(JarFile jar, JarEntry entry, Sets sets) {
        if (sets.testOnly(entry.getName())) {
            sets.add(Kind.TEST, entry.getName());
        }
        byte[] bytes = bytes(jar, entry);
        if (contains(bytes, this.repositoryPath)) {
            sets.add(Kind.LOCAL_PATH, entry.getName());
        }
        CharSequence content = new String(bytes, StandardCharsets.ISO_8859_1);
        if (SECRET_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(content).find())) {
            sets.add(Kind.SECRET, entry.getName());
        }
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

    private static boolean contains(byte[] content, byte[] sought) {
        boolean present = false;
        for (int offset = 0; offset <= content.length - sought.length && !present; offset += 1) {
            present = matchesAt(content, sought, offset);
        }
        return present;
    }

    private static boolean matchesAt(byte[] content, byte[] sought, int offset) {
        boolean matches = true;
        for (int index = 0; index < sought.length && matches; index += 1) {
            matches = content[offset + index] == sought[index];
        }
        return matches;
    }

    private enum Kind {

        DEVELOPMENT,
        LOCAL_PATH,
        SECRET,
        TEST,
        UNSAFE
    }

    private static final class Sets {

        private final Set<String> main;
        private final Map<Kind, List<String>> offences;
        private final Set<String> seen;
        private final Set<String> test;

        Sets(Set<String> main, Set<String> test) {
            this.main = main;
            this.test = test;
            this.seen = new HashSet<>();
            this.offences = new EnumMap<>(Kind.class);
            this.offences.put(Kind.UNSAFE, new ArrayList<>());
            this.offences.put(Kind.DEVELOPMENT, new ArrayList<>());
            this.offences.put(Kind.TEST, new ArrayList<>());
            this.offences.put(Kind.LOCAL_PATH, new ArrayList<>());
            this.offences.put(Kind.SECRET, new ArrayList<>());
        }

        boolean testOnly(String name) {
            return this.test.contains(name) && !this.main.contains(name);
        }

        boolean duplicate(String name) {
            return !this.seen.add(name);
        }

        void add(Kind kind, String name) {
            Objects.requireNonNull(this.offences.get(kind)).add(name);
        }

        List<String> offences(Kind kind) {
            return List.copyOf(Objects.requireNonNull(this.offences.get(kind)));
        }
    }
}
