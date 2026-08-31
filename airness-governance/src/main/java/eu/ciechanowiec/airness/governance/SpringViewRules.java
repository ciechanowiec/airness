package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The view names a module's controllers hand back, read against the markup the module ships.
 *
 * <p>A handler that returns a view name states a template in a string, and nothing compiles that
 * string. The template is found by name at the moment a request is answered, so a template renamed or
 * moved leaves every handler that named it compiling, passing every analyzer, and failing on the first
 * request that reaches it. That is the same defect the rule over fragment calls reaches from the other
 * side, and the two share {@link TemplateIndex} so that a name means one thing to both of them.
 *
 * <p>A name is read where it is written plainly, which in practice means a constant rather than a
 * literal, because a handler that returns the same view from four branches names it once and returns
 * the name. A name this cannot resolve to a written string is passed over rather than guessed at, and
 * so is a name a handler builds, since a view chosen at runtime is not a fact about the source.
 *
 * <p>What a redirect or a forward names is an address rather than a template, so neither is read here.
 * An address is answered by a mapping, which is a different question from the one this asks.
 */
@UtilityClass
final class SpringViewRules {

    // Only a plain controller returns a view name. A REST controller and a body-annotated handler
    // return what the response carries, and a string from one of those is content rather than a name.
    private static final Pattern VIEWED = Pattern.compile("@Controller\\b");

    private static final Pattern BODIED = Pattern.compile("@ResponseBody\\b");

    // A returned string, written out or named by a constant. Anything else is built, and what a handler
    // builds is not a fact about the source.
    private static final Pattern RETURNED = Pattern.compile("return\\s+(\"[^\"]*\"|[A-Z][A-Z0-9_]*)\\s*;");

    // The view a model and view is constructed around, which names a template wherever it is written.
    private static final Pattern MODELLED = Pattern.compile(
        "new\\s+ModelAndView\\s*\\(\\s*(\"[^\"]*\"|[A-Z][A-Z0-9_]*)"
    );

    private static final Pattern CONSTANT = Pattern.compile(
        "static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\""
    );

    // What a handler returns when it names an address rather than a template.
    private static final Set<String> DISPATCHED = Set.of("redirect:", "forward:");

    // What parts a view name is written in when it reaches a fragment of a page rather than a page.
    private static final String SEPARATOR = "::";

    // What an address opens with. A view name is resolved against a prefix and so never does.
    private static final String ROOTED = "/";

    /**
     * Every view name a controller of the module hands back that reaches no template it ships.
     *
     * @param types the module already read
     * @param index the markup the module ships
     * @return one offence per unresolved name, by source and line
     */
    static List<String> unresolvedViews(SpringTypes types, TemplateIndex index) {
        return types.all().stream()
            .filter(source -> !source.test())
            .flatMap(source -> unresolved(source, index))
            .toList();
    }

    private static Stream<String> unresolved(SpringTypes.Declared source, TemplateIndex index) {
        String read = source.quoted();
        if (BODIED.matcher(read).find()) {
            return Stream.of();
        }
        Map<String, String> constants = constants(read);
        return named(read).flatMap(
            found -> reported(source, index, constants, found)
        );
    }

    // Every place the source states a view name. A returned string is one only in a plain controller,
    // while a model and view names a template wherever it is constructed.
    private static Stream<MatchResult> named(String read) {
        Stream<MatchResult> returned = VIEWED.matcher(read).find()
            ? RETURNED.matcher(read).results()
            : Stream.of();
        return Stream.concat(returned, MODELLED.matcher(read).results());
    }

    private static Stream<String> reported(
        SpringTypes.Declared source, TemplateIndex index, Map<String, String> constants, MatchResult found
    ) {
        return value(constants, found.group(1))
            .filter(SpringViewRules::names)
            .filter(view -> !reaches(index, view))
            .map(view -> offence(source, found.start(), view))
            .stream();
    }

    // What the written name says, which a literal says itself and a constant says where it was declared.
    // A constant declared in another source says nothing here, and is passed over.
    private static Optional<String> value(Map<String, String> constants, String written) {
        return written.startsWith("\"")
            ? Optional.of(written.substring(1, written.length() - 1))
            : Optional.ofNullable(constants.get(written));
    }

    // Whether a string is a view name at all. An address, an empty string and a sentence are each
    // returned from a controller for reasons that have nothing to do with a template.
    private static boolean names(String view) {
        boolean addressed = view.isEmpty()
            || view.startsWith(ROOTED)
            || DISPATCHED.stream().anyMatch(view::startsWith);
        return !addressed && (!view.contains(" ") || view.contains(SEPARATOR));
    }

    // Whether the markup answers the name, read the same way a fragment expression written in a
    // document is read, since a view name reaching a fragment is written in exactly that shape.
    private static boolean reaches(TemplateIndex index, String view) {
        List<FragmentCall> calls = TemplateCallRules.calls(view);
        return calls.isEmpty() || calls.stream().allMatch(call -> answered(index, call));
    }

    private static boolean answered(TemplateIndex index, FragmentCall call) {
        Optional<Path> template = index.template(call.template());
        return template.isPresent()
            && (call.whole() || index.fragment(template.orElseThrow(), call.fragment()).isPresent());
    }

    private static Map<String, String> constants(String read) {
        return CONSTANT.matcher(read).results().collect(
            Collectors.toMap(match -> match.group(1), match -> match.group(2), (first, _) -> first)
        );
    }

    private static String offence(SpringTypes.Declared source, int at, String view) {
        return source.source() + ": line " + JavaCode.lineOf(source.text(), at) + ": "
            + "the view name " + view + " reaches no template this module ships, so the handler that"
            + " returns it answers its first request with a template that could not be found";
    }
}
