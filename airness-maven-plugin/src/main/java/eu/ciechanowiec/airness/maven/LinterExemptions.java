package eu.ciechanowiec.airness.maven;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.SelectorUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * The paths the file linter's configuration exempts by role, read from the effective model rather than written
 * out a second time.
 *
 * <p>The linter's excludes name the binaries it would read as text, the data whose format is a tab, and the
 * directories a tool writes. A check that holds the same tree to the same {@code .editorconfig} exempts the
 * same roles, and reading them where the linter reads them keeps one list rather than two that drift. The
 * patterns are Ant patterns, matched as the linter's directory scanner matches them, against the path from the
 * working tree root written with forward slashes.
 */
final class LinterExemptions {

    private static final String LINTER = "org.ec4j.maven:editorconfig-maven-plugin";
    private static final String EXCLUDES = "excludes";
    private static final String SEPARATOR = "/";

    private final List<String> patterns;

    /**
     * Holds the patterns of the exempt roles.
     *
     * @param patterns Ant patterns, each naming the paths of one role
     */
    LinterExemptions(List<String> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    /**
     * Reads the excludes of the file linter the project's parent configures.
     *
     * @param project the project that owns the repository-wide checks
     * @return the exemptions, none when the linter declares no excludes
     * @throws IllegalStateException when the project configures no file linter, whose roles are then unknown
     */
    static LinterExemptions from(MavenProject project) {
        Xpp3Dom configuration = Optional.ofNullable(project.getPlugin(LINTER))
            .map(plugin -> (Xpp3Dom) plugin.getConfiguration())
            .orElseThrow(() -> new IllegalStateException("Missing inherited file linter configuration " + LINTER));
        List<String> patterns = Optional.ofNullable(configuration.getChild(EXCLUDES))
            .map(excludes -> Arrays.stream(excludes.getChildren()).map(Xpp3Dom::getValue).map(String::strip).toList())
            .orElseGet(List::of);
        return new LinterExemptions(patterns);
    }

    /**
     * The patterns of the exempt roles, which a caller logs so the reach of a clean verdict is on the record.
     *
     * @return the patterns, in the order the configuration declares them
     */
    List<String> patterns() {
        return this.patterns;
    }

    /**
     * Whether a role the linter exempts covers a path.
     *
     * @param relative the path from the working tree root, written with forward slashes
     * @return whether some pattern matches it
     */
    boolean exempts(String relative) {
        return this.patterns.stream().anyMatch(pattern -> SelectorUtils.matchPath(pattern, relative, SEPARATOR, true));
    }
}
