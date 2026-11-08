package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.ec4j.core.Cache.Caches;
import org.ec4j.core.EditorConfigLoader;
import org.ec4j.core.Resource.Resources;
import org.ec4j.core.ResourcePath.ResourcePaths;
import org.ec4j.core.ResourcePropertiesService;

/**
 * Resolves the {@code .editorconfig} properties of a file the way the reference implementation does.
 *
 * <p>Which sections apply to a file is a question of EditorConfig globs and of which file nearer the file
 * overrides which further from it, and the reference implementation, ec4j, is the parser the file linter of
 * the same build reads them with. A matcher written here would be a second reading of the same files, and
 * the two could disagree about one path without anything noticing. The search stops at the working tree
 * root, so a file outside the repository never decides a verdict about it.
 */
final class EditorconfigLookup {

    private final ResourcePropertiesService service;

    /**
     * Reads the {@code .editorconfig} files at and below a working tree root, each once.
     *
     * @param root the working tree root, above which no {@code .editorconfig} is read
     */
    EditorconfigLookup(Path root) {
        this.service = ResourcePropertiesService.builder()
            .rootDirectory(ResourcePaths.ofPath(root.toAbsolutePath(), StandardCharsets.UTF_8))
            .cache(Caches.permanent())
            .loader(EditorConfigLoader.default_())
            .build();
    }

    /**
     * The indentation the sections matching a file declare.
     *
     * @param file the file whose properties are asked for
     * @return what those sections declare about its indentation
     */
    IndentationSettings settings(Path file) {
        try {
            return new IndentationSettings(
                this.service.queryProperties(Resources.ofPath(file.toAbsolutePath(), StandardCharsets.UTF_8))
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the .editorconfig files that apply to " + file, exception);
        }
    }
}
