package eu.ciechanowiec.airness.governance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The frontmatter of an agent skill manifest, {@code SKILL.md}, and the defects that stop an agent from
 * discovering or invoking the skill.
 *
 * <p>Only the frontmatter is judged. A skill body is a knowledge file with tables and examples, so rules
 * about its prose would reject valid skills. Only flat top-level keys are read: a line indented under a
 * nested key such as {@code metadata} is part of that key, and a folded or literal block scalar carries its
 * value on the indented lines beneath it.
 *
 * @param present whether the file opens with a frontmatter block
 * @param keys    each top-level key with its value
 */
record SkillManifest(boolean present, Map<String, String> keys) {

    // The keys the agent runtime recognises. Anything else is dead metadata that should not accrue.
    private static final Set<String> ALLOWED = Set.of(
        "name", "description", "license", "allowed-tools", "argument-hint", "disable-model-invocation", "model",
        "metadata"
    );
    private static final Pattern KEBAB = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern FRONTMATTER = Pattern.compile("^---\\n([\\s\\S]*?)\\n---");
    private static final Pattern KEY = Pattern.compile("^([\\w-]+):[ \\t]?(.*)$");
    private static final Pattern BLOCK_SCALAR = Pattern.compile("^[>|][-+]?\\d*$");
    private static final Pattern CONTINUATION = Pattern.compile("^\\s+\\S");
    // The description has to say when to reach for the skill. The word need not end there, because
    // "whenever the user asks" says the same thing.
    private static final Pattern WHEN = Pattern.compile("(?i)\\bwhen");
    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";

    /**
     * Copies the keys, so the manifest cannot change after it was read.
     *
     * @param present whether the file opens with a frontmatter block
     * @param keys    each top-level key with its value
     */
    SkillManifest {
        keys = Map.copyOf(keys);
    }

    /**
     * Reads the frontmatter of a manifest.
     *
     * @param content the file
     * @return the frontmatter, absent when the file does not open with one
     */
    static SkillManifest parse(String content) {
        Matcher matcher = FRONTMATTER.matcher(content.replace("\r\n", "\n"));
        if (!matcher.find()) {
            return new SkillManifest(false, Map.of());
        }
        List<String> lines = List.of(matcher.group(1).split("\n", -1));
        Map<String, String> keys = new LinkedHashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            Matcher key = KEY.matcher(lines.get(index));
            if (key.matches()) {
                keys.put(key.group(1), value(lines, index, key.group(2).strip()).strip());
            }
        }
        return new SkillManifest(true, keys);
    }

    /**
     * Every defect of this frontmatter, each as a rule and a reason.
     *
     * @param directory the name of the directory holding the manifest, which the name has to equal
     * @return the defects, empty when an agent can discover and invoke the skill
     */
    List<String> violations(String directory) {
        if (!this.present) {
            return List.of("[frontmatter] file must open with valid \"---\" frontmatter");
        }
        return Stream.of(this.name(directory), this.description(), this.unknownKeys())
            .flatMap(List::stream)
            .toList();
    }

    private List<String> name(String directory) {
        Optional<String> name = Optional.ofNullable(this.keys.get(NAME));
        if (name.isEmpty()) {
            return List.of("[name] frontmatter has no \"name\" key");
        }
        String value = name.get();
        return Stream.of(
            Optional.of("[name] name \"" + value + "\" must be kebab-case")
                .filter(_ -> !KEBAB.matcher(value).matches()),
            Optional.of("[name] name \"" + value + "\" must equal parent directory \"" + directory + '"')
                .filter(_ -> !value.equals(directory))
        ).flatMap(Optional::stream).toList();
    }

    private List<String> description() {
        String description = this.keys.getOrDefault(DESCRIPTION, "");
        if (description.isEmpty()) {
            return List.of("[description] frontmatter has no \"description\" key");
        }
        return WHEN.matcher(description).find()
            ? List.of()
            : List.of("[description] description must say when to use the skill (\"when ...\" clause)");
    }

    private List<String> unknownKeys() {
        return this.keys.keySet().stream()
            .filter(key -> !ALLOWED.contains(key))
            .sorted()
            .map(key -> "[keys] unknown frontmatter key \"" + key + '"')
            .toList();
    }

    // A key's value, joined with the indented lines beneath it when the value is a block scalar.
    private static String value(List<String> lines, int index, String raw) {
        if (!BLOCK_SCALAR.matcher(raw).matches()) {
            return raw;
        }
        return lines.subList(index + 1, lines.size()).stream()
            .takeWhile(line -> CONTINUATION.matcher(line).find())
            .map(String::strip)
            .collect(Collectors.joining(" "));
    }
}
