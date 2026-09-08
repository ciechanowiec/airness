package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InputSelectionTest {

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void usesMavenPatternsAndDefaultExclusions() {
        Files.createDirectories(this.directory.resolve("nested"));
        Files.writeString(this.directory.resolve("nested/keep.mjs"), "Keep\n");
        Files.writeString(this.directory.resolve("nested/drop.mjs"), "Drop\n");
        Files.writeString(this.directory.resolve("nested/note.txt"), "Note\n");
        InputSelection selection = new InputSelection(
            this.directory, List.of("**/*.mjs"), List.of("**/drop.*"), true
        );
        assertEquals(Set.of("nested/keep.mjs"), selection.files(List.of()));
    }

    @Test
    @SneakyThrows
    void honorsDisabledDefaultExclusionsAndGeneratedDirectories() {
        Files.createDirectories(this.directory.resolve(".git"));
        Files.createDirectories(this.directory.resolve("output"));
        Files.writeString(this.directory.resolve(".git/config"), "Git\n");
        Files.writeString(this.directory.resolve("output/value.txt"), "Output\n");
        List<Path> outputs = List.of(this.directory.resolve("output"));
        assertTrue(new InputSelection(this.directory, List.of(), List.of(), true).files(outputs).isEmpty());
        assertEquals(
            Set.of(".git/config"),
            new InputSelection(this.directory, List.of(), List.of(), false).files(outputs)
        );
    }

    @Test
    void acceptsAnAbsentOptionalInputDirectory() {
        InputSelection selection = new InputSelection(this.directory.resolve("missing"), List.of(), List.of(), true);
        assertTrue(selection.files(List.of()).isEmpty());
    }

    @Test
    @SneakyThrows
    void refusesAFileWhereAnInputDirectoryWasRequired() {
        Path file = Files.writeString(this.directory.resolve("not-a-directory"), "File\n");
        InputSelection selection = new InputSelection(file, List.of(), List.of(), true);
        assertThrows(IllegalStateException.class, () -> selection.files(List.of()));
    }
}
