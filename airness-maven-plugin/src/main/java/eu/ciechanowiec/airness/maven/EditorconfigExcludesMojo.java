package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.airness.governance.EditorconfigExcludes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.eclipse.aether.SessionData;

/**
 * Tells the file linter which paths git is configured never to carry, before the linter reads the
 * tree.
 *
 * <p>The linter takes its exclusions as static configuration, which cannot ask git anything, so the
 * answer is written to a file it is pointed at instead. That file is rewritten on every build, because
 * what a tree ignores changes as the tools working in it come and go.
 *
 * <p>The files a generator owns are written beside them, as the typography and source-rule checks skip them:
 * those the stack layers, the {@code airness.generated.files} property and each node project's
 * {@code airness.generatedArtefacts} declare. Which globs those are is a question about the whole repository,
 * read from every model in it, so it is answered once per session and kept. Only matching them against the
 * module's own files is done per module. The property is read from the project that owns the repository-wide
 * work, as typography and the generated-file denial read it, so a module cannot exempt a file from the linter
 * without the agent runtime losing write access to it.
 *
 * <p>This runs per module rather than once for the repository, because the patterns a linter matches
 * are relative to the module it is reading, so two modules of one repository need two different lists
 * of the same ignored paths.
 */
@Mojo(name = "editorconfig-excludes", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class EditorconfigExcludesMojo extends AbstractPreflightMojo {

    private static final String DIRECTORY = "airness";

    private static final String FILE = "editorconfig-excludes.txt";

    private static final String GENERATED = "airness.generated.files";

    private static final Object KEY = new Object();

    private static final Lock LOCK = new ReentrantLock();

    private final ProjectBuilder builder;

    /**
     * Receives the builder that reads the models of every module in the repository.
     *
     * @param builder the Maven project builder
     */
    @Inject
    public EditorconfigExcludesMojo(ProjectBuilder builder) {
        this.builder = builder;
    }

    @Override
    boolean applies() {
        return true;
    }

    @Override
    List<String> problems() {
        Path file = this.target();
        Path root = this.repositoryRoot();
        EditorconfigExcludes excludes = new EditorconfigExcludes(
            root, this.project().getBasedir().toPath(), this.generated(root)
        );
        write(file, excludes.document());
        return List.of();
    }

    // The globs of the files a generator owns, kept per root and declared value for the rest of the session,
    // because every module of a reactor asks the same question and the answer builds every model in the tree.
    @Justification("Maven session data is untyped, and this private key stores only this map type")
    @SuppressWarnings("unchecked")
    private List<String> generated(Path root) {
        String declared = this.declared();
        String key = root + "\n" + declared;
        SessionData data = this.session().getRepositorySession().getData();
        LOCK.lock();
        try {
            Map<String, List<String>> known = Optional.ofNullable(data.get(KEY))
                .map(value -> (Map<String, List<String>>) value)
                .orElseGet(Map::of);
            Optional<List<String>> held = Optional.ofNullable(known.get(key));
            if (held.isPresent()) {
                return held.get();
            }
            List<String> generated = GeneratedDenialMojo.generated(
                root, RepositorySites.read(this.builder, this.session().getProjectBuildingRequest(), root),
                Sentinel.optional(declared)
            );
            Map<String, List<String>> update = new HashMap<>(known);
            update.put(key, generated);
            data.set(KEY, Map.copyOf(update));
            return generated;
        } finally {
            LOCK.unlock();
        }
    }

    // The value the top-level project's own goals are handed, which is the project that owns the repository-wide
    // work: what the command line sets, else what its model declares. In a build started inside one module, that
    // module is the top-level project, as it is for typography.
    private String declared() {
        MavenProject owner = this.session().getTopLevelProject();
        return Optional.ofNullable(this.session().getUserProperties().getProperty(GENERATED))
            .or(() -> Optional.ofNullable(owner.getProperties().getProperty(GENERATED)))
            .orElse("");
    }

    private Path target() {
        Path directory = Path.of(this.project().getBuild().getDirectory(), DIRECTORY);
        try {
            Files.createDirectories(directory);
            return directory.resolve(FILE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create the linter exclusions directory: " + directory, exception);
        }
    }

    // Written whether or not the module has anything ignored under it, because the linter reports an
    // unreadable exclusions file rather than passing over an absent one, and a build that ignores
    // nothing is the ordinary case rather than a reason to leave the file out.
    private static void write(Path file, String document) {
        try {
            Files.writeString(file, document);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write the linter exclusions to " + file, exception);
        }
    }
}
