package eu.ciechanowiec.airness.governance;

import lombok.experimental.UtilityClass;

/**
 * The one wildcard the blocklist understands: a trailing asterisk, which matches every name under the
 * prefix before it.
 *
 * <p>A richer grammar would let an entry be written that its author cannot read back, and the entries
 * are read far more often than they are written. A prefix covers every case the table needs, which is a
 * whole group ({@code org.mongodb}), a group and everything beneath it ({@code com.oracle.database.}),
 * an artifact family ({@code spring-cloud-starter-consul}), and an image namespace ({@code bitnami/}).
 */
@UtilityClass
final class NamePattern {

    private static final String WILDCARD = "*";

    /**
     * Whether a name is the pattern, or lies under the prefix a trailing asterisk names.
     *
     * @param pattern the pattern, with or without a trailing asterisk
     * @param name    the name to place against it
     * @return whether the pattern covers the name
     */
    static boolean matches(String pattern, String name) {
        return pattern.endsWith(WILDCARD)
            ? name.startsWith(pattern.substring(0, pattern.length() - WILDCARD.length()))
            : name.equals(pattern);
    }
}
