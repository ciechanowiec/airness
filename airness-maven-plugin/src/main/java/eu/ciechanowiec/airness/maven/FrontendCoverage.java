package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Every authored script and stylesheet in a module is a file some Airness gate checks.
 *
 * <p>A {@code package.json} is not what makes a frontend: a module serving a stylesheet from its Java
 * resources has one without any manifest, and a manifest beside it would check nothing the gates do not
 * select. So the inventory is of the files themselves, in every module, and each is matched to the node
 * project that owns it and to the roots that project's gates read:
 *
 * <ul>
 * <li>a stylesheet ({@code .css}, {@code .scss}) is checked when it lies under a checked source root, which
 * is how the {@code css} gate selects it.</li>
 * <li>a script (the JS and TS variants) is checked under a checked source root, or as a tooling file at the
 * top of its project, such as a test or lint configuration, which the linters and the type check read
 * over the whole project.</li>
 * <li>a {@code .sass} or {@code .less} file is checked by no gate wherever it lies.</li>
 * </ul>
 *
 * <p>A file is passed over only when a generator owns it, as the layers, the {@code airness.generated.files}
 * property and the node projects' {@code generatedArtefacts} declare, or when the harness places it. A
 * directory named {@code vendor} or a minified file name is no evidence that somebody else wrote a file, so
 * neither is passed over: a distribution is taken from a dependency instead. The files read are the ones a
 * commit would carry. An ignored file that the Maven build consumes is refused by {@code build-inputs}, and a
 * file only a custom tool reads from outside both is beyond what this walk can see.
 */
final class FrontendCoverage {

    private static final String HEADLINE = "Frontend resources have no applicable Airness source checks; declare a "
        + "frontend tooling project in airness.node.projects and include these files in its checked source roots "
        + "(src, tests, scripts and airness.sourceRoots). The application may retain its Java runtime";
    private static final List<String> SCRIPTS = List.of(
        ".js", ".mjs", ".cjs", ".jsx", ".ts", ".mts", ".cts", ".tsx"
    );
    private static final List<String> STYLESHEETS = List.of(".css", ".scss");
    private static final List<String> UNREAD = List.of(".sass", ".less");
    private static final String MANIFEST = "package.json";
    private static final String INSTALLED = "node_modules";
    private static final String DEPENDENCY = "; if it is a third-party distribution, take it from a dependency "
        + "(a WebJar, or an npm package the build copies) instead of keeping a copy";

    private static final String STALE = " is excused by the harness, but no unchecked authored file is there any "
        + "more; remove the entry";

    private final int inventoried;
    private final List<String> offences;

    /**
     * Inventories one module, excusing the files the harness itself records for it.
     *
     * @param walk      where the module lies and what its walk passes over
     * @param declared  the node projects the module declares
     * @param generated the repository-relative paths and globs a generator owns or the harness places, read
     *                  only when the module holds a frontend file at all
     * @param excused   module-relative paths the harness excuses, each with its reason. One that names no
     *                  otherwise unchecked file is stale and refused
     */
    FrontendCoverage(
        FrontendWalk walk, NodeProjects declared, Supplier<List<String>> generated, Map<String, String> excused
    ) {
        Path root = walk.root();
        Path basedir = walk.basedir();
        Collection<Path> skipped = walk.skipped();
        Path repository = root.toAbsolutePath().normalize();
        Path module = basedir.toAbsolutePath().normalize();
        List<Path> excluded = skipped.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        List<Path> frontend = NodeManifests.files(module, excluded).stream()
            .filter(file -> extension(file).isPresent())
            .filter(file -> !installed(module.relativize(file)))
            .toList();
        List<PathMatcher> owned = frontend.isEmpty() ? List.of() : generated.get().stream()
            .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
            .toList();
        List<Path> authored = frontend.stream()
            .filter(file -> owned.stream().noneMatch(glob -> glob.matches(repository.relativize(file))))
            .toList();
        Owners ownership = new Owners(module, declared);
        this.inventoried = authored.size();
        List<String> unchecked = authored.stream()
            .filter(file -> !excused.containsKey(relative(module, file)))
            .flatMap(file -> problem(module, file, ownership).stream())
            .toList();
        List<String> present = authored.stream().map(file -> relative(module, file)).toList();
        List<String> stale = excused.keySet().stream()
            .filter(path -> !present.contains(path) || problem(module, module.resolve(path), ownership).isEmpty())
            .sorted()
            .map(path -> path + STALE)
            .toList();
        this.offences = Stream.concat(unchecked.stream(), stale.stream()).toList();
    }

    /**
     * How many authored frontend files the module holds, which the caller logs so a clean verdict states
     * its reach.
     *
     * @return the number of authored frontend files
     */
    int inventoried() {
        return this.inventoried;
    }

    /**
     * The verdict over the inventory.
     *
     * @return the single verdict this check produces
     */
    List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.offences));
    }

    private static String relative(Path module, Path file) {
        return module.relativize(file).toString().replace('\\', '/');
    }

    private static Optional<String> problem(Path module, Path file, Owners ownership) {
        String path = relative(module, file);
        String suffix = extension(file).orElseThrow();
        if (UNREAD.contains(suffix)) {
            return Optional.of(
                path + " is written in " + suffix.substring(1) + ", which no Airness gate reads; write it as CSS "
                    + "or SCSS"
            );
        }
        return ownership.owner(file)
            .map(member -> outsideRoots(module, file, member))
            .orElseGet(() -> Optional.of(path + " lies in no declared node project" + DEPENDENCY));
    }

    // A script directly in the project's top directory is a tooling file the linters and type check read.
    private static Optional<String> outsideRoots(Path module, Path file, NodeMember member) {
        boolean tooling = SCRIPTS.contains(extension(file).orElseThrow()) && member.root().equals(file.getParent());
        if (member.checked(file) || tooling) {
            return Optional.empty();
        }
        String path = relative(module, file);
        String project = Optional.of(module.relativize(member.root()).toString().replace('\\', '/'))
            .filter(key -> !key.isEmpty()).orElse(".");
        return Optional.of(
            path + " lies in the node project " + project + " but outside its checked source roots "
                + member.checkedRoots() + "; move it under one of them or add its directory to airness.sourceRoots"
                + DEPENDENCY
        );
    }

    private static Optional<String> extension(Path file) {
        String name = String.valueOf(file.getFileName()).toLowerCase(Locale.ROOT);
        return Stream.of(SCRIPTS, STYLESHEETS, UNREAD)
            .flatMap(List::stream)
            .filter(name::endsWith)
            .filter(suffix -> name.length() > suffix.length())
            .max(Comparator.comparingInt(String::length));
    }

    private static boolean installed(Path relative) {
        return StreamSupport.stream(relative.spliterator(), false).map(Path::toString).anyMatch(INSTALLED::equals);
    }

    /**
     * Which declared node project, or member of a declared pnpm workspace, owns a file: the nearest one at
     * or above it.
     */
    private static final class Owners {

        private final Path module;
        private final List<Path> projects;
        private final List<PnpmWorkspace> workspaces;

        Owners(Path module, NodeProjects declared) {
            List<NodeProject> inside = declared.projects().stream()
                .filter(project -> !project.outside() && project.hasManifest())
                .toList();
            this.module = module;
            this.projects = inside.stream().map(NodeProject::directory).toList();
            this.workspaces = inside.stream()
                .flatMap(project -> PnpmWorkspace.at(project.directory()).stream())
                .toList();
        }

        Optional<NodeMember> owner(Path file) {
            return Stream.iterate(file.getParent(), Objects::nonNull, Path::getParent)
                .takeWhile(directory -> directory.startsWith(this.module))
                .filter(this::owns)
                .findFirst()
                .map(NodeMember::at);
        }

        private boolean owns(Path directory) {
            boolean member = Files.isRegularFile(directory.resolve(MANIFEST))
                && this.workspaces.stream().anyMatch(workspace -> workspace.covers(directory));
            return member || this.projects.contains(directory);
        }
    }
}
