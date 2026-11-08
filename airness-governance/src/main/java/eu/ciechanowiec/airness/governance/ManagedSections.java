package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The marked blocks a {@link AssetPolicy#SECTION} file carries, one per layer that claims it.
 *
 * <p>The blocks lead the file, in layer order and separated by one blank line, and everything after
 * them belongs to the project. Requiring the exact leading text rather than each block wherever it sits
 * keeps one answer to two questions at once: whether every block is current, and whether a block nobody
 * claims, or a marker left from an earlier layout, survives further down.
 */
@UtilityClass
final class ManagedSections {

    private static final String BEGIN_TEXT = "BEGIN AIRNESS MANAGED";
    private static final String END_TEXT = "END AIRNESS MANAGED";
    private static final Pattern MARKER = Pattern.compile("(?:BEGIN|END) AIRNESS MANAGED");
    private static final Pattern PAIRED = Pattern.compile("(?:B,E,)*");
    private static final Pattern BLOCK = Pattern.compile(
        "(?m)^.*BEGIN AIRNESS MANAGED.*\\n(?:.*\\n)*?.*END AIRNESS MANAGED.*(?:\\n|\\z)"
    );
    private static final Pattern LEADING_BLANK_LINES = Pattern.compile("\\A(?:[ \\t]*\\n)+");
    private static final String BEGIN = "BEGIN AIRNESS MANAGED: ";
    private static final String END = "END AIRNESS MANAGED: ";
    private static final String MARKDOWN = ".md";
    private static final String LF = "\n";

    /**
     * One block as it appears in the file, markers included.
     *
     * @param path  the managed path, whose kind decides the comment syntax of the markers
     * @param owner the layer that owns the block
     * @param body  the block's body as shipped
     * @return the block, ending with a newline
     */
    static String block(String path, Layer owner, String body) {
        String terminated = body.endsWith(LF) ? body : body + LF;
        return marker(path, BEGIN + owner.id()) + LF + terminated + marker(path, END + owner.id()) + LF;
    }

    /**
     * The leading text a file with these blocks must start with.
     *
     * @param blocks the blocks, in layer order
     * @return the blocks separated by one blank line
     */
    static String compose(Collection<String> blocks) {
        return String.join(LF, blocks);
    }

    /**
     * Whether the file starts with exactly the expected blocks and carries no other marker.
     *
     * @param content the file content
     * @param leading the composed blocks
     * @return whether the managed part is current
     */
    static boolean current(String content, String leading) {
        return content.startsWith(leading) && !MARKER.matcher(content.substring(leading.length())).find();
    }

    /**
     * The project's part of a file once every managed block, current, stale or unclaimed, is removed.
     *
     * @param content the file content
     * @return the project's lines, or nothing when the markers do not pair up and an edit would guess
     */
    static Optional<String> projectPart(String content) {
        String markers = content.lines().map(ManagedSections::token).collect(Collectors.joining());
        return Optional.of(content)
            .filter(_ -> PAIRED.matcher(markers).matches())
            .map(text -> LEADING_BLANK_LINES.matcher(BLOCK.matcher(text).replaceAll("")).replaceFirst(""));
    }

    // One token per marker line, so a well-formed file reads as begin, end, begin, end. A line carrying
    // both markers yields one token of its own, which pairs with nothing.
    private static String token(String line) {
        String token = (line.contains(BEGIN_TEXT) ? "B" : "") + (line.contains(END_TEXT) ? "E" : "");
        return token.isEmpty() ? token : token + ',';
    }

    /**
     * The file as the sync writes it: the blocks first, then a blank line and the project's part.
     *
     * @param leading the composed blocks
     * @param project the project's part
     * @return the file content
     */
    static String write(String leading, String project) {
        return project.isEmpty() ? leading : leading + LF + project;
    }

    private static String marker(String path, String text) {
        return path.endsWith(MARKDOWN) ? "<!-- " + text + " -->" : "# " + text;
    }
}
