package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * One file of a bundle, read as the names it declares and the line each was declared on.
 *
 * <p>Only the names are read. What they are worth is the translation itself, which no rule of a
 * harness has any business judging, so the value is passed over and a name written twice is reported
 * whether the two say the same thing or not.
 *
 * <p>The file is read line by line rather than through the runtime's own loader, because the loader
 * answers a map and a map has neither an order nor a second entry under one name. Both are what this
 * is read for: a name declared twice is invisible to a map, which keeps the last of them.
 *
 * <p>A line is read without what closes it, because a tree written under the other line ending closes
 * every one of them with a carriage return, and the carry is read off the last character there is.
 *
 * <p>A value carried onto the next line takes that line with it. Written as a map the continuation is
 * part of a value, and read as lines it looks exactly like a name being declared, so a reader that did
 * not follow the carry would report a sentence of prose as a duplicated name.
 */
final class MessageBundle {

    private static final String SEPARATORS = "=: \t\f";
    private static final char CARRY = '\\';
    private static final char HASH = '#';
    private static final char BANG = '!';
    private static final int PAIRED = 2;
    private static final char RETURN = '\r';

    private final List<Declaration> declarations;

    /**
     * Reads the given file into the names it declares.
     *
     * @param content the text of one file of a bundle
     */
    MessageBundle(CharSequence content) {
        Collection<Declaration> found = new ArrayList<>();
        List<String> lines = List.of(content.toString().split("\n", -1));
        boolean carried = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = unclosed(lines.get(index));
            boolean continues = carried;
            carried = escaped(line, line.length());
            if (!continues) {
                declared(line, index + 1).ifPresent(found::add);
            }
        }
        this.declarations = List.copyOf(found);
    }

    /**
     * Every name this file declares, in the order it declares them and once per declaration.
     *
     * @return the declarations of this file
     */
    List<Declaration> declarations() {
        return this.declarations;
    }

    // The line without what closes it. A tree written under the other line ending closes every line
    // with a carriage return, and the carry is read off the last character there is, so a line left
    // holding one would never be read as carrying. Only that character is taken: a space after the
    // carry is what stops it carrying, and stripping the end of the line would carry anyway.
    private static String unclosed(String line) {
        boolean closed = !line.isEmpty() && line.charAt(line.length() - 1) == RETURN;
        return closed ? line.substring(0, line.length() - 1) : line;
    }

    // Whether what stands at the given place is carried in by an escape, which an odd number of
    // escapes before it means. An even number is that many escaped escapes, and carries nothing
    // beyond itself.
    //
    // The end of a line is a place like any other here: a line carries its value onto the next one
    // exactly when the character that would follow it would have been escaped.
    private static boolean escaped(String line, int at) {
        int escapes = 0;
        for (int before = at - 1; before >= 0 && line.charAt(before) == CARRY; before--) {
            escapes++;
        }
        return escapes % PAIRED != 0;
    }

    private static Optional<Declaration> declared(String line, int number) {
        String bare = line.stripLeading();
        if (bare.isEmpty() || bare.charAt(0) == HASH || bare.charAt(0) == BANG) {
            return Optional.empty();
        }
        return Optional.of(new Declaration(name(bare), number));
    }

    // The name is what stands before the first separator that is not escaped, written without the
    // escapes that carried a separator into it.
    private static String name(String line) {
        return unescaped(line.substring(0, ends(line)));
    }

    // Where the name ends. A separator that an escape carried in is part of the name rather than the
    // end of it.
    private static int ends(String line) {
        for (int at = 0; at < line.length(); at++) {
            if (SEPARATORS.indexOf(line.charAt(at)) >= 0 && !escaped(line, at)) {
                return at;
            }
        }
        return line.length();
    }

    // The name as a runtime holds it, which is without the escapes. An escape carries the character
    // after it into the name and is not itself part of what the name is looked up under.
    private static String unescaped(String written) {
        StringBuilder name = new StringBuilder(written.length());
        for (int at = 0; at < written.length(); at++) {
            if (written.charAt(at) != CARRY || escaped(written, at)) {
                name.append(written.charAt(at));
            }
        }
        return name.toString();
    }

    /**
     * One name a file declares, and where it declared it.
     *
     * @param name the name a runtime would answer a text under
     * @param line the line it was declared on, counted from one
     */
    public record Declaration(String name, int line) {
    }
}
