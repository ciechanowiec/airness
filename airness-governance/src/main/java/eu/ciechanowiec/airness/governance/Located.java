package eu.ciechanowiec.airness.governance;

/**
 * One value read out of a file, with the line it was read from.
 *
 * <p>An offence names the line, because a file that names three images is repaired one line at a time
 * and a report that named only the file would send the reader back to search it.
 *
 * @param <T>   the kind of value
 * @param line  the one-based line the value was read from
 * @param value what was read there
 */
record Located<T>(int line, T value) {
}
