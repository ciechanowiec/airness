package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;

/**
 * One fragment declaration, as the document that wrote it named its parts.
 *
 * <p>Where it was written is carried beside what it declares, because a rule reporting a fragment
 * nothing reaches reports on the declaration rather than on a call, and the place a reader has to go
 * is the attribute that wrote it.
 *
 * @param in        the document the declaration was written in
 * @param fragment  the name the fragment is called by
 * @param arguments how many arguments it declares
 * @param line      the line it was written on
 * @param column    the column it was written at
 */
record FragmentDeclaration(Path in, String fragment, int arguments, int line, int column) {
}
