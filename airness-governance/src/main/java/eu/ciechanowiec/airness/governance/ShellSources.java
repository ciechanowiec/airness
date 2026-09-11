package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Selects shell entry points while retaining library checks in their callers' variable context.
 *
 * <p>Checking a sourced library as an independent executable accuses its caller-supplied variables of
 * being unassigned. ShellCheck's check-sourced option instead checks that same library with the caller,
 * preserving both the finding and the context needed to decide whether it is a defect.
 */
@UtilityClass
public final class ShellSources {

    private static final Pattern SOURCE = Pattern.compile("(?m)^\\s*(?:\\.|source)\\s+[\"']?([^\\s\"';]+)");
    private static final Pattern PREFIX = Pattern.compile("^\\$(?:\\{\\w+}|\\w+)/");

    /**
     * Scripts not sourced by another selected script. Cyclic source graphs cannot produce a clean empty scan.
     *
     * @param root    repository root
     * @param scripts all discovered shell inputs
     * @return entry points, or all scripts when the graph has no entry point
     */
    public static List<Path> entryPoints(Path root, Collection<Path> scripts) {
        Set<Path> sourced = scripts.stream().flatMap(file -> references(root, file).stream())
            .filter(scripts::contains).collect(Collectors.toUnmodifiableSet());
        List<Path> entries = new ArrayList<>(scripts.stream().filter(file -> !sourced.contains(file)).toList());
        Set<Path> reached = new HashSet<>();
        entries.forEach(entry -> visit(root, entry, reached));
        for (Path script : scripts) {
            if (!reached.contains(script)) {
                entries.add(script);
                visit(root, script, reached);
            }
        }
        return List.copyOf(entries);
    }

    private static void visit(Path root, Path entry, Set<Path> reached) {
        Deque<Path> remaining = new ArrayDeque<>(List.of(entry));
        while (!remaining.isEmpty()) {
            Path script = remaining.removeFirst();
            if (reached.add(script) && script.startsWith(root)) {
                remaining.addAll(references(root, script));
            }
        }
    }

    static List<Path> references(Path root, Path script) {
        String text = Repository.readText(script).orElse("");
        return references(root, text);
    }

    static List<Path> references(Path root, String text) {
        return SOURCE.matcher(text).results().map(match -> match.group(1))
            .map(reference -> PREFIX.matcher(reference).replaceFirst(""))
            .map(root::resolve).map(Path::normalize).toList();
    }
}
