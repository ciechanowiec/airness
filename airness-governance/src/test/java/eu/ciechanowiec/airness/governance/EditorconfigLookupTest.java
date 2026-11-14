package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.ec4j.core.model.PropertyType.IndentStyleValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * A file's indentation is what the {@code .editorconfig} files at and below the working tree root declare for
 * it, read by the reference implementation.
 */
class EditorconfigLookupTest {

    private static final String EDITORCONFIG = ".editorconfig";

    @Test
    void readsTheStyleAndTheSizeTheMatchingSectionsDeclare(@TempDir Path root) {
        write(
            root.resolve(EDITORCONFIG),
            "root = true\n[*]\nindent_style = space\nindent_size = 4\n[*.yml]\nindent_size = 2\n"
        );
        IndentationSettings yaml = new EditorconfigLookup(root).settings(root.resolve("deploy/values.yml"));
        IndentationSettings other = new EditorconfigLookup(root).settings(root.resolve("Main.java"));
        assertAll(
            () -> assertEquals(Optional.of(IndentStyleValue.space), yaml.style()),
            () -> assertEquals(Optional.of(2), yaml.size()),
            () -> assertEquals(Optional.of(4), other.size())
        );
    }

    @Test
    void readsATabStyleAsDeclaringNoNumberOfColumns(@TempDir Path root) {
        write(root.resolve(EDITORCONFIG), "root = true\n[*]\nindent_size = 4\n[Makefile]\nindent_style = tab\n");
        IndentationSettings settings = new EditorconfigLookup(root).settings(root.resolve("Makefile"));
        assertAll(
            () -> assertEquals(Optional.of(IndentStyleValue.tab), settings.style()),
            () -> assertEquals(Optional.empty(), settings.size(), "indent_size = tab defers to a tab width")
        );
    }

    @Test
    @SneakyThrows
    void holdsTheFormatsTheirFormattersWriteWithTabsToTabsInTheManagedEditorconfig(@TempDir Path root) {
        Path managed = SelfModules.repository()
            .resolve("airness-assets/src/main/resources/airness/files/generic/.editorconfig.asset");
        write(root.resolve(EDITORCONFIG), Files.readString(managed));
        EditorconfigLookup lookup = new EditorconfigLookup(root);
        List<String> tabbed = List.of(
            "Makefile", "tools/rules.mk", ".gitmodules", "cmd/main.go", "Caddyfile", "deployment/site.Caddyfile",
            "deployment/site.caddyfile"
        );
        assertAll(
            Stream.concat(
                tabbed.stream().<Executable>map(name -> () -> assertEquals(
                    Optional.of(IndentStyleValue.tab), lookup.settings(root.resolve(name)).style(), name
                )),
                Stream.of("src/Main.java", "docs/Caddyfile.md").<Executable>map(name -> () -> assertEquals(
                    Optional.of(IndentStyleValue.space), lookup.settings(root.resolve(name)).style(), name
                ))
            )
        );
    }

    @Test
    void readsAnUnsetOrAnInvalidPropertyAsAbsent(@TempDir Path root) {
        write(
            root.resolve(EDITORCONFIG),
            """
                root = true
                [*]
                indent_style = space
                indent_size = 4
                [*.txt]
                indent_style = unset
                indent_size = wide
                """
        );
        IndentationSettings settings = new EditorconfigLookup(root).settings(root.resolve("notes.txt"));
        assertAll(
            () -> assertEquals(Optional.empty(), settings.style()),
            () -> assertEquals(Optional.empty(), settings.size())
        );
    }

    @Test
    void readsNoEditorconfigAboveTheWorkingTreeRoot(@TempDir Path outside) {
        write(outside.resolve(EDITORCONFIG), "[*]\nindent_style = tab\n");
        Path root = outside.resolve("repository");
        IndentationSettings settings = new EditorconfigLookup(root).settings(root.resolve("Main.java"));
        assertEquals(Optional.empty(), settings.style());
    }

    @Test
    @SneakyThrows
    void failsWhenAnEditorconfigCannotBeRead(@TempDir Path root) {
        Files.createDirectories(root.resolve(EDITORCONFIG));
        EditorconfigLookup lookup = new EditorconfigLookup(root);
        assertThrows(UncheckedIOException.class, () -> lookup.settings(root.resolve("Main.java")));
    }

    @SneakyThrows
    private static void write(Path file, String content) {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
