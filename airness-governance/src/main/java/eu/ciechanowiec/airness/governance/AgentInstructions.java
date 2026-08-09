package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Checks and synchronizes the one Airness-owned section at the start of project-owned {@code AGENTS.md}.
 */
public final class AgentInstructions {

    static final String BEGIN = "<!-- BEGIN AIRNESS MANAGED INSTRUCTIONS -->";
    static final String END = "<!-- END AIRNESS MANAGED INSTRUCTIONS -->";
    private static final String LF = "\n";
    private static final char LF_CHARACTER = '\n';

    private final Path root;
    private final String canonical;

    /**
     * Describes the repository and the exact section shipped by its active Airness version.
     *
     * @param root      repository root
     * @param canonical complete managed section
     */
    public AgentInstructions(Path root, String canonical) {
        this.root = real(root);
        this.canonical = canonical;
        if (!canonical.startsWith(BEGIN + LF) || !canonical.endsWith(END + LF)) {
            throw new IllegalArgumentException("Canonical agent instructions need the fixed marker lines");
        }
    }

    /**
     * Whether the file begins with the exact managed section.
     *
     * @param content complete instruction-file content
     * @return whether the managed section is current
     */
    public boolean current(CharSequence content) {
        return content.toString().startsWith(this.canonical);
    }

    /**
     * Whether marker corruption makes an automatic edit ambiguous.
     *
     * @param content complete instruction-file content
     * @return whether synchronization must refuse the file
     */
    public boolean malformed(CharSequence content) {
        String held = content.toString();
        int begins = occurrences(held, BEGIN);
        int ends = occurrences(held, END);
        boolean absent = begins == 0 && ends == 0;
        return !absent && !wellFormed(held, begins, ends);
    }

    private static boolean wellFormed(String held, int begins, int ends) {
        if (begins != 1 || ends != 1 || !held.startsWith(BEGIN + LF)) {
            return false;
        }
        int ending = held.indexOf(END);
        return ending > BEGIN.length() && lineEnd(held, ending + END.length());
    }

    /**
     * Creates, prepends, or refreshes the managed section while preserving project prose.
     *
     * @return whether the file changed
     */
    public boolean write() {
        Path file = this.safe();
        String held = this.read(file);
        if (this.malformed(held)) {
            throw new IllegalStateException(
                "AGENTS.md has malformed, duplicate, or non-leading Airness instruction markers; repair them manually"
            );
        }
        if (this.current(held)) {
            return false;
        }
        String replacement = held.contains(BEGIN) ? this.replace(held) : this.prepend(held);
        writeFile(file, replacement);
        return true;
    }

    private String replace(String held) {
        int after = held.indexOf(END) + END.length();
        boolean newline = after < held.length() && held.charAt(after) == LF_CHARACTER;
        int remainder = newline ? after + 1 : after;
        return this.canonical + held.substring(remainder);
    }

    private String prepend(CharSequence held) {
        return held.isEmpty() ? this.canonical : this.canonical + LF + held;
    }

    private String read(Path file) {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return "";
        }
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private Path safe() {
        Path target = this.root.resolve(EntryFileRules.INSTRUCTIONS).normalize();
        if (!target.startsWith(this.root)) {
            throw new IllegalStateException(
                "Agent instruction path escapes the repository root: " + EntryFileRules.INSTRUCTIONS
            );
        }
        Path relative = this.root.relativize(target);
        IntStream.rangeClosed(1, relative.getNameCount())
            .mapToObj(index -> this.root.resolve(relative.subpath(0, index)))
            .filter(Files::isSymbolicLink)
            .findFirst()
            .ifPresent(AgentInstructions::rejectLink);
        return target;
    }

    private static void rejectLink(Path link) {
        throw new IllegalStateException("Agent instruction path crosses a symbolic link: " + link);
    }

    private static int occurrences(CharSequence content, String marker) {
        return Math.toIntExact(Pattern.compile(Pattern.quote(marker)).matcher(content).results().count());
    }

    private static boolean lineEnd(CharSequence content, int offset) {
        return offset == content.length() || content.charAt(offset) == LF_CHARACTER;
    }

    private static void writeFile(Path file, CharSequence content) {
        try {
            Files.writeString(file, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write " + file, exception);
        }
    }

    private static Path real(Path root) {
        try {
            return root.toRealPath();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not resolve repository root " + root, exception);
        }
    }
}
