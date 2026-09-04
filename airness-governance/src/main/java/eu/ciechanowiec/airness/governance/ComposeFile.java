package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * Reads the images a compose file pulls: the {@code image} of every service under {@code services}
 * that is not built from a Dockerfile of its own.
 *
 * <p>A service with a {@code build} is judged through its Dockerfile, and its {@code image} is only the
 * name the build is tagged with, so it is passed over here. A variable written with a default resolves
 * to the default, which is what compose pulls where nothing set it, and a variable written without one
 * stays in the reference for the rule to report as one it cannot judge.
 *
 * <p>The grammar this needs is a few keys wide, and it is read the way {@link SpringConfiguration} reads
 * its files: a mapping is one level of indentation deeper than its key. The lines under
 * {@code services} are grouped by the service that owns them, and each group is then asked for its
 * image. Anchors, {@code extends}, and the files a compose file includes are not followed. A file that
 * leans on them names its images in those places, and a reference nothing read is not a reference
 * nobody pulls, so a project that wants the verdict writes the image where the reader looks.
 */
@UtilityClass
final class ComposeFile {

    private static final Pattern COMMENTED = Pattern.compile("(?:^|\\s)#");
    private static final String SERVICES = "services";
    private static final String IMAGE = "image";
    private static final String BUILD = "build";
    private static final String COMMENT = "#";
    private static final char SEPARATOR = ':';
    private static final int OUTSIDE = -1;

    /**
     * Every image a service pulls rather than builds.
     *
     * @param yaml the text of the file
     * @return the images with their service and line, in file order
     */
    static List<Located<PulledImage>> images(CharSequence yaml) {
        String[] lines = yaml.toString().split("\n", -1);
        List<Located<Entry>> entries = IntStream.range(0, lines.length)
            .mapToObj(index -> new Located<>(index + 1, Entry.of(lines[index])))
            .filter(entry -> entry.value().hasContent())
            .toList();
        return services(entries).stream().flatMap(group -> image(group).stream()).toList();
    }

    // The lines under the services key, grouped by service. A line at or above the depth of the key
    // closes the block, and the key opens it wherever it sits in the file.
    private static List<List<Located<Entry>>> services(List<Located<Entry>> entries) {
        List<List<Located<Entry>>> groups = new ArrayList<>();
        int servicesIndent = OUTSIDE;
        for (Located<Entry> entry : entries) {
            servicesIndent = step(groups, servicesIndent, entry);
        }
        return groups;
    }

    private static int step(List<List<Located<Entry>>> groups, int servicesIndent, Located<Entry> entry) {
        if (servicesIndent != OUTSIDE && entry.value().indent() > servicesIndent) {
            place(groups, entry);
            return servicesIndent;
        }
        return SERVICES.equals(entry.value().key()) ? entry.value().indent() : OUTSIDE;
    }

    // A line at the depth of the first service starts a new one, and a deeper line belongs to the last.
    private static void place(List<List<Located<Entry>>> groups, Located<Entry> entry) {
        boolean starts = groups.isEmpty() || entry.value().indent() <= groups.getFirst().getFirst().value().indent();
        if (starts) {
            groups.add(new ArrayList<>(List.of(entry)));
        } else {
            groups.getLast().add(entry);
        }
    }

    private static Optional<Located<PulledImage>> image(List<Located<Entry>> group) {
        String service = group.getFirst().value().key();
        boolean built = group.stream().skip(1).anyMatch(entry -> BUILD.equals(entry.value().key()));
        return group.stream()
            .skip(1)
            .filter(entry -> IMAGE.equals(entry.value().key()))
            .findFirst()
            .filter(_ -> !built)
            .map(entry -> new Located<>(entry.line(), new PulledImage(service, entry.value().value())));
    }

    // A '#' opens a comment only where blank space precedes it, which is what YAML says, and a quoted
    // value is taken whole before that rule is applied to it.
    private static String scalar(String value) {
        String stripped = Quotes.stripped(value);
        Matcher comment = COMMENTED.matcher(stripped);
        boolean unquoted = stripped.equals(value.strip());
        String literal = unquoted && comment.find() ? stripped.substring(0, comment.start()) : stripped;
        return VariableDefaults.applied(literal.strip());
    }

    /**
     * One service of a compose file and the image it pulls.
     *
     * @param service the service name
     * @param image   the image reference, with variable defaults applied
     */
    record PulledImage(String service, String image) {
    }

    /**
     * One line of the file, split into the parts the reader keys on.
     *
     * @param indent the leading blank space, which says how deep the key sits
     * @param key    the text before the first colon
     * @param value  the scalar after it, or nothing when the line opens a mapping
     */
    private record Entry(int indent, String key, String value) {

        static Entry of(String raw) {
            String text = raw.strip();
            int separated = text.indexOf(SEPARATOR);
            String key = separated < 0 ? text : text.substring(0, separated).strip();
            String value = separated < 0 ? "" : scalar(text.substring(separated + 1));
            return new Entry(raw.length() - raw.stripLeading().length(), key, value);
        }

        boolean hasContent() {
            return !this.key.isEmpty() && !this.key.startsWith(COMMENT);
        }
    }
}
