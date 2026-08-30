package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the dependencies of one module say they bind, indexed so a written key can be asked about.
 *
 * <p>Three questions are asked of this, and the third is the one that decides how the index is built. A
 * key may be deprecated, which the supplier states outright. A key may be one the metadata names, which
 * is a lookup. Or a key may be one nothing names, and that is not the same as a key that is wrong: a
 * project is free to bind its own settings, and a starter that publishes no metadata declares nothing
 * while still reading what it declares nowhere.
 *
 * <p>So an unnamed key is reported only when a declared group already claims the namespace above it.
 * {@code spring.jpa.hiberante.ddl-auto} is answerable because {@code spring.jpa} is a declared group and
 * that group knows nothing of {@code hiberante}, while a project's own {@code acme.retry.attempts} is
 * left alone because no group claims {@code acme}. That is what keeps the rule silent about the keys it
 * has no standing to judge, which matters here more than elsewhere, because a finding cannot be waived.
 *
 * <p>Open containers end the questioning. A key bound into a map or into properties has whatever names
 * the project puts under it, so {@code logging.level.org.hibernate} is declared by {@code logging.level}
 * being a map and not by anything naming that logger.
 */
final class SpringMetadata {

    private static final List<String> CONTAINERS = List.of("java.util.Map", "java.util.Properties");
    private static final String SEPARATOR = ".";

    private final Set<String> names;
    private final List<String> open;
    private final List<String> groups;
    private final Map<String, ConfigurationProperty.Deprecation> deprecations;

    /**
     * Indexes what the dependencies published.
     *
     * @param published every metadata entry read from the classpath, in any order
     */
    SpringMetadata(Collection<ConfigurationProperty> published) {
        this.names = published.stream()
            .map(entry -> SpringConfiguration.canonical(entry.name()))
            .collect(Collectors.toUnmodifiableSet());
        this.open = published.stream()
            .filter(SpringMetadata::container)
            .map(entry -> SpringConfiguration.canonical(entry.name()))
            .toList();
        this.groups = published.stream()
            .filter(ConfigurationProperty::group)
            .map(entry -> SpringConfiguration.canonical(entry.name()))
            .toList();
        this.deprecations = published.stream()
            .filter(entry -> entry.deprecation().deprecated())
            .collect(
                Collectors.toUnmodifiableMap(
                    entry -> SpringConfiguration.canonical(entry.name()),
                    ConfigurationProperty::deprecation,
                    (first, _) -> first
                )
            );
    }

    /**
     * Whether nothing was read, which makes every answer below meaningless rather than reassuring.
     *
     * @return whether the classpath published no metadata at all
     */
    boolean empty() {
        return this.names.isEmpty();
    }

    /**
     * What the supplier of a key says about withdrawing it.
     *
     * @param key the key as the project wrote it
     * @return the deprecation, when the key carries one
     */
    Optional<ConfigurationProperty.Deprecation> deprecation(String key) {
        return Optional.ofNullable(this.deprecations.get(SpringConfiguration.canonical(key)));
    }

    /**
     * Whether some dependency declares this key, either by name or by opening a container above it.
     *
     * @param key the key as the project wrote it
     * @return whether anything on the classpath binds it
     */
    boolean known(String key) {
        String wanted = SpringConfiguration.canonical(key);
        return this.names.contains(wanted) || beneath(this.open, wanted).isPresent();
    }

    /**
     * The declared group that claims the namespace a key sits in, which is what gives standing to say
     * the key is not one of its own.
     *
     * @param key the key as the project wrote it
     * @return the most specific group above the key, when one is declared
     */
    Optional<String> anchor(String key) {
        return beneath(this.groups, SpringConfiguration.canonical(key));
    }

    private static Optional<String> beneath(Collection<String> prefixes, String key) {
        return prefixes.stream()
            .filter(prefix -> key.startsWith(prefix + SEPARATOR))
            .max(Comparator.comparingInt(String::length));
    }

    private static boolean container(ConfigurationProperty entry) {
        return CONTAINERS.stream().anyMatch(entry.type()::startsWith);
    }
}
