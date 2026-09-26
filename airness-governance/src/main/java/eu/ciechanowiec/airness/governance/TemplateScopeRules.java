package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * What an element binds into the markup under it, and whether a value reads one of those names.
 *
 * <p>Two attributes bind. One states a list of assignments, and the other names what it walks a
 * collection as, which is one name or a pair of them. An iteration naming one name binds a second
 * alongside it, because the engine supplies the status of the walk under the same name with a suffix,
 * and a fragment reading that status is as far from its binding as one reading the item.
 *
 * <p>Whether a value reads a name is asked of one name at a time rather than answered by listing
 * every name a value reads. The names worth asking about are the ones an element above has bound,
 * which is a handful, while the identifiers an expression carries include every utility object, every
 * method and every property of everything it touches. Asking the narrow question keeps this from
 * needing an expression parser, and keeps it from reporting a page because it mentioned a property
 * that happens to share a name with a binding.
 *
 * <p>A name is read only where it is the root of what an expression names. A property of something
 * else is not this name, and neither is a longer name beginning with these letters.
 */
@UtilityClass
final class TemplateScopeRules {

    // What walks a collection separates what it is walked as from the collection itself. The first one
    // is the separator, because what precedes it is a list of names and holds no expression.
    private static final char SEPARATES = ':';

    private static final String NAMES = ",";

    // What the engine calls the status of a walk that never named one, which is the name of the item
    // with this after it.
    private static final String STATUS = "Stat";

    private static final Set<String> WITH = Set.of("th:with", "data-th-with");

    private static final Set<String> EACH = Set.of("th:each", "data-th-each");

    // A name given a value, at the start of a list of assignments or after the comma that ends the one
    // before it. An equals sign inside an expression is a comparison rather than an assignment, and is
    // passed over because what precedes it opened a brace rather than a list.
    private static final Pattern ASSIGNMENT = Pattern.compile("(?:^|,)\\s*([A-Za-z_]\\w*)\\s*=");

    // What has to sit before a name for the name to be the root of what an expression reads. Neither a
    // word character nor a dot may, which is what tells this name apart from a longer one holding it
    // and from a property of something else carrying it.
    private static final String ROOT = "(?<![\\w.])";

    /**
     * Every name an element binds into the markup under it.
     *
     * @param attributes what the element carries, spelled in lower case
     * @return the names, and none where the element binds nothing
     */
    static Set<String> bound(Map<String, String> attributes) {
        return Stream.concat(
            named(attributes, WITH, TemplateScopeRules::assigned),
            named(attributes, EACH, TemplateScopeRules::iterated)
        ).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether the given value reads the given name as the root of an expression.
     *
     * @param written what an attribute or an inlined expression carries
     * @param name    the name asked about
     * @return whether the value reads it
     */
    static boolean reads(String written, String name) {
        return Pattern.compile(ROOT + Pattern.quote(name) + "\\b").matcher(written).find();
    }

    private static Stream<String> named(
        Map<String, String> attributes, Set<String> spellings, Function<String, Collection<String>> reading
    ) {
        return attributes.entrySet()
            .stream()
            .filter(entry -> spellings.contains(entry.getKey()))
            .flatMap(entry -> reading.apply(entry.getValue()).stream());
    }

    // The names a list of assignments gives values to, which are the names it binds.
    private static Collection<String> assigned(String written) {
        Matcher given = ASSIGNMENT.matcher(written);
        Collection<String> names = new ArrayList<>();
        while (given.find()) {
            names.add(given.group(1));
        }
        return names;
    }

    // What a walk binds, which is what it is walked as and the status of the walk beside it. A walk
    // naming both binds both and nothing else, and one naming only the item is given the status under
    // the name of the item with a suffix, so a fragment can read it and this has to know it exists.
    private static Collection<String> iterated(String written) {
        int separates = written.indexOf(SEPARATES);
        String walked = separates < 0 ? "" : written.substring(0, separates);
        List<String> names = Arrays.stream(walked.split(NAMES))
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .toList();
        return names.size() == 1 ? List.of(names.getFirst(), names.getFirst() + STATUS) : names;
    }
}
