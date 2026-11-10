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
 * gets an answer whichever the project wrote. A map key written in brackets, as Spring requires when the
 * key carries dots of its own, is read as the flattened key the brackets stand for.
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
 * <p>Nesting is read from the column each key starts at rather than from a fixed indent width. YAML lets a
 * file indent by any number of spaces, and dividing by two read four-space nesting as twice as deep as it
 * is, so a sibling became the child of the key before it. A key belongs under the nearest key above it that
 * starts further left.
 *
 * <p>Each setting also records which document of the file it sits in. One file may hold several,
 * separated by {@code ---} in YAML and by a {@code #---} or {@code !---} line in properties, and the same key
 * in two of them is the ordinary way a profile overrides a default rather than a key written twice by
 * accident. A properties separator is read as Spring Boot reads it: written from the first column, and
 * not directly after a comment opened by the same character, which makes it one more line of that comment.
 * A comment met between a separator and the first setting after it reopens the document before, because
 * that is where Spring Boot binds what follows.
 *
 * <p>An item of a list written by its index, as {@code include[0]}, is read as the list's key followed by
 * the index, so a rule asking about the list is answered by each item as well as by the list written whole.
 */
final class SpringConfiguration {

    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|'[^']*'");
    private static final Pattern REPEATED_DOTS = Pattern.compile("\\.{2,}");
    private static final Pattern EDGE_DOTS = Pattern.compile("^\\.+|\\.+$");
    private static final Pattern COMMENTED = Pattern.compile("(?:^|\\s)#");
    private static final Pattern PROPERTIES_DOCUMENT = Pattern.compile("[#!]---\\s*");
    private static final Pattern INDEX = Pattern.compile("\\.\\d+");
    // The canonical prefix of spring.config.activate.on-profile and of every other activation condition.
    private static final String CONDITION = "spring.config.activate.";
    private static final String SPACE = " ";
    private static final String COMMENT = "#";
    private static final String PROPERTIES_COMMENT = "!";
    private static final String DOCUMENT = "---";
    private static final String SEQUENCE = "-";
    private static final String PROPERTIES = ".properties";
    private static final char SEPARATOR = ':';
    private static final char ASSIGNMENT = '=';
    private static final int FIRST = 0;
    private static final int UNCOMMENTED = -1;
    private static final int NONE = -1;

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
     * Every setting of a key in every document, and every item of it written by its index.
     *
     * @param key the key, in any spelling Spring accepts
     * @return the settings, in the order they are written
     */
    List<Setting> declarations(String key) {
        String wanted = canonical(key);
        return this.settings.stream()
            .filter(setting -> wanted.equals(setting.key()) || item(wanted, setting.key()))
            .toList();
    }

    /**
     * The first setting of a key, if the file declares one.
     *
     * @param key the key, in any spelling Spring accepts
     * @return the setting, when it is declared
     */
    Optional<Setting> declared(String key) {
        String wanted = canonical(key);
        return this.settings.stream().filter(setting -> setting.key().equals(wanted)).findFirst();
    }

    /**
     * Every setting beneath a prefix, which is how a subsystem announces itself.
     *
     * @param prefix the key prefix, in any spelling Spring accepts
     * @return the settings configuring it, in the order they are written
     */
    List<Setting> beneath(String prefix) {
        String wanted = canonical(prefix);
        return this.settings.stream().filter(setting -> setting.key().startsWith(wanted)).toList();
    }

    /**
     * Whether a document applies only under a condition it states, such as an active profile, rather than
     * wherever the file is read.
     *
     * @param document the zero-based document of the file
     * @return whether the document states a condition for applying at all
     */
    boolean conditional(int document) {
        return this.settings.stream()
            .anyMatch(setting -> setting.document() == document && setting.key().startsWith(CONDITION));
    }

    /**
     * The key as Spring binds it, so that every spelling of one setting compares equal.
     *
     * <p>A map key that carries dots of its own is written in brackets, as {@code [mail.smtp.timeout]},
     * and in YAML the brackets sit inside quotation marks. Both are notation rather than name, so they
     * are taken off here and the key inside them joins the path with a dot, which is how Spring flattens
     * it whether the brackets follow a dot or stand in its place. The relaxed spelling is applied to the
     * key inside the brackets as well, although Spring keeps a map key as written, so a dash inside one
     * is read here as generously as a dash anywhere else.
     *
     * @param key the key as written
     * @return the canonical form
     */
    static String canonical(String key) {
        String bare = key.toLowerCase(Locale.ROOT)
            .replace("-", "")
            .replace("_", "")
            .replace("\"", "")
            .replace("'", "")
            .replace("[", ".")
            .replace("]", "");
        return EDGE_DOTS.matcher(REPEATED_DOTS.matcher(bare).replaceAll(".")).replaceAll("");
    }

    private void readProperties(List<String> lines) {
        Cursor cursor = new Cursor(FIRST, NONE, UNCOMMENTED);
        for (int number = 0; number < lines.size(); number++) {
            cursor = this.property(lines.get(number), number + 1, cursor);
        }
    }

    private Cursor property(String line, int number, Cursor cursor) {
        String text = line.strip();
        boolean assigns = !cursor.separates(line) && !commented(text) && !text.isEmpty();
        if (assigns) {
            this.assignment(text, number, cursor.document());
        }
        return assigns ? cursor.assigned() : cursor.passed(line, text);
    }

    private static boolean commented(String text) {
        return text.startsWith(COMMENT) || text.startsWith(PROPERTIES_COMMENT);
    }

    private void assignment(String line, int number, int document) {
        int at = separator(line);
        if (at < 0) {
            this.unreadable.add("line " + number + ": " + line);
        } else {
            String written = line.substring(0, at).strip();
            this.settings.add(
                new Setting(canonical(written), written, line.substring(at + 1).strip(), number, document)
            );
        }
    }

    private static boolean item(String list, String key) {
        return key.startsWith(list) && INDEX.matcher(key.substring(list.length())).matches();
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
        Deque<Level> path = new ArrayDeque<>();
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

    private void entry(Deque<Level> path, String line, int number, int document) {
        String text = line.strip();
        int column = line.length() - line.stripLeading().length();
        int separated = text.indexOf(SEPARATOR);
        if (separated < 0 || text.startsWith(SEQUENCE)) {
            this.unreadable.add("line " + number + ": " + text);
            return;
        }
        String written = text.substring(0, separated).strip();
        nest(path, new Level(column, canonical(written)));
        String value = scalar(text.substring(separated + 1).strip());
        if (!value.isEmpty()) {
            String key = String.join(".", path.stream().map(Level::key).toList());
            this.settings.add(new Setting(key, written, value, number, document));
        }
    }

    // A key sits under the nearest key above it written further left, so every level at its column or
    // deeper is closed before it is placed.
    private static void nest(Deque<Level> path, Level level) {
        while (!path.isEmpty() && path.getLast().column() >= level.column()) {
            path.removeLast();
        }
        path.addLast(level);
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

    // One key of the path to a setting, with the column it starts at.
    private record Level(int column, String key) {
    }

    /**
     * Where a properties file is read up to, kept as Spring Boot keeps it. A separator written directly
     * beneath a comment opened by the same character continues that comment, so the character of the last
     * comment is remembered until a blank line or an assignment. A comment met before the first setting
     * of a new document reopens the last document holding one, so the separator above it opens nothing.
     *
     * @param document the document a setting read now belongs to
     * @param filled   the last document holding a setting, or NONE
     * @param comment  the character that opened the last line, when it was a comment, or UNCOMMENTED
     */
    private record Cursor(int document, int filled, int comment) {

        boolean separates(String line) {
            return PROPERTIES_DOCUMENT.matcher(line).matches() && line.charAt(FIRST) != this.comment;
        }

        // A line that assigns nothing: a separator, a comment, or a blank line.
        Cursor passed(String line, String text) {
            if (this.separates(line)) {
                return new Cursor(this.document + 1, this.filled, this.comment);
            }
            return text.isEmpty() ? this.blank() : this.commented(text.charAt(FIRST));
        }

        private Cursor commented(int opening) {
            boolean reopened = this.filled != NONE && this.filled < this.document;
            return new Cursor(reopened ? this.filled : this.document, this.filled, opening);
        }

        private Cursor blank() {
            return new Cursor(this.document, this.filled, UNCOMMENTED);
        }

        Cursor assigned() {
            return new Cursor(this.document, this.document, UNCOMMENTED);
        }
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
