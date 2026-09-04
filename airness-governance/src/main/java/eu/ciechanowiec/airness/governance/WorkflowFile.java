package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads what a GitHub Actions workflow names that the blocklist judges: the images of its service
 * containers, job containers, and Docker actions, and the JDK distribution a setup step installs.
 *
 * <p>A line reader, since each is one key and the value beside it. A job container written as a
 * mapping names its image on an {@code image} line of its own, which the same pattern reads. A value
 * carrying a workflow expression stays as written, and the rule reports it as one it cannot judge.
 */
@UtilityClass
final class WorkflowFile {

    private static final Pattern IMAGE = Pattern.compile("^[ \\t]*(?:-[ \\t]*)?image:[ \\t]*(?<value>[^\\s#].*)$");
    private static final Pattern CONTAINER = Pattern.compile("^[ \\t]*container:[ \\t]*(?<value>[^\\s#].*)$");
    private static final Pattern DOCKER_ACTION = Pattern.compile("uses:[ \\t]*['\"]?docker://(?<value>[^'\"]+)");
    private static final Pattern DISTRIBUTION = Pattern.compile("^[ \\t]*distribution:[ \\t]*(?<value>[^\\s#].*)$");
    private static final Pattern COMMENTED = Pattern.compile("\\s#.*$");
    private static final String COMMENT = "#";

    /**
     * The images a workflow pulls.
     *
     * @param yaml the workflow
     * @return the references with their lines, in file order
     */
    static List<Located<String>> images(String yaml) {
        return values(yaml, DOCKER_ACTION, IMAGE, CONTAINER);
    }

    /**
     * The JDK distributions a workflow installs.
     *
     * @param yaml the workflow
     * @return the distribution names with their lines, in file order
     */
    static List<Located<String>> distributions(String yaml) {
        return values(yaml, DISTRIBUTION);
    }

    private static List<Located<String>> values(String yaml, Pattern... patterns) {
        String[] lines = yaml.split("\n", -1);
        return IntStream.range(0, lines.length)
            .boxed()
            .filter(index -> !lines[index].strip().startsWith(COMMENT))
            .flatMap(index -> valueIn(lines[index], patterns).stream().map(value -> new Located<>(index + 1, value)))
            .toList();
    }

    private static Optional<String> valueIn(String line, Pattern... patterns) {
        return Stream.of(patterns)
            .map(pattern -> pattern.matcher(line))
            .filter(Matcher::find)
            .findFirst()
            .map(matched -> Quotes.stripped(COMMENTED.matcher(matched.group("value")).replaceFirst("")));
    }
}
