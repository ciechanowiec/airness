package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * What an action's metadata file, its {@code action.yml} or {@code action.yaml}, says about how it runs: the
 * runtime its {@code runs.using} key names, and the actions its own steps use when it is a composite action.
 *
 * <p>A line reader like {@link WorkflowFile}, since the runtime is one key under one top-level key. The
 * runtime is the {@code using} key written directly under the top-level {@code runs} key, at the indentation of
 * that mapping's first key, or inside a flow mapping written on the {@code runs} line itself. A {@code using}
 * key anywhere else, deeper in a step or in a script a step runs, is not the runtime. Comments are ignored,
 * quoted spans included.
 *
 * @param runtime the runtime as written, lowercased, with its line, or nothing when no {@code runs.using} is
 *                written
 * @param steps   the {@code uses} values of the file, with their lines
 */
record ActionMetadata(Optional<Located<String>> runtime, List<Located<String>> steps) {

    private static final Pattern RUNS = Pattern.compile("^runs:(?<rest>\\s.*|)$");
    private static final Pattern USING = Pattern.compile("^using:[ \\t]*(?<value>\\S.*)$");
    private static final Pattern FLOW_USING = Pattern.compile("[{,][ \\t]*using:[ \\t]*(?<value>[^,}\\s]+)");
    private static final Pattern NODE = Pattern.compile("node(?<major>\\d{1,4})");

    /**
     * Copies the steps, so the answer cannot change after it was read.
     *
     * @param runtime the runtime
     * @param steps   the steps
     */
    ActionMetadata {
        steps = List.copyOf(steps);
    }

    /**
     * Reads one metadata file.
     *
     * @param yaml the metadata as text
     * @return what it says about how the action runs
     */
    static ActionMetadata read(String yaml) {
        return new ActionMetadata(runtime(List.of(yaml.split("\n", -1))), WorkflowFile.actions(yaml));
    }

    /**
     * The runtime's number when it is a Node runtime, such as {@code 20} for {@code node20}.
     *
     * @param runtime the runtime as written
     * @return the Node major version, or nothing for {@code docker}, {@code composite} or anything else
     */
    static Optional<Integer> nodeVersion(String runtime) {
        Matcher node = NODE.matcher(runtime);
        return node.matches() ? Optional.of(Integer.parseInt(node.group("major"))) : Optional.empty();
    }

    private static Optional<Located<String>> runtime(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            Matcher runs = RUNS.matcher(YamlText.uncommented(lines.get(index)));
            if (runs.matches()) {
                int runsLine = index;
                return flow(runs.group("rest"), runsLine).or(() -> block(lines, runsLine));
            }
        }
        return Optional.empty();
    }

    private static Optional<Located<String>> flow(CharSequence rest, int index) {
        Matcher using = FLOW_USING.matcher(rest);
        return using.find() ? Optional.of(located(index, using.group("value"))) : Optional.empty();
    }

    // The block under the runs key ends at the first significant line back at the left margin, and its keys sit at
    // the indentation of its first significant line.
    private static Optional<Located<String>> block(List<String> lines, int runsLine) {
        List<Integer> significant = IntStream.range(runsLine + 1, lines.size())
            .filter(index -> !YamlText.insignificant(lines.get(index)))
            .boxed()
            .takeWhile(index -> YamlText.indent(lines.get(index)) > 0)
            .toList();
        if (significant.isEmpty()) {
            return Optional.empty();
        }
        int keys = YamlText.indent(lines.get(significant.getFirst()));
        return significant.stream()
            .filter(index -> YamlText.indent(lines.get(index)) == keys)
            .map(index -> Map.entry(index, USING.matcher(YamlText.uncommented(lines.get(index)).strip())))
            .filter(entry -> entry.getValue().matches())
            .findFirst()
            .map(entry -> located(entry.getKey(), entry.getValue().group("value")));
    }

    private static Located<String> located(int index, String value) {
        return new Located<>(index + 1, Quotes.stripped(value).toLowerCase(Locale.ROOT));
    }
}
