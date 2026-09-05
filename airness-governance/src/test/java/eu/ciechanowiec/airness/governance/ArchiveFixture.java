package eu.ciechanowiec.airness.governance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;

/**
 * Real archives and real build output, standing in for what a module ships.
 *
 * <p>What is being tested is that a check opens a finished archive and reads the bytes it holds, so
 * the archive has to be one. Each call writes a new archive under the same directory, which lets one
 * reading compare an archive against another built beside it.
 *
 * @param directory the temporary directory the archives and the output trees are written under
 */
record ArchiveFixture(Path directory) {

    private static final String ARCHIVE = "artifact-";

    private static final String SUFFIX = ".jar";

    /**
     * Writes an archive holding the given entries.
     *
     * @param entries entry name to the text that entry carries
     * @return the archive
     */
    @SneakyThrows
    Path jar(Map<String, String> entries) {
        Path jar = this.directory().resolve(ARCHIVE + this.written() + SUFFIX);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            entries.forEach((name, content) -> writeEntry(output, name, content));
        }
        return jar;
    }

    /**
     * Writes one file into a compiled output tree.
     *
     * @param tree    name of the output tree, such as the main one or the test one
     * @param entry   path of the file inside that tree
     * @param content the text the file carries
     * @return the root of the output tree
     */
    @SneakyThrows
    Path output(String tree, String entry, String content) {
        Path file = this.directory().resolve(tree).resolve(entry);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return this.directory().resolve(tree);
    }

    /**
     * The offences one rule of a verdict reported.
     *
     * @param findings every rule of the check
     * @param headline the headline naming the rule to read
     * @return what that rule reported
     */
    static List<String> offences(Collection<Findings> findings, String headline) {
        return findings.stream()
            .filter(finding -> headline.equals(finding.headline()))
            .findFirst()
            .orElseThrow()
            .offences();
    }

    @SneakyThrows
    private static void writeEntry(JarOutputStream output, String name, String content) {
        output.putNextEntry(new JarEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    /*
     * How many files stand in the directory already, which gives every archive a name of its own.
     */
    @SneakyThrows
    private long written() {
        try (Stream<Path> paths = Files.list(this.directory())) {
            return paths.count();
        }
    }
}
