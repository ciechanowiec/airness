package eu.ciechanowiec.airness.governance;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Parses a raw commit message into a {@link CommitMessage}. Comment lines are dropped first, as git's
 * own cleanup does, then the header is the first line and the body is everything after it, stripped.
 * Keeping that cleanup in the history reader also makes the parser deterministic for messages written
 * through tools that preserve a commented template.
 */
@UtilityClass
final class CommitMessages {

    private static final String NEWLINE = "\n";
    private static final String COMMENT = "#";

    static CommitMessage parse(String raw) {
        String normalized = withoutComments(raw).strip();
        int newline = normalized.indexOf(NEWLINE);
        String header = newline < 0 ? normalized : normalized.substring(0, newline);
        String body = newline < 0 ? "" : normalized.substring(newline + 1).strip();
        return new CommitMessage(header, body);
    }

    private static String withoutComments(String raw) {
        return raw.lines()
            .filter(line -> !line.startsWith(COMMENT))
            .collect(Collectors.joining(NEWLINE));
    }
}
