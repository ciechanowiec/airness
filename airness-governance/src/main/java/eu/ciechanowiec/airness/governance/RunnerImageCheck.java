package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Every hosted Ubuntu runner the repository's CI selects runs the current Ubuntu long-term-support release, named
 * by its release rather than by an alias.
 *
 * <p>An alias such as {@code ubuntu-latest} is moved to another release by the provider, on a schedule of its own,
 * so the environment the verification runs in changes while the repository does not. An earlier release is
 * retired by the provider, and every job naming it then fails or waits for a runner that never comes. Both are
 * repaired by naming the current release, which is the only thing a finding asks for.
 *
 * <p>The files are the GitHub Actions workflows and composite actions {@link BlocklistFiles} finds, and the Azure
 * Pipelines definitions: a file named {@code azure-pipelines.yml} or {@code azure-pipelines.yaml} anywhere, the
 * names the infrastructure scan routes to that framework, and every YAML file under a {@code .azure-pipelines}
 * directory, the conventional home of the templates a definition includes, where the pool is as often chosen.
 *
 * <p>Every hosted Ubuntu label written in those files is judged wherever it stands, rather than only where a
 * {@code runs-on} key or a {@code vmImage} key holds it. A label reaches the runner from a scalar, a flow list, a
 * block list, the {@code labels} of a mapping, a matrix value a {@code ${{ matrix.os }}} expression reads, an
 * {@code include} entry, the default of a reusable workflow's input, a caller's {@code with}, a quoted literal an
 * expression chooses between, or an Azure variable a {@code $(vmImage)} macro reads. Following each of those paths
 * would be a parser of two CI dialects that is right only for the shapes it was taught, while the label itself has
 * one spelling in all of them. An expression or a macro holding no label is passed, because what it reads is
 * judged where that value is written. That is unlike an image reference carrying a variable, which the blocklist
 * reports, because the repository can always write an image out and here the literal already sits elsewhere in
 * the file. A value held outside the repository, such as a repository variable, is not a label the repository
 * writes, and no change to the repository could move it.
 *
 * <p>A label is a whole word: what is joined to it by a hyphen, a slash, a dot, a colon or an at sign is part of
 * another word, so a Docker image such as {@code ubuntu:24.04}, a tag such as {@code base:ubuntu-24.04}, an
 * artifact named {@code geometry-ubuntu-24.04} and a path or a file named after a release are not labels. A
 * comment holds no label. Labels are matched without regard to case, as GitHub matches them. A larger
 * runner whose label carries the release, such as {@code ubuntu-24.04-8-core}, is judged by that release, and the
 * replacement keeps the rest of the label. One whose label carries no release, such as {@code ubuntu-latest-8-core},
 * runs the image its owner configured for it, so its name states nothing to judge. A self-hosted runner labelled
 * with the name of a hosted image is read as that image, since nothing in the file tells the two apart, and
 * relabelling it costs the project nothing.
 *
 * <p>A release later than {@value #CURRENT_RELEASE} passes, so a project can move once its provider offers the
 * next runner and is not stopped until the harness has moved too.
 */
public final class RunnerImageCheck {

    /**
     * The Ubuntu release every hosted runner is required to run, the current long-term-support release.
     *
     * <p>Moved to the next long-term-support release, 28.04 in April 2028, only once GitHub-hosted runners for it
     * are generally available, which typically follows Ubuntu's own release by about five months, and never on
     * Ubuntu's release date. Moved earlier, it fails every governed build with no label to move to.
     */
    static final String CURRENT_RELEASE = "26.04";

    private static final String HEADLINE
        = "Hosted Ubuntu runners a CI file selects by an alias or below Ubuntu " + CURRENT_RELEASE
            + " (name the current long-term-support release)";
    private static final String PREFIX = "ubuntu-";
    private static final String RELEASE = "release";
    private static final String REST = "rest";
    private static final String ALIASED = "an alias its provider moves to another release on its own schedule";
    // A label is a whole word, and the release is two two-digit numbers, so the strings compare as the releases do.
    private static final Pattern LABEL = Pattern.compile(
        "(?<![\\w./:@-])ubuntu-(?:latest|(?<release>\\d{2}\\.\\d{2})(?<rest>(?:-[a-z0-9]+)*))(?![\\w/@-]|[.:]\\w)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMMENT = Pattern.compile("(?:^|\\s)#.*$");
    private static final Pattern AZURE_DEFINITION = Pattern.compile("^azure-pipelines\\.ya?ml$");
    private static final Pattern YAML = Pattern.compile("^.+\\.ya?ml$");
    private static final Path AZURE_TEMPLATES = Path.of(".azure-pipelines");
    private static final char LINE_SEPARATOR = ':';
    private static final String ARM = "-arm";

    private final int scanned;
    private final List<String> offences;

    /**
     * Reads every CI file of the working tree once.
     *
     * @param root the working tree root
     */
    public RunnerImageCheck(Path root) {
        List<Path> tracked = Repository.trackedFiles(root);
        List<Path> files = Stream.concat(
            BlocklistFiles.workflows(root, tracked).stream(),
            tracked.stream().filter(file -> azure(root.relativize(file)))
        ).distinct().toList();
        this.scanned = files.size();
        this.offences = files.stream().flatMap(file -> offences(root, file)).toList();
    }

    /**
     * The GitHub-hosted labels of the required release, for x64 and for Arm, so a tool that must know which
     * labels exist reads them from the release this check requires rather than from a second copy of it.
     *
     * @return the x64 label, then the Arm label
     */
    public static List<String> currentLabels() {
        String label = PREFIX + CURRENT_RELEASE;
        return List.of(label, label + ARM);
    }

    /**
     * How many CI files the check read, which a caller logs so the reach of a clean verdict is on the record.
     *
     * @return the number of files read
     */
    public int scanned() {
        return this.scanned;
    }

    /**
     * Every hosted Ubuntu runner selected by an alias or by a release earlier than the current one.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.offences));
    }

    private static boolean azure(Path relative) {
        String name = String.valueOf(relative.getFileName());
        boolean templated = YAML.matcher(name).matches()
            && Stream.iterate(relative.getParent(), Objects::nonNull, Path::getParent)
                .anyMatch(parent -> AZURE_TEMPLATES.equals(parent.getFileName()));
        return AZURE_DEFINITION.matcher(name).matches() || templated;
    }

    private static Stream<String> offences(Path root, Path file) {
        String relative = root.relativize(file).toString();
        List<String> lines = List.of(Repository.readText(file).orElse("").split("\n", -1));
        return IntStream.range(0, lines.size())
            .boxed()
            .flatMap(
                index -> LABEL.matcher(COMMENT.matcher(lines.get(index)).replaceFirst("")).results()
                    .flatMap(label -> refusal(label).stream())
                    .map(refusal -> refusal.at(relative + LINE_SEPARATOR + (index + 1)))
            );
    }

    private static Optional<Refusal> refusal(MatchResult label) {
        String subject = label.group();
        String release = label.group(RELEASE);
        return release == null
            ? Optional.of(new Refusal(subject, ALIASED, PREFIX + CURRENT_RELEASE))
            : outdated(subject, release, label.group(REST));
    }

    // The replacement keeps what follows the release, so an Arm or a larger runner stays one.
    private static Optional<Refusal> outdated(String subject, String release, String rest) {
        return Optional.of(release)
            .filter(written -> CURRENT_RELEASE.compareTo(written) > 0)
            .map(
                written -> new Refusal(
                    subject, "Ubuntu %s rather than the current long-term-support release".formatted(written),
                    PREFIX + CURRENT_RELEASE + rest
                )
            );
    }
}
