package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * The members a pnpm workspace root lists in its {@code pnpm-workspace.yaml}.
 *
 * <p>Each entry of {@code packages} is a glob relative to the root, and an entry starting with {@code !}
 * excludes what it matches. A directory is a member when an including glob matches it and no excluding
 * glob does, which is how pnpm reads the same list. A leading or trailing {@code **} may match no directory,
 * as in pnpm, and syntax whose meaning differs between the two matchers is refused.
 *
 * @param root     the workspace root
 * @param included the globs that admit a member
 * @param excluded the globs that exclude one
 */
record PnpmWorkspace(Path root, List<PathMatcher> included, List<PathMatcher> excluded) {

    private static final String FILE = "pnpm-workspace.yaml";
    private static final String NEGATION = "!";
    private static final String CURRENT = "./";
    private static final String GLOBSTAR_SUFFIX = "/**";
    private static final String GLOBSTAR_PREFIX = "**/";
    private static final Pattern UNSUPPORTED = Pattern.compile("[()!^\\[\\]]");

    /**
     * Copies the globs, so they cannot change after the file was read.
     *
     * @param root     the workspace root
     * @param included the globs that admit a member
     * @param excluded the globs that exclude one
     */
    PnpmWorkspace {
        included = List.copyOf(included);
        excluded = List.copyOf(excluded);
    }

    /**
     * Reads the workspace a directory roots, if it roots one.
     *
     * @param root the directory
     * @return the workspace, or nothing when the directory holds no {@code pnpm-workspace.yaml}
     */
    static Optional<PnpmWorkspace> at(Path root) {
        Path file = root.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        List<String> packages = packages(file);
        return Optional.of(
            new PnpmWorkspace(
                root,
                matchers(file, packages.stream().filter(glob -> !glob.startsWith(NEGATION))),
                matchers(
                    file, packages.stream().filter(glob -> glob.startsWith(NEGATION)).map(glob -> glob.substring(1))
                )
            )
        );
    }

    /**
     * Whether a directory is a member of this workspace.
     *
     * @param directory the directory holding a {@code package.json}
     * @return whether the workspace lists it
     */
    boolean covers(Path directory) {
        Path relative = this.root.relativize(directory);
        return !relative.toString().isEmpty()
            && this.included.stream().anyMatch(glob -> glob.matches(relative))
            && this.excluded.stream().noneMatch(glob -> glob.matches(relative));
    }

    private static List<PathMatcher> matchers(Path file, Stream<String> globs) {
        return globs.map(String::strip)
            .map(glob -> glob.startsWith(CURRENT) ? glob.substring(CURRENT.length()) : glob)
            .map(glob -> glob.endsWith("/") ? glob.substring(0, glob.length() - 1) : glob)
            .filter(glob -> !glob.isEmpty())
            .map(glob -> supported(file, glob))
            .flatMap(PnpmWorkspace::variants)
            .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
            .toList();
    }

    // pnpm's matcher lets a leading or trailing globstar match no directory at all, where a Java glob wants at
    // least one, so each such glob is also read without it. An exclusion that matched less than pnpm's would
    // count as covered a directory pnpm leaves out of the workspace, which no gate would then check.
    private static Stream<String> variants(String glob) {
        String trailing = glob.endsWith(GLOBSTAR_SUFFIX)
            ? glob.substring(0, glob.length() - GLOBSTAR_SUFFIX.length())
            : glob;
        String leading = trailing.startsWith(GLOBSTAR_PREFIX) ? trailing.substring(GLOBSTAR_PREFIX.length()) : trailing;
        String unled = glob.startsWith(GLOBSTAR_PREFIX) ? glob.substring(GLOBSTAR_PREFIX.length()) : glob;
        return Stream.of(glob, trailing, leading, unled).filter(variant -> !variant.isEmpty()).distinct();
    }

    // Extended globs, negated character classes and a negation inside a glob read differently in Java and in
    // pnpm, so a glob using them is refused rather than read the wrong way.
    private static String supported(Path file, String glob) {
        if (UNSUPPORTED.matcher(glob).find()) {
            throw new IllegalStateException(
                file + " lists the workspace glob " + glob + ", whose syntax Airness does not read the way pnpm "
                    + "does; write it with *, ** and {a,b} only"
            );
        }
        return glob;
    }

    private static List<String> packages(Path file) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try {
            Object document = new Yaml(new SafeConstructor(options)).load(Files.readString(file));
            Object packages = document instanceof Map<?, ?> mapping ? mapping.get("packages") : null;
            return packages instanceof Collection<?> entries
                ? entries.stream().map(String::valueOf).toList()
                : List.of();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        } catch (YAMLException exception) {
            throw new IllegalStateException(
                file + " is not valid YAML, so its workspace members cannot be read", exception
            );
        }
    }
}
