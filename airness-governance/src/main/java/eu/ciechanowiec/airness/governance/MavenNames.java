package eu.ciechanowiec.airness.governance;

import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * The shapes a governed project's Maven coordinates take.
 *
 * <p>A group is a reverse domain of at least two lower-case segments, the convention Maven Central requires, so
 * that two owners never publish under one name. The domains the specification reserves for examples are refused,
 * because a group left at {@code com.example} is a starter or a template nobody finished naming. An artifact is
 * lower-case words joined by hyphens, and a version is three numbers, optionally a SNAPSHOT, which is what the
 * release and freshness checks reason about.
 */
@UtilityClass
public final class MavenNames {

    private static final Pattern REVERSE_DOMAIN = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+");
    private static final Pattern KEBAB = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private static final Pattern PLAIN_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    private static final Set<String> EXAMPLE_DOMAINS = Set.of("com.example", "org.example", "net.example");

    /**
     * Whether a group is a reverse domain of at least two lower-case segments.
     *
     * @param group the groupId
     * @return whether it has that shape
     */
    public static boolean reverseDomain(CharSequence group) {
        return REVERSE_DOMAIN.matcher(group).matches();
    }

    /**
     * Whether a group is, or lies under, a domain reserved for examples.
     *
     * @param group the groupId
     * @return whether it names an example rather than an owner
     */
    public static boolean exampleDomain(String group) {
        return EXAMPLE_DOMAINS.stream().anyMatch(domain -> group.equals(domain) || group.startsWith(domain + '.'));
    }

    /**
     * Whether a name is lower-case words joined by single hyphens.
     *
     * @param name the artifactId
     * @return whether it has that shape
     */
    public static boolean kebab(CharSequence name) {
        return KEBAB.matcher(name).matches();
    }

    /**
     * Whether a version is three numbers joined by dots, optionally followed by {@code -SNAPSHOT}.
     *
     * @param version the version
     * @return whether it has that shape
     */
    public static boolean plainVersion(CharSequence version) {
        return PLAIN_VERSION.matcher(version).matches();
    }
}
