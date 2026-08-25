package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Inspects every file uploaded for a Maven release, including classified archives and the POM.
 */
public final class PublicationContentCheck {

    private static final String JAR = ".jar";
    private final List<Path> files;
    private final String repositoryPath;

    /**
     * Creates a publication inspection.
     *
     * @param files          publication files Maven will upload
     * @param repositoryRoot repository root whose absolute path must not leak
     */
    public PublicationContentCheck(Collection<Path> files, Path repositoryRoot) {
        this.files = List.copyOf(files);
        byte[] encoded = repositoryRoot.toAbsolutePath().normalize().toString()
            .getBytes(StandardCharsets.UTF_8);
        this.repositoryPath = new String(encoded, StandardCharsets.ISO_8859_1);
    }

    /**
     * Publication-content verdicts.
     *
     * @return missing files, local paths, and recognizable secrets
     */
    public List<Findings> findings() {
        List<Content> content = this.files.stream()
            .filter(Files::isRegularFile)
            .flatMap(this::content)
            .toList();
        return List.of(
            new Findings("Missing Maven publication files", this.missing()),
            new Findings(
                "Machine-local paths in Maven publication files", content.stream()
                    .filter(entry -> entry.value().contains(this.repositoryPath))
                    .map(Content::name)
                    .toList()
            ),
            new Findings(
                "Recognizable secrets in Maven publication files", content.stream()
                    .filter(entry -> SensitiveContent.secret(entry.value()))
                    .map(Content::name)
                    .toList()
            )
        );
    }

    private List<String> missing() {
        return this.files.stream().filter(path -> !Files.isRegularFile(path)).map(Path::toString).toList();
    }

    private Stream<Content> content(Path path) {
        return path.toString().endsWith(JAR) ? archive(path) : Stream.of(plain(path));
    }

    private static Content content(JarFile jar, Path path, JarEntry entry) {
        try (InputStream input = jar.getInputStream(entry)) {
            String value = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
            return new Content(path + "!" + entry.getName(), value);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect " + entry.getName() + " in " + path, exception);
        }
    }

    private static Stream<Content> archive(Path path) {
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.stream().filter(entry -> !entry.isDirectory())
                .map(entry -> content(jar, path, entry))
                .toList()
                .stream();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect publication archive " + path, exception);
        }
    }

    /**
     * Reads a file the same way {@link #content(JarFile, Path, JarEntry)} reads an archive entry, so
     * both sides of this check compare like with like.
     *
     * <p>The repository path this class searches for is held as its UTF-8 bytes mapped one to one onto
     * ISO-8859-1 characters, which is what makes a byte-level search work whatever a file's real
     * encoding is. Decoding a file as UTF-8 instead would leave a root path carrying any non-ASCII
     * character unmatchable in the POM, which is the publication file most likely to spell one out, and
     * would fail outright on any byte UTF-8 cannot decode
     *
     * @param path the publication file to read
     * @return its bytes, decoded so that every byte survives as one character
     */
    private static Content plain(Path path) {
        try {
            return new Content(path.toString(), new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect publication file " + path, exception);
        }
    }

    private record Content(String name, String value) {
    }
}
