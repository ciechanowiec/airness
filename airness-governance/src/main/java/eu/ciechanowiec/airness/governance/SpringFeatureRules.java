package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Correlates a Spring feature annotation with the production configuration that activates it.
 *
 * <p>The use and its enabler ordinarily live in different modules: a library holds the scheduled or
 * guarded method and the application module decides which infrastructure runs. The complete reactor is
 * therefore the smallest scope that can answer the question without reporting a correctly configured
 * library module on its own.
 */
@UtilityClass
final class SpringFeatureRules {

    private static final Pattern ASYNC = Pattern.compile("@Async\\b");
    private static final Pattern SCHEDULED = Pattern.compile("@Scheduled\\b");
    private static final Pattern CACHE = Pattern.compile("@(?:Cacheable|CachePut|CacheEvict|Caching)\\b");
    private static final Pattern RETRY = Pattern.compile("@(?:Retryable|Recover)\\b");
    private static final Pattern PERSISTENT = Pattern.compile("@(?:Entity|MappedSuperclass)\\b");
    private static final Pattern AUDITED = Pattern.compile(
        "@(?:CreatedDate|LastModifiedDate|CreatedBy|LastModifiedBy)\\b"
    );
    private static final Pattern ENABLE_ASYNC = Pattern.compile("@EnableAsync\\b");
    private static final Pattern ENABLE_SCHEDULING = Pattern.compile("@EnableScheduling\\b");
    private static final Pattern ENABLE_CACHING = Pattern.compile("@EnableCaching\\b");
    private static final Pattern ENABLE_RETRY = Pattern.compile("@EnableRetry\\b");
    private static final Pattern ENABLE_AUDITING = Pattern.compile("@EnableJpaAuditing\\b");
    private static final Pattern PRE_POST = Pattern.compile(
        "@(?:PreAuthorize|PostAuthorize|PreFilter|PostFilter)\\b"
    );
    private static final Pattern SECURED = Pattern.compile("@Secured\\b");
    private static final Pattern JSR250 = Pattern.compile("@(?:RolesAllowed|PermitAll|DenyAll)\\b");
    private static final Pattern METHOD_SECURITY = Pattern.compile("@EnableMethodSecurity(?:\\s*\\(([^)]*)\\))?");

    static List<String> unenabledAsync(SpringTypes types) {
        return unenabled(types, ASYNC, ENABLE_ASYNC, "@EnableAsync");
    }

    static List<String> unenabledScheduling(SpringTypes types) {
        return unenabled(types, SCHEDULED, ENABLE_SCHEDULING, "@EnableScheduling");
    }

    static List<String> unenabledCaching(SpringTypes types) {
        return unenabled(types, CACHE, ENABLE_CACHING, "@EnableCaching");
    }

    static List<String> unenabledRetry(SpringTypes types) {
        return unenabled(types, RETRY, ENABLE_RETRY, "@EnableRetry");
    }

    static List<String> unenabledAuditing(SpringTypes types) {
        Predicate<SpringTypes.Declared> usage = type -> PERSISTENT.matcher(type.code()).find()
            && AUDITED.matcher(type.code()).find();
        return enabled(types, ENABLE_AUDITING)
            ? List.of()
            : production(types).filter(usage).flatMap(type -> offence(type, AUDITED, "@EnableJpaAuditing")).toList();
    }

    static List<String> disabledMethodSecurity(SpringTypes types) {
        List<String> enablers = production(types)
            .flatMap(type -> METHOD_SECURITY.matcher(type.code()).results())
            .map(SpringFeatureRules::arguments)
            .toList();
        return Stream.of(
            family(types, PRE_POST, enablers, "prePostEnabled"),
            family(types, SECURED, enablers, "securedEnabled"),
            family(types, JSR250, enablers, "jsr250Enabled")
        )
            .flatMap(List::stream)
            .toList();
    }

    private static List<String> family(
        SpringTypes types, Pattern annotations, List<String> enablers, String flag
    ) {
        boolean active = enablers.stream().anyMatch(arguments -> explicit(arguments, flag));
        return active
            ? List.of()
            : production(types)
                .flatMap(
                    type -> annotations.matcher(type.code()).results().limit(1).map(
                        annotation -> offence(
                            type, annotation.start(),
                            "the method-security family is inactive until production configuration declares"
                                + " @EnableMethodSecurity(" + flag + " = true)"
                        )
                    )
                )
                .toList();
    }

    private static boolean explicit(String arguments, String flag) {
        return Pattern.compile("\\b" + Pattern.quote(flag) + "\\s*=\\s*true\\b")
            .matcher(arguments)
            .find();
    }

    private static String arguments(MatchResult enabler) {
        return Optional.ofNullable(enabler.group(1)).orElse("");
    }

    private static List<String> unenabled(
        SpringTypes types, Pattern use, Pattern enabler, String required
    ) {
        return enabled(types, enabler)
            ? List.of()
            : production(types).flatMap(type -> offence(type, use, required)).toList();
    }

    private static boolean enabled(SpringTypes types, Pattern enabler) {
        return production(types).map(SpringTypes.Declared::code).anyMatch(code -> enabler.matcher(code).find());
    }

    private static Stream<SpringTypes.Declared> production(SpringTypes types) {
        return types.all().stream().filter(SpringTypes.Declared::production);
    }

    private static Stream<String> offence(
        SpringTypes.Declared type, Pattern annotation, String enabler
    ) {
        return annotation.matcher(type.code()).results().limit(1).map(
            use -> offence(
                type, use.start(),
                "the annotation is read by nothing until production configuration declares " + enabler
            )
        );
    }

    private static String offence(SpringTypes.Declared source, int at, String consequence) {
        return source.source() + ": line " + JavaCode.lineOf(source.text(), at) + ": " + consequence;
    }
}
