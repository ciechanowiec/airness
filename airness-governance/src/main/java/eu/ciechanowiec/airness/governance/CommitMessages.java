package eu.ciechanowiec.airness.governance;

import lombok.experimental.UtilityClass;

/**
 * Parses a stored commit message into a {@link CommitMessage}. The header is the first line and the body
 * is everything after it, stripped. No cleanup is applied here: Git performs its configured cleanup
 * before writing a commit, and every line that remains in the stored object is part of the history the
 * policy must inspect.
 */
@UtilityClass
final class CommitMessages {

    private static final String NEWLINE = "\n";

    static CommitMessage parse(String raw) {
        String normalized = raw.strip();
        int newline = normalized.indexOf(NEWLINE);
        String header = newline < 0 ? normalized : normalized.substring(0, newline);
        String body = newline < 0 ? "" : normalized.substring(newline + 1).strip();
        return new CommitMessage(header, body);
    }
}
