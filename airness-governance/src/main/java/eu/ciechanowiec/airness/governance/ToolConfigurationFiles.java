package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The files the pinned tools read from the tree of their own accord, kept in one inventory.
 *
 * <p>A tool Airness runs takes its configuration from the arguments the harness passes, and also from files
 * it finds beside what it reads. A file of the second kind needs no flag, no plugin declaration and no
 * property, so every rule that closes those leaves it open: gitleaks drops each finding a
 * {@code .gitleaksignore} at the root of the scanned tree lists, whatever {@code --gitleaks-ignore-path} says,
 * and a scanner reads a native configuration file from any directory of its input. Each such file is
 * refused wherever a tool would find it, tracked or not, because the tool reads the tree on disk.
 *
 * <p>One file is the project's and only some of its keys are not. JUnit reads
 * {@code junit-platform.properties} from the test class path, and a project needs it for what the harness
 * does not decide, such as registering an extension for automatic detection. The timeout keys in it are the
 * harness's: the parents pin every one of them as a launcher parameter, which outranks the file, so a key
 * written there would read as a setting while changing nothing.
 *
 * @param root the working tree root
 */
public record ToolConfigurationFiles(Path root) {

    private static final Set<String> SCANNER_CONFIGURATIONS = Set.of(
        ".shellcheckrc", "shellcheckrc", ".checkov.yml", ".checkov.yaml", ".checkov.baseline", ".hadolint.yaml",
        ".hadolint.yml"
    );
    // actionlint reads its configuration from this one place only, and a workflow may be named actionlint.yml.
    private static final Set<Path> PLACED_SCANNER_CONFIGURATIONS = Set.of(
        Path.of(".github", "actionlint.yaml"), Path.of(".github", "actionlint.yml")
    );
    private static final Path SECRET_SCAN_IGNORE = Path.of(".gitleaksignore");
    private static final String JUNIT_PROPERTIES = "junit-platform.properties";
    private static final Pattern TIMEOUT_KEY = Pattern.compile(
        "^\\s*(junit\\.jupiter\\.execution\\.timeout\\.[^\\s=:]+)"
    );

    /**
     * Whether a scanner reads a file of this path as its own configuration.
     *
     * @param relative the path of the file relative to the root of the scanned tree
     * @return whether the file configures a scanner
     */
    public static boolean scannerConfiguration(Path relative) {
        return Optional.ofNullable(relative.getFileName())
            .map(name -> SCANNER_CONFIGURATIONS.contains(name.toString()))
            .orElse(false)
            || PLACED_SCANNER_CONFIGURATIONS.contains(relative);
    }

    /**
     * The verdict over the tree.
     *
     * @return one verdict, clean when no tool would read configuration the harness does not own
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(
                "Tool configuration that the harness owns, which a tool would read from the tree on its own",
                this.problems()
            )
        );
    }

    private List<String> problems() {
        Stream<String> ignore = Stream.of(SECRET_SCAN_IGNORE)
            .filter(path -> Files.exists(this.root.resolve(path), LinkOption.NOFOLLOW_LINKS))
            .map(
                path -> path + ": gitleaks drops every finding this file lists; write the exception in "
                    + ".gitleaks.toml, naming the rule and the exact value"
            );
        Stream<String> tree = Repository.trackedFiles(this.root).stream().flatMap(this::problems);
        return Stream.concat(ignore, tree).sorted().toList();
    }

    private Stream<String> problems(Path file) {
        Path relative = this.root.relativize(file);
        if (scannerConfiguration(relative)) {
            return Stream.of(relative + ": scanner configuration belongs to Airness; remove the local copy");
        }
        return JUNIT_PROPERTIES.equals(relative.getFileName().toString())
            ? timeoutKeys(file).map(key -> relative + ": the harness pins " + key + "; remove the key")
            : Stream.empty();
    }

    private static Stream<String> timeoutKeys(Path file) {
        return Repository.readText(file).stream()
            .flatMap(String::lines)
            .map(TIMEOUT_KEY::matcher)
            .filter(Matcher::find)
            .map(matcher -> matcher.group(1));
    }
}
