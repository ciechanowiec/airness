package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.RunnerImageCheck;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The configuration the harness writes for actionlint into the staged tree it lints, which declares only the hosted
 * runner labels {@link RunnerImageCheck} requires.
 *
 * <p>actionlint knows the hosted runner labels of its own release and reports any other label as unknown, and its
 * newest release predates the Ubuntu release the harness requires, so without this every workflow that obeys the
 * runner rule would break the actionlint rules. The labels are declared as runner labels and nothing else: no
 * finding is ignored, and a label neither the release nor this file knows is still reported. A project's own
 * configuration stays refused by the inventory, so this file is the only one actionlint reads.
 */
@UtilityClass
final class ActionlintConfiguration {

    private static final String HEADER = "self-hosted-runner:\n  labels:\n";

    /**
     * The configuration text.
     *
     * @return the YAML that declares every label the runner rule requires
     */
    static String text() {
        return RunnerImageCheck.currentLabels().stream()
            .map(label -> "    - " + label + "\n")
            .collect(Collectors.joining("", HEADER, ""));
    }

    /**
     * Writes the configuration where actionlint reads it in a tree.
     *
     * @param tree the root of the tree actionlint lints
     * @throws IOException when the file cannot be written
     */
    static void writeInto(Path tree) throws IOException {
        Path github = Files.createDirectories(tree.resolve(".github"));
        Files.writeString(github.resolve("actionlint.yaml"), text());
    }
}
