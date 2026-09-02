package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reports Spring Framework bean registrars that the container cannot register as written.
 *
 * <p>A {@code BeanRegistrar} is discovered only through {@code @Import}. Making it a component does not
 * turn it into a registrar, while declaring it without an import leaves its callback unused. Those are
 * two states of one registration contract, so the invalid component form takes precedence and the
 * missing-import rule reports only after no invalid stereotype remains.
 */
@UtilityClass
final class SpringRegistrarRules {

    private static final String QUALIFIED = "org.springframework.beans.factory.BeanRegistrar";
    private static final Pattern REGISTRAR = Pattern.compile(
        "\\bimplements\\b[^{]*\\b(?:" + QUALIFIED.replace(".", "\\.") + "|BeanRegistrar)\\b"
    );
    private static final Pattern IMPORTED_TYPE = Pattern.compile(
        "\\bimport\\s+org\\.springframework\\.beans\\.factory\\.BeanRegistrar\\s*;"
    );
    private static final Pattern COMPONENT = Pattern.compile(
        "@(?:Component|Service|Repository|Controller|RestController|Configuration|SpringBootApplication)\\b"
    );
    private static final Pattern CONFIGURATION = Pattern.compile(
        "@(?:Configuration|SpringBootApplication)\\b"
    );

    /**
     * Registrar types incorrectly offered to component scanning.
     *
     * @param types the module already read
     * @return one offence per invalid registrar declaration
     */
    static List<String> componentRegistrars(SpringTypes types) {
        return registrars(types).flatMap(SpringRegistrarRules::component).toList();
    }

    /**
     * Registrar types no configuration imports.
     *
     * @param types the module already read
     * @return one offence per otherwise valid unimported registrar
     */
    static List<String> unimportedRegistrars(SpringTypes types) {
        List<SpringTypes.Declared> declared = types.all();
        return registrars(types)
            .filter(type -> !COMPONENT.matcher(type.code()).find())
            .filter(type -> !imported(type, declared))
            .flatMap(SpringRegistrarRules::unimported)
            .toList();
    }

    private static Stream<SpringTypes.Declared> registrars(SpringTypes types) {
        return types.all().stream().filter(SpringRegistrarRules::registrar);
    }

    private static boolean registrar(SpringTypes.Declared type) {
        return REGISTRAR.matcher(type.code()).find()
            && (type.code().contains(QUALIFIED) || IMPORTED_TYPE.matcher(type.code()).find());
    }

    private static Stream<String> component(SpringTypes.Declared type) {
        return COMPONENT.matcher(type.code()).results()
            .limit(1)
            .map(
                marker -> offence(
                    type, marker,
                    "a BeanRegistrar cannot be registered through component scanning; remove the"
                        + " component stereotype and import the registrar from a configuration class"
                )
            );
    }

    private static boolean imported(
        SpringTypes.Declared registrar, List<SpringTypes.Declared> declared
    ) {
        Pattern reference = Pattern.compile(
            "(?s)@Import\\s*\\([^)]*\\b(?:[\\w.]+\\.)?" + Pattern.quote(registrar.name())
                + "\\s*\\.\\s*class\\b[^)]*\\)"
        );
        return declared.stream()
            .map(type -> Map.entry(type, type.code()))
            .filter(entry -> entry.getKey().production() == registrar.production())
            .filter(entry -> CONFIGURATION.matcher(entry.getValue()).find())
            .anyMatch(entry -> reference.matcher(entry.getValue()).find());
    }

    private static Stream<String> unimported(SpringTypes.Declared type) {
        return REGISTRAR.matcher(type.code()).results()
            .limit(1)
            .map(
                marker -> offence(
                    type, marker,
                    "this BeanRegistrar is imported by no configuration in its source role, so Spring"
                        + " never invokes it; add the registrar class to @Import"
                )
            );
    }

    private static String offence(
        SpringTypes.Declared source, MatchResult marker, String consequence
    ) {
        return source.source() + ": line " + JavaCode.lineOf(source.text(), marker.start()) + ": "
            + consequence;
    }
}
