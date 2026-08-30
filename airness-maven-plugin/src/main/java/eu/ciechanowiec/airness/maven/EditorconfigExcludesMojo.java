package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.EditorconfigExcludes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Tells the file linter which paths git is configured never to carry, before the linter reads the
 * tree.
 *
 * <p>The linter takes its exclusions as static configuration, which cannot ask git anything, so the
 * answer is written to a file it is pointed at instead. That file is rewritten on every build, because
 * what a tree ignores changes as the tools working in it come and go.
 *
 * <p>This runs per module rather than once for the repository, because the patterns a linter matches
 * are relative to the module it is reading, so two modules of one repository need two different lists
 * of the same ignored paths.
 */
@Mojo(name = "editorconfig-excludes", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class EditorconfigExcludesMojo extends AbstractPreflightMojo {

    private static final String DIRECTORY = "airness";

    private static final String FILE = "editorconfig-excludes.txt";

    @Override
    boolean applies() {
        return true;
    }

    @Override
    List<String> problems() {
        Path file = this.target();
        EditorconfigExcludes excludes = new EditorconfigExcludes(
            this.repositoryRoot(), this.project().getBasedir().toPath()
        );
        write(file, excludes.document());
        return List.of();
    }

    private Path target() {
        return Path.of(this.project().getBuild().getDirectory(), DIRECTORY, FILE);
    }

    // Written whether or not the module has anything ignored under it, because the linter reports an
    // unreadable exclusions file rather than passing over an absent one, and a build that ignores
    // nothing is the ordinary case rather than a reason to leave the file out.
    private static void write(Path file, String document) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, document, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write the linter exclusions to " + file, exception);
        }
    }
}
