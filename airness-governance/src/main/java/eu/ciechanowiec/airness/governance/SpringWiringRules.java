package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reports the wiring a module asks the container for and then does not get.
 *
 * <p>Each rule here reads a declaration in one file against a use in another, which is the shape
 * {@link SpringModuleRules} states for the web layer and this states for the container. The two halves
 * are always innocent apart: a class annotated {@code @Service} is an ordinary service, a {@code new} is
 * an ordinary construction, and only the pair says that the annotation was ignored.
 *
 * <p>Two of these read {@link SpringTypes.Declared#quoted()} rather than the blanked code, because the
 * defect turns on a value. A scope is a word inside a literal, and blanking the literal takes the very
 * thing that tells a prototype from a singleton.
 */
@UtilityClass
final class SpringWiringRules {

    private static final Pattern BOUND = Pattern.compile("@ConfigurationProperties\\b");
    private static final Pattern SCANNED = Pattern.compile("@ConfigurationPropertiesScan\\b");
    private static final Pattern ENABLED_PROPERTIES
        = Pattern.compile("@EnableConfigurationProperties\\s*\\(([^)]*)\\)");
    private static final Pattern LITERAL = Pattern.compile("(\\w+)\\s*\\.\\s*class");
    private static final Pattern STEREOTYPE = Pattern.compile("@(?:Service|Component|Repository)\\b");
    private static final Pattern SINGLETON = Pattern.compile(
        "@(?:Service|Component|Repository|RestController|Controller|Configuration)\\b"
    );
    private static final Pattern PROTOTYPE = Pattern.compile("@Scope\\s*\\([^)]*prototype[^)]*\\)");
    private static final Pattern BUILT = Pattern.compile("\\bnew\\s+(\\w+)\\s*\\(");
    /*
     * An @Async that names nothing. The negative lookahead is what states the rule, since the blanking
     * leaves the parentheses of a named executor in place even after it has taken the name inside them.
     */
    private static final Pattern ASYNC = Pattern.compile("@Async\\b(?!\\s*\\()");
    private static final Pattern EXECUTOR_BEAN = Pattern.compile("@Bean\\b[^;{]*\\b\\w*Executor\\b");
    private static final Pattern ASYNC_CONFIGURER = Pattern.compile("\\bAsyncConfigurer\\b");
    private static final Pattern GUARDED = Pattern.compile(
        "@(?:PreAuthorize|PostAuthorize|PreFilter|PostFilter|Secured|RolesAllowed)\\b"
    );
    private static final Pattern ENABLED = Pattern.compile("@Enable(?:Global)?MethodSecurity\\b");

    /**
     * Every place a component of this module is built with {@code new} outside a test.
     *
     * @param types the module already read
     * @return one offence per construction, by source and line
     */
    static List<String> instantiatedComponents(SpringTypes types) {
        Set<String> components = types.named(STEREOTYPE);
        return components.isEmpty()
            ? List.of()
            : types.all().stream()
                .filter(SpringTypes.Declared::production)
                .flatMap(type -> instantiations(type, components))
                .toList();
    }

    private static Stream<String> instantiations(SpringTypes.Declared source, Collection<String> components) {
        return BUILT.matcher(source.code()).results()
            .filter(built -> components.contains(built.group(1)))
            .map(
                built -> offence(
                    source, built.start(),
                    "a component built with new is a plain object the container never saw, so its"
                        + " collaborators are null, the proxies that carry @Transactional and @Cacheable"
                        + " are absent, and every annotation the class declares does nothing"
                )
            );
    }

    /**
     * Every constructor of a singleton that takes a prototype-scoped bean directly.
     *
     * @param types the module already read
     * @return one offence per constructor, by source and line
     */
    static List<String> prototypesWithoutProviders(SpringTypes types) {
        Set<String> prototypes = scoped(types);
        return prototypes.isEmpty()
            ? List.of()
            : types.carrying(SINGLETON).stream()
                .filter(type -> !PROTOTYPE.matcher(type.quoted()).find())
                .flatMap(singleton -> injections(singleton, prototypes))
                .toList();
    }

    private static Set<String> scoped(SpringTypes types) {
        return types.all().stream()
            .filter(type -> PROTOTYPE.matcher(type.quoted()).find())
            .map(SpringTypes.Declared::name)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /*
     * The parameter list allows one level of nesting, because a parameter may carry an annotation that
     * takes arguments of its own. Stopping at the first closing bracket instead would cut the list short
     * at a @Value and pass over every parameter written after it, which is a miss rather than a report
     * and so would go unnoticed.
     */
    private static Stream<String> injections(SpringTypes.Declared singleton, Collection<String> prototypes) {
        Pattern constructor = Pattern.compile(
            "\\b" + Pattern.quote(singleton.name()) + "\\s*\\(((?:[^()]|\\([^()]*\\))*)\\)"
        );
        return constructor.matcher(singleton.code()).results()
            .filter(declared -> injects(declared.group(1), prototypes))
            .map(
                declared -> offence(
                    singleton, declared.start(),
                    "a prototype bean injected into a singleton is resolved once, while the singleton is"
                        + " being built, so the one instance it receives is held for the life of the"
                        + " application and the scope the bean asks for is never honoured again"
                )
            );
    }

    /*
     * A parameter list naming a prototype type directly. A provider around it reads as "Kind<Proto>",
     * where the name is followed by the closing angle bracket rather than by the parameter it declares,
     * so the shape that answers this rule is the shape this pattern cannot match.
     */
    private static boolean injects(String parameters, Collection<String> prototypes) {
        return prototypes.stream()
            .anyMatch(
                prototype -> Pattern.compile("\\b" + Pattern.quote(prototype) + "\\b\\s+\\w")
                    .matcher(parameters)
                    .find()
            );
    }

    /**
     * Every configuration property type that nothing in the module registers as a bean.
     *
     * @param types the module already read
     * @return one offence per unregistered type, by source and line
     */
    static List<String> unregisteredProperties(SpringTypes types) {
        if (!types.carrying(SCANNED).isEmpty()) {
            return List.of();
        }
        Set<String> registered = registered(types);
        return types.carrying(BOUND).stream()
            .filter(type -> !registered.contains(type.name()))
            .flatMap(SpringWiringRules::unregistered)
            .toList();
    }

    /*
     * The names a module hands to @EnableConfigurationProperties, taken from the class literals inside
     * it. A module that declares @ConfigurationPropertiesScan instead has answered for all of them at
     * once, which is why the caller leaves before reaching here.
     */
    private static Set<String> registered(SpringTypes types) {
        return types.all().stream()
            .flatMap(type -> ENABLED_PROPERTIES.matcher(type.code()).results())
            .flatMap(enabled -> LITERAL.matcher(enabled.group(1)).results())
            .map(literal -> literal.group(1))
            .collect(Collectors.toSet());
    }

    private static Stream<String> unregistered(SpringTypes.Declared type) {
        return BOUND.matcher(type.code()).results()
            .limit(1)
            .map(
                bound -> offence(
                    type, bound.start(),
                    "@ConfigurationProperties builds no bean on its own, and this module neither declares"
                        + " @ConfigurationPropertiesScan nor names this type in @EnableConfigurationProperties,"
                        + " so nothing binds it and every field keeps the default the settings were written"
                        + " to replace"
                )
            );
    }

    /**
     * Every method security annotation in a module that enables no method security.
     *
     * @param types the module already read
     * @return one offence per source carrying such an annotation, by source and line
     */
    static List<String> unenabledMethodSecurity(SpringTypes types) {
        return types.carrying(ENABLED).isEmpty()
            ? types.all().stream().flatMap(SpringWiringRules::guards).toList()
            : List.of();
    }

    /*
     * One offence per source rather than one per annotation. The module is missing a single declaration,
     * so naming every guarded method would print one defect as many times as the module guards methods.
     */
    private static Stream<String> guards(SpringTypes.Declared type) {
        return GUARDED.matcher(type.code()).results()
            .limit(1)
            .map(
                guard -> offence(
                    type, guard.start(),
                    "a method security annotation is read by nothing until @EnableMethodSecurity is"
                        + " declared somewhere in the module, so every method it guards runs unguarded"
                        + " while the source reads as though the guard were in force"
                )
            );
    }

    /**
     * Every unnamed {@code @Async} in a module that declares no executor to run it on.
     *
     * @param types the module already read
     * @return one offence per annotation, by source and line
     */
    static List<String> unnamedAsyncExecutors(SpringTypes types) {
        return pooled(types)
            ? List.of()
            : types.all().stream().flatMap(SpringWiringRules::asyncs).toList();
    }

    private static boolean pooled(SpringTypes types) {
        return types.all().stream().map(SpringTypes.Declared::code).anyMatch(SpringWiringRules::declaresAPool);
    }

    private static boolean declaresAPool(String code) {
        return EXECUTOR_BEAN.matcher(code).find() || ASYNC_CONFIGURER.matcher(code).find();
    }

    private static Stream<String> asyncs(SpringTypes.Declared type) {
        return ASYNC.matcher(type.code()).results()
            .map(
                async -> offence(
                    type, async.start(),
                    "@Async naming no executor in a module that declares none falls back to"
                        + " SimpleAsyncTaskExecutor, which pools nothing and starts one unbounded thread"
                        + " per call, so load turns into thread exhaustion rather than into a queue"
                )
            );
    }

    private static String offence(SpringTypes.Declared source, int at, String consequence) {
        return source.source() + ": line " + JavaCode.lineOf(source.text(), at) + ": " + consequence;
    }
}
