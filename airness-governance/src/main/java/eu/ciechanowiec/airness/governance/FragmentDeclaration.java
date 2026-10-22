package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;

/**
 * One fragment declaration, as the document that wrote it named its parts.
 *
 * <p>Where it was written is carried beside what it declares, because a rule reporting a fragment
 * nothing reaches reports on the declaration rather than on a call, and the place a reader has to go
 * is the attribute that wrote it.
 *
 * @param in         the document the declaration was written in
 * @param fragment   the name the fragment is called by
 * @param parameters the argument names it declares
 * @param line       the line it was written on
 * @param column     the column it was written at
 */
record FragmentDeclaration(Path in, String fragment, List<String> parameters, int line, int column) {

    FragmentDeclaration {
        parameters = List.copyOf(parameters);
    }
}
