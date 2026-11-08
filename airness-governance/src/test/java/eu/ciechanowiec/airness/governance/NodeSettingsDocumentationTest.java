package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Every key the node harness reads under {@code airness} in a project's {@code package.json} is listed in the
 * agent guide's Node Project Settings table and in the README's key table, which are the two places a reader
 * looks for what a node project may declare. A key added to the harness alone would otherwise stay unknown to
 * the agents the guide instructs.
 */
class NodeSettingsDocumentationTest {

    private static final Path SOURCES = Path.of("airness-node", "src");
    private static final List<String> READERS = List.of(
        "settings.ts", "browser-settings.ts", "invocation-settings.ts", "gate-wiring.ts"
    );
    private static final Pattern KEY = Pattern.compile(
        "raw\\['([A-Za-z]+)']"
            + "|_SETTING: string = '([A-Za-z]+)'"
            + "|readKey\\(readKey\\([^)]+, 'airness'\\), '([A-Za-z]+)'\\)"
    );
    private static final List<String> KNOWN_KEYS = List.of(
        "serve", "serverUrl", "sourceRoots", "testWrapper", "vulnerabilityAllowlist", "javascriptAllowlist"
    );
    private static final Path GUIDE = Path.of(
        "airness-assets/src/main/resources/airness/files/generic/.airness/agent-guide.md.asset"
    );
    private static final String GUIDE_TABLE = "### Node Project Settings";
    private static final String README_TABLE = "A node project declares what only it knows";
    private static final String TABLE_END = "|===";

    @Test
    void readsAtLeastTheKeysEveryBrowserApplicationDeclares() {
        Set<String> keys = keys();
        assertEquals(
            List.of(),
            KNOWN_KEYS.stream().filter(key -> !keys.contains(key)).toList(),
            "the key scan still finds the keys of every reader"
        );
    }

    @Test
    void listsEveryKeyInTheGuide() {
        String guide = read(GUIDE);
        assertEquals(List.of(), undocumented(guide.substring(guide.indexOf(GUIDE_TABLE))), "the guide's table");
    }

    @Test
    void listsEveryKeyInTheReadme() {
        String readme = read(Path.of("README.adoc"));
        int start = readme.indexOf(README_TABLE);
        int end = readme.indexOf(TABLE_END, readme.indexOf(TABLE_END, start) + TABLE_END.length());
        assertEquals(List.of(), undocumented(readme.substring(start, end)), "the README's key table");
    }

    private static List<String> undocumented(String table) {
        return keys().stream().filter(key -> !table.contains('`' + key + '`')).toList();
    }

    private static Set<String> keys() {
        return READERS.stream().map(reader -> read(SOURCES.resolve(reader)))
            .flatMap(source -> KEY.matcher(source).results())
            .map(NodeSettingsDocumentationTest::key)
            .collect(TreeSet::new, Set::add, Set::addAll);
    }

    private static String key(MatchResult match) {
        return Stream.of(match.group(1), match.group(2), match.group(3))
            .filter(Objects::nonNull).findFirst().orElseThrow();
    }

    @SneakyThrows
    private static String read(Path relative) {
        return Files.readString(SelfModules.repository().resolve(relative));
    }
}
