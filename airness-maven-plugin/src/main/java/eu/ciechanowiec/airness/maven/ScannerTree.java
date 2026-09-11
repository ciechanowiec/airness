package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ScannerInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * An invocation's private input tree. scanner ignores cannot change Git's selected set.
 *
 * @param directory temporary directory beneath Maven build output
 * @param input     staged committable files
 */
record ScannerTree(Path directory, Path input) {

    static ScannerTree create(Path output, ScannerInventory inventory) throws IOException {
        List<String> problems = inventory.problems();
        if (!problems.isEmpty()) {
            throw new IOException(String.join(System.lineSeparator(), problems));
        }
        ScannerTree tree = empty(output);
        for (Path source : inventory.files()) {
            copy(source, tree.input.resolve(inventory.root().relativize(source)));
        }
        return tree;
    }

    static ScannerTree empty(Path output) throws IOException {
        Files.createDirectories(output);
        Path directory = Files.createTempDirectory(output, "scan-");
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
}
