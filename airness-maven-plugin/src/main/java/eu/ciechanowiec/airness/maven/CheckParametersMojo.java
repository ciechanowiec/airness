package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The parameters the harness cannot default are set, and the two that describe the same thing agree.
 *
 * <p>Each of these has the same failure mode, which is why they are checked before any analyzer runs
 * rather than being left to fail where they are read. Left at {@code UNSET}, the group root reaches the
 * fully-qualified-name rule as an alternative that matches nothing while the rule keeps firing on
 * {@code java} and {@code javax}, so it enforces less than it states while looking configured. Left at
 * {@code UNSET}, the package root makes the null-checker treat every class as unannotated and makes the
 * mutation analysis find no mutants, which reports as a perfect kill rate.
 *
 * <p>A derived default would be worse than none. Deriving the package root from the coordinates is right
 * for a project whose artifactId is one word and wrong for every hyphenated one, and a default that is
 * usually right is a default nobody checks.
 */
@Mojo(name = "check-parameters", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class CheckParametersMojo extends PreflightMojo {

    private static final String UNSET = "UNSET";

    /**
     * This project's own group root, regex-escaped, as the fully-qualified-name rule reads it.
     */
    @Parameter(property = "airness.group.root", defaultValue = UNSET)
    private String groupRoot;

    /**
     * The package every class of this project lives under, unescaped.
     */
    @Parameter(property = "airness.package.root", defaultValue = UNSET)
    private String packageRoot;

    /**
     * The entry files this project ships, comma-separated, or {@code NONE}.
     */
    @Parameter(property = "airness.entry.files")
    private String entryFiles;

    /**
     * The documents the prose lint reads, comma-separated, or {@code NONE}.
     */
    @Parameter(property = "airness.docs")
    private String docs;

    @Override
    protected List<String> problems() {
        this.record();
        return Stream.of(
            set(this.groupRoot, "airness.group.root", "this project's group root, regex-escaped"),
            set(this.packageRoot, "airness.package.root", "the package every class lives under"),
            this.agreement(),
            declared(this.entryFiles, "airness.entry.files"),
            declared(this.docs, "airness.docs")
        ).flatMap(Optional::stream).toList();
    }

    private void record() {
        this.getLog().info("airness.group.root = " + this.groupRoot);
        this.getLog().info("airness.package.root = " + this.packageRoot);
        this.getLog().info("airness.entry.files = " + this.entryFiles);
        this.getLog().info("airness.docs = " + this.docs);
    }

    /**
     * Whether the escaped group root and the plain package root still describe the same project.
     *
     * <p>They are written twice because one is a regex and the other is not, and two spellings of one
     * fact drift. When they do, each half stays plausible on its own and only the pair shows it.
     *
     * @return the problem, if the two disagree
     */
    private Optional<String> agreement() {
        String plain = this.groupRoot.replace("\\", "");
        boolean stated = !UNSET.equals(this.groupRoot) && !UNSET.equals(this.packageRoot);
        boolean agree = this.packageRoot.startsWith(plain);
        return Optional.of("airness.package.root (" + this.packageRoot + ") does not start with airness.group.root ("
            + plain + "), so the two describe different projects")
            .filter(problem -> stated && !agree);
    }

    private static Optional<String> set(String value, String property, String what) {
        return Optional.of("Set " + property + " to " + what + ", which the harness cannot guess")
            .filter(problem -> UNSET.equals(value));
    }

    private static Optional<String> declared(String value, String property) {
        return Optional.of(
            "Set " + property + ", or the literal " + Sentinel.NONE + " to declare this project has none. "
                + "An empty value would pass by checking nothing, which reads the same as checking a clean tree"
        ).filter(problem -> Optional.ofNullable(value).map(String::strip).orElse("").isEmpty());
    }
}
