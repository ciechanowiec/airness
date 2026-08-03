package eu.ciechanowiec.airness.maven;

import lombok.experimental.UtilityClass;

/**
 * Refuses a check that read nothing.
 *
 * <p>A check over an empty scope answers exactly as a check over a clean tree does, and the log entry
 * the two produce is the same sentence. That makes a mistyped source root, a renamed directory, or a
 * target pattern aimed at the wrong package look like success, which is worse than a failure because
 * nobody goes looking. So the count travels back out of the check, and the goal that asked for it
 * refuses a zero.
 */
@UtilityClass
final class Scope {

    /**
     * Fails when the check read nothing, naming what it was looking for and where.
     *
     * @param read  how many units the check read
     * @param where the parameter or path that decided the scope
     * @throws IllegalStateException when nothing was read
     */
    static void requireJavaSources(long read, Object where) {
        if (read == 0) {
            throw new IllegalStateException(
                "No Java sources were read, so this check proved nothing. Its scope comes from " + where
            );
        }
    }
}
