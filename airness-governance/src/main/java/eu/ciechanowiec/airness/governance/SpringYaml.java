package eu.ciechanowiec.airness.governance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a YAML configuration file into the flat settings Spring would bind from it, for
 * {@link SpringConfiguration}, whose documentation describes what is read and how.
 *
 * <p>Kept apart from the properties reader because the two share nothing but the setting they produce:
 * YAML is read from the column each key starts at and the items of its lists, properties from one key on
 * each line.
 */
final class SpringYaml {

    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|'[^']*'");
    private static final Pattern COMMENTED = Pattern.compile("(?:^|\\s)#");
    private static final String SPACE = " ";
    private static final String COMMENT = "#";
    private static final String DOCUMENT = "---";
    private static final String SEQUENCE = "-";
    private static final char SEPARATOR = ':';
    private static final int FIRST = 0;

    private final Deque<Level> path;
    private final List<SpringConfiguration.Setting> settings;
    private final List<String> unreadable;

    /**
     * Reads the lines of one file.
     *
     * @param lines the lines of the file, in order
     */
    SpringYaml(List<String> lines) {
        this.path = new ArrayDeque<>();
        this.settings = new ArrayList<>();
        this.unreadable = new ArrayList<>();
        int document = FIRST;
        for (int number = 0; number < lines.size(); number++) {
            String line = lines.get(number);
            String text = line.strip();
            if (DOCUMENT.equals(text)) {
                this.path.clear();
                document += 1;
            } else if (hasContent(text)) {
                this.entry(line, number + 1, document);
            }
        }
    }

    /**
     * Every setting the file declares, flattened to a dotted key.
     *
     * @return the settings, in the order they are written
     */
    List<SpringConfiguration.Setting> settings() {
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

    private void entry(String written, int number, int document) {
        String text = written.strip();
        Line line = new Line(text, written.length() - written.stripLeading().length(), number, document);
        if (sequenced(text)) {
            this.item(line);
        } else if (text.indexOf(SEPARATOR) < 0 || text.startsWith(SEQUENCE)) {
            this.unread(line);
        } else {
            this.mapping(line, text.indexOf(SEPARATOR));
        }
    }

    private void mapping(Line line, int separated) {
        String written = line.text().substring(0, separated).strip();
        this.nest(new Level(line.column(), SpringConfiguration.canonical(written), false));
        String value = scalar(line.text().substring(separated + 1).strip());
        if (!value.isEmpty()) {
            this.settings.add(
                new SpringConfiguration.Setting(this.joined(), written, value, line.number(), line.document())
            );
        }
    }

    /*
     * The item is placed under the key that opens the list, by its index, and what follows the dash is
     * read as the item's content at the column it starts at. That content is a scalar unless it holds a
     * colon followed by a space or ending the line, which is what makes a YAML mapping key, so a value
     * such as an address keeps the colon of its scheme.
     */
    private void item(Line line) {
        String text = line.text();
        String content = text.substring(SEQUENCE.length()).stripLeading();
        int index = this.opened(line.column());
        if (this.path.isEmpty() || sequenced(content)) {
            this.unread(line);
            return;
        }
        String list = this.path.getLast().key();
        this.path.addLast(new Level(line.column(), String.valueOf(index), true));
        int separated = keyed(content);
        if (separated >= 0) {
            this.mapping(line.within(content, line.column() + text.length() - content.length()), separated);
        } else if (!content.isEmpty()) {
            this.settings.add(
                new SpringConfiguration.Setting(
                    this.joined(), list + "[" + index + "]", scalar(content), line.number(), line.document()
                )
            );
        }
    }

    private void unread(Line line) {
        this.unreadable.add("line " + line.number() + ": " + line.text());
    }

    private String joined() {
        return String.join(".", this.path.stream().map(Level::key).toList());
    }

    // A key sits under the nearest key above it written further left, so every level at its column or
    // deeper is closed before it is placed.
    private void nest(Level level) {
        while (!this.path.isEmpty() && this.path.getLast().column() >= level.column()) {
            this.path.removeLast();
        }
        this.path.addLast(level);
    }

    /*
     * Closes every level deeper than the dash and answers the index of the item it opens. An item already
     * open at the dash's own column is the one before it in the same list, so the new one follows it.
     * Anything else at or left of the dash is the key that opens the list, and the item is its first.
     */
    private int opened(int column) {
        while (!this.path.isEmpty() && this.path.getLast().column() > column) {
            this.path.removeLast();
        }
        boolean following = !this.path.isEmpty() && this.path.getLast().item()
            && this.path.getLast().column() == column;
        return following ? Integer.parseInt(this.path.removeLast().key()) + 1 : 0;
    }

    private static boolean sequenced(String text) {
        return SEQUENCE.equals(text) || text.startsWith(SEQUENCE + SPACE);
    }

    private static int keyed(String content) {
        int spaced = content.indexOf(SEPARATOR + SPACE);
        boolean ending = !content.isEmpty() && content.charAt(content.length() - 1) == SEPARATOR;
        return spaced >= 0 || !ending ? spaced : content.length() - 1;
    }

    /*
     * A '#' opens a comment only where whitespace precedes it and no quotation mark is open, which is
     * what YAML says. The quoted spans are blanked to spaces of their own length first, so the search
     * runs over a text in which a '#' inside a value cannot be found while every offset still lines up
     * with the value the caller holds. This is how JavaCode reads a source around its literals.
     */
    private static String scalar(String value) {
        String readable = QUOTED.matcher(value).replaceAll(SpringYaml::blanked);
        Matcher comment = COMMENTED.matcher(readable);
        return comment.find() ? value.substring(FIRST, comment.start()).strip() : value;
    }

    private static String blanked(MatchResult quoted) {
        return SPACE.repeat(quoted.group().length());
    }

    private static boolean hasContent(String line) {
        return !line.isEmpty() && !line.startsWith(COMMENT);
    }

    // One key of the path to a setting, with the column it starts at and whether it is a list item's index.
    private record Level(int column, String key, boolean item) {
    }

    // One line with content: its text without the indent, the column the text starts at, and where it sits.
    private record Line(String text, int column, int number, int document) {

        Line within(String content, int start) {
            return new Line(content, start, this.number, this.document);
        }
    }
}
