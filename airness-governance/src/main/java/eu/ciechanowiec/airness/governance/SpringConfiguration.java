package eu.ciechanowiec.airness.governance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an application configuration file into the flat settings Spring would bind from it.
 *
 * <p>The grammar this needs is a few keys wide: nested maps in YAML, one key per line in properties, and
 * a scalar at the end of each. That is small enough to read directly, and a library would have to be
 * added to a module that currently declares four dependencies and reads every other format by hand.
 *
 * <p>A line this reader cannot account for is reported rather than skipped, the way
 * {@link SecretScanConfiguration} reports one. A reader that passed over what it did not recognise would
 * pass over a misspelled key in silence, and that silence is the thing being removed.
 *
 * <p>Keys are canonicalised the way Spring binds them, so {@code open-in-view}, {@code openInView} and
 * {@code OPEN_IN_VIEW} are one setting rather than three. A rule therefore asks about one spelling and
 * gets an answer whichever the project wrote.
 *
 * <p>A trailing comment is removed from a YAML value rather than left on it. Keeping it made the value a
 * rule compares against the whole of {@code true # only in development}, which no rule matches, so a
 * setting was excused by the note written beside it. Properties keep every {@code #} they carry, because
 * there the character opens a comment only at the start of a line.
 *
 * <p>A sequence entry is reported rather than read. Splitting {@code - name: first} on its colon made a
 * key out of the dash and the text beside it, which no rule could match and no reader could recognise as
 * the item of a list, so the line was neither honoured nor mentioned. The scalar items of a sequence
 * were already reported, and this puts the mapping items beside them.
 *
 * <p>Each setting also records which document of the file it sits in. One file may hold several,
 * separated by {@code ---}, and the same key in two of them is the ordinary way a profile overrides a
 * default rather than a key written twice by accident.
 */
final class SpringConfiguration {

    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|'[^']*'");
    private static final Pattern COMMENTED = Pattern.compile("(?:^|\\s)#");
    private static final String SPACE = " ";
    private static final String COMMENT = "#";
    private static final String DOCUMENT = "---";
    private static final String SEQUENCE = "-";
    private static final String PROPERTIES = ".properties";
    private static final char SEPARATOR = ':';
    private static final char ASSIGNMENT = '=';
    private static final int INDENT = 2;
    private static final int FIRST = 0;

    private final List<Setting> settings;
    private final List<String> unreadable;

    /**
     * Reads one configuration file.
     *
     * @param name    the file name, which decides whether it is read as YAML or as properties
     * @param content the text of the file
     */
    SpringConfiguration(String name, CharSequence content) {
        this.settings = new ArrayList<>();
        this.unreadable = new ArrayList<>();
        List<String> lines = List.of(content.toString().split("\n", -1));
        if (name.endsWith(PROPERTIES)) {
            this.readProperties(lines);
        } else {
            this.readYaml(lines);
        }
    }

    /**
     * Every setting the file declares, flattened to a dotted key.
     *
     * @return the settings, in the order they are written
     */
    List<Setting> settings() {
        return List.copyOf(this.settings);
    }

    /**
     * Every line the reader could not account for.
     *
     * @return the offending lines, in the order they are written
     */
    List<String> unreadable() {
        return List.copyOf(this.unreadable);
    }

    /**
     * The setting a key names, if the file declares one.
     *
     * @param key the key, in any spelling Spring accepts
     * @return the setting, when it is declared
     */
    Optional<Setting> declared(String key) {
        String wanted = canonical(key);
        return this.settings.stream().filter(setting -> setting.key().equals(wanted)).findFirst();
    }

    /**
     * Whether the file declares any key beneath a prefix, which is how a subsystem announces itself.
     *
     * @param prefix the key prefix, in any spelling Spring accepts
     * @return whether anything under it is configured
     */
    boolean configures(String prefix) {
        String wanted = canonical(prefix);
        return this.settings.stream().anyMatch(setting -> setting.key().startsWith(wanted));
    }

    /**
     * The key as Spring binds it, so that every spelling of one setting compares equal.
     *
     * @param key the key as written
     * @return the canonical form
     */
    static String canonical(String key) {
        return key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private void readProperties(List<String> lines) {
        for (int number = 0; number < lines.size(); number++) {
            String line = lines.get(number).strip();
            if (hasContent(line)) {
                this.assignment(line, number + 1);
            }
        }
    }

    private void assignment(String line, int number) {
        int at = separator(line);
        if (at < 0) {
            this.unreadable.add("line " + number + ": " + line);
        } else {
            String written = line.substring(0, at).strip();
            this.settings.add(
                new Setting(canonical(written), written, line.substring(at + 1).strip(), number, FIRST)
            );
        }
    }

    private static int separator(String line) {
        int assigned = line.indexOf(ASSIGNMENT);
        int separated = line.indexOf(SEPARATOR);
        boolean unassigned = assigned < 0;
        boolean colonFirst = separated >= 0 && separated < assigned;
        boolean colon = unassigned || colonFirst;
        return colon ? separated : assigned;
    }

    private void readYaml(List<String> lines) {
        Deque<String> path = new ArrayDeque<>();
        int document = FIRST;
        for (int number = 0; number < lines.size(); number++) {
            String line = lines.get(number);
            String text = line.strip();
            if (DOCUMENT.equals(text)) {
                path.clear();
                document += 1;
            } else if (hasContent(text)) {
                this.entry(path, line, number + 1, document);
            }
        }
    }

    private void entry(Deque<String> path, String line, int number, int document) {
        String text = line.strip();
        int depth = (line.length() - line.stripLeading().length()) / INDENT;
        int separated = text.indexOf(SEPARATOR);
        if (separated < 0 || text.startsWith(SEQUENCE)) {
            this.unreadable.add("line " + number + ": " + text);
            return;
        }
        while (path.size() > depth) {
            path.removeLast();
        }
        String written = text.substring(0, separated).strip();
        path.addLast(canonical(written));
        String value = scalar(text.substring(separated + 1).strip());
        if (!value.isEmpty()) {
            this.settings.add(new Setting(String.join(".", path), written, value, number, document));
        }
    }

    /*
     * A '#' opens a comment only where whitespace precedes it and no quotation mark is open, which is
     * what YAML says. The quoted spans are blanked to spaces of their own length first, so the search
     * runs over a text in which a '#' inside a value cannot be found while every offset still lines up
     * with the value the caller holds. This is how JavaCode reads a source around its literals.
     */
    private static String scalar(String value) {
        String readable = QUOTED.matcher(value).replaceAll(SpringConfiguration::blanked);
        Matcher comment = COMMENTED.matcher(readable);
        return comment.find() ? value.substring(FIRST, comment.start()).strip() : value;
    }

    private static String blanked(MatchResult quoted) {
        return SPACE.repeat(quoted.group().length());
    }

    private static boolean hasContent(String line) {
        return !line.isEmpty() && !line.startsWith(COMMENT);
    }

    /**
     * One setting, by canonical key, written value, and the line it sits on.
     *
     * @param key      the key as Spring binds it
     * @param raw      the key as the project wrote it, which the canonical form discards
     * @param value    the value as written, quotation marks included and any trailing comment removed
     * @param line     the one-based line the setting sits on
     * @param document the zero-based document of the file, which {@code ---} separates
     */
    public record Setting(String key, String raw, String value, int line, int document) {
    }
}
