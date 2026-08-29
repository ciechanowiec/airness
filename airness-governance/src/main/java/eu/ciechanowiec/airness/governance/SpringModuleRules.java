package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reports the defects whose two halves are written in different files.
 *
 * <p>Each rule here reads the whole of a module and then judges one file in it, which is what separates
 * these from every other Spring rule Airness states. A controller returning a type says nothing on its
 * own, because the type being an entity is written somewhere else entirely, and only the two together
 * are the defect. So the module is collected first by {@link SpringTypes} and questioned afterwards.
 */
@UtilityClass
final class SpringModuleRules {

    private static final Pattern ENTITY = Pattern.compile("@Entity\\b");
    private static final Pattern CONTROLLER = Pattern.compile("@(?:RestController|Controller)\\b");
    /*
     * The argument list is consumed rather than left behind, so the span that follows the annotation
     * begins at the return type instead of somewhere inside the path the mapping declares.
     */
    private static final Pattern MAPPING = Pattern.compile(
        "@(?:Get|Post|Put|Delete|Patch|Request)Mapping\\b\\s*(?:\\([^)]*\\))?"
    );
    private static final Pattern REQUEST_BODY = Pattern.compile("@RequestBody\\b([^,)]*)");
    private static final char OPENING = '(';

    /**
     * Every place a persistence entity is carried by a web signature.
     *
     * @param types the module already read
     * @return one offence per signature, by source and line
     */
    static List<String> exposedEntities(SpringTypes types) {
        Set<String> entities = types.named(ENTITY);
        return entities.isEmpty()
            ? List.of()
            : types.carrying(CONTROLLER).stream()
                .flatMap(controller -> exposures(controller, entities))
                .toList();
    }

    private static Stream<String> exposures(SpringTypes.Declared controller, Collection<String> entities) {
        return Stream.concat(accepted(controller, entities), returned(controller, entities));
    }

    private static Stream<String> accepted(SpringTypes.Declared controller, Collection<String> entities) {
        return REQUEST_BODY.matcher(controller.code()).results()
            .filter(body -> mentions(body.group(1), entities))
            .map(
                body -> offence(
                    controller, body.start(),
                    "an entity accepted as a request body lets whoever calls the endpoint set every column"
                        + " it declares, the identifier and the audit fields included, because binding"
                        + " writes the fields it is given rather than the ones the endpoint meant"
                )
            );
    }

    private static Stream<String> returned(SpringTypes.Declared controller, Collection<String> entities) {
        return MAPPING.matcher(controller.code()).results()
            .flatMap(mapping -> signature(controller.code(), mapping).stream())
            .filter(signature -> mentions(signature.text(), entities))
            .map(
                signature -> offence(
                    controller, signature.at(),
                    "an entity returned from a controller republishes the schema as the API, so a column"
                        + " added or renamed in the database changes the response, and a lazy association"
                        + " serialises or throws depending on where the transaction ended"
                )
            );
    }

    /*
     * The text between the mapping annotation and the parameter list, which holds the modifiers, the
     * return type and the method name. An entity named anywhere in it is an entity being returned.
     */
    private static Optional<Signature> signature(String code, MatchResult mapping) {
        int opening = code.indexOf(OPENING, mapping.end());
        return opening < 0
            ? Optional.empty()
            : Optional.of(new Signature(code.substring(mapping.end(), opening), mapping.start()));
    }

    private static boolean mentions(String span, Collection<String> entities) {
        return entities.stream()
            .anyMatch(entity -> Pattern.compile("\\b" + Pattern.quote(entity) + "\\b").matcher(span).find());
    }

    private static String offence(SpringTypes.Declared source, int at, String consequence) {
        return source.source() + ": line " + JavaCode.lineOf(source.text(), at) + ": " + consequence;
    }

    /**
     * The declaration text between a mapping annotation and the parameter list it introduces.
     *
     * @param text the modifiers, the return type and the method name
     * @param at   the offset the mapping annotation opens at, which the offence is reported against
     */
    private record Signature(String text, int at) {
    }
}
