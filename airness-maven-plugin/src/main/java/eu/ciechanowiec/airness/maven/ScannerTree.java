package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ScannerInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * An invocation's private input tree. scanner ignores cannot change Git's selected set.
 *
 * <p>The tree is the source of the scanner's bind mounts, and Docker mounts only what the machine it runs
 * on shares with the host: a source outside the shared folders arrives in the container as an empty
 * directory, which the mount probe then refuses. Colima shares only the home directory by default, so the
 * system temporary directory, which macOS keeps outside it, would mount empty. Every tree is therefore
 * created beneath the home directory, under {@code .cache/airness/scan}, beside the report browser cache.
 * Being outside the project tree as well, the empty Git directory actionlint needs to find a project root
 * leaves no nested repository in the build output.
 *
 * <p>A scan that passed leaves nothing worth reading, so its tree is deleted. A finding or a failure keeps
 * the tree and names it, because what the scanner read and wrote is the evidence a reader needs.
 *
 * @param directory the scanner's output directory, beside the staged input
 * @param input     staged committable files
 */
record ScannerTree(Path directory, Path input) {

    private static final String EVIDENCE = "Scanner evidence: ";

    /**
     * The directory every scanner tree is created in, shared with Docker wherever the home directory is.
     *
     * @return the scanner cache beneath the user's home directory
     */
    static Path cache() {
        return Path.of(System.getProperty("user.home"), ".cache", "airness", "scan");
    }

    static ScannerTree create(Path parent, String tool, ScannerInventory inventory) throws IOException {
        List<String> problems = inventory.problems();
        if (!problems.isEmpty()) {
            throw new IOException(String.join(System.lineSeparator(), problems));
        }
        ScannerTree tree = empty(parent, tool);
        for (Path source : inventory.files()) {
            try {
                copy(source, tree.input.resolve(inventory.root().relativize(source)));
            } catch (IOException exception) {
                throw tree.abandon(source, exception);
            }
        }
        return tree;
    }

    static ScannerTree empty(Path parent, String tool) throws IOException {
        Files.createDirectories(parent);
        Path directory = Files.createTempDirectory(parent, tool + '-');
        Path input = Files.createDirectory(directory.resolve("input"));
        Files.writeString(input.resolve(".airness-scanner-input"), "Airness scanner input tree\n");
        Path results = Files.createDirectory(directory.resolve("output"));
        Files.writeString(results.resolve("empty.yaml"), "{}\n");
        return new ScannerTree(results, input);
    }

    static void copy(Path source, Path destination) throws IOException {
        if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) {
            throw new IOException("Scanner input is not a regular file: " + source);
        }
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination);
    }

    /**
     * The tree itself, holding the staged input beside the scanner output.
     *
     * @return the directory created for this invocation
     */
    Path location() {
        return Objects.requireNonNull(this.directory.getParent(), "A scanner output directory has a tree around it");
    }

    /**
     * Runs a scan over this tree, then deletes the tree when every verdict is clean.
     *
     * <p>A finding or a failure leaves the tree in place, and its location goes to {@code evidence}.
     *
     * @param evidence receives the line naming a tree that was kept
     * @param scan     the scan, reading and writing only this tree
     * @return the verdicts of the scan
     * @throws IOException when the scan fails or a passing tree cannot be deleted
     */
    List<Findings> conclude(Consumer<String> evidence, ScannerScan scan) throws IOException {
        try {
            List<Findings> verdicts = scan.verdicts();
            if (verdicts.stream().allMatch(Findings::clean)) {
                this.delete();
            }
            return verdicts;
        } finally {
            if (Files.exists(this.location())) {
                evidence.accept(EVIDENCE + this.location());
            }
        }
    }

    /**
     * Removes a tree whose staging failed, naming the repository input it could not stage.
     *
     * <p>No scanner has run, so a partial copy is no evidence, and the destination the copy reports lies
     * inside the removed tree. The failure therefore names the source, and a tree that cannot be removed
     * travels with it rather than replacing it.
     *
     * @param source the repository input that could not be staged
     * @param cause  why it could not
     * @return the failure to throw
     */
    private IOException abandon(Path source, IOException cause) {
        IOException failure = new IOException("Could not stage the scanner input " + source, cause);
        try {
            this.delete();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private void delete() throws IOException {
        try (Stream<Path> paths = Files.walk(this.location())) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
