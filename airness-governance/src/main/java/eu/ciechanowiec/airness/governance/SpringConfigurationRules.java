package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reports the runtime settings that are wrong, and the ones whose absence is wrong.
 *
 * <p>Two shapes of rule sit here. One reads a value the project wrote and objects to it, which needs no
 * qualification: an actuator exposed at a wildcard is wrong wherever it appears. The other objects to a
 * key that is missing, and that one is gated on the subsystem it belongs to, because a project running
 * no database is not answerable for how it would have pooled connections.
 *
 * <p>The gate reads the configuration rather than the classpath. A file that configures nothing under a
 * prefix has not taken on the obligations of that prefix, and an application that configures nothing at
 * all is left to the defaults the analyzer rules already argue about elsewhere.
 */
@UtilityClass
final class SpringConfigurationRules {

    private static final String NEVER = "never";
    private static final Pattern PLACEHOLDER = Pattern.compile("^\"?\\$\\{.*}\"?$");
    private static final Pattern SENSITIVE = Pattern.compile(
        "(?:password|secret|token|credential|privatekey|apikey|accesskey)$"
    );
    private static final Pattern KEBAB = Pattern.compile("[A-Z_]");

    private static final List<Rule> WRONG_VALUE = List.of(
        new Rule(
            "management.endpoints.web.exposure.include", SpringConfigurationRules::wildcard,
            "the actuator is exposed at a wildcard, which publishes env, beans, configprops and mappings;"
                + " name the endpoints this application serves"
        ),
        new Rule(
            "management.endpoint.health.show-details", "always"::equals,
            "the health payload names every component and often its host to an unauthenticated caller;"
                + " use when-authorized"
        ),
        new Rule(
            "server.error.include-stacktrace", value -> !NEVER.equals(value),
            "an unhandled error returns its stack trace to the caller; set never"
        ),
        new Rule(
            "server.error.include-message", value -> !NEVER.equals(value),
            "an unhandled error returns its internal message to the caller; set never"
        ),
        new Rule(
            "spring.main.allow-circular-references", "true"::equals,
            "circular references are refused by default because they are a design defect, and this"
                + " restores a bean holding a half-built proxy of another"
        ),
        new Rule(
            "spring.main.allow-bean-definition-overriding", "true"::equals,
            "two definitions of one name then resolve by scan order, so which implementation runs is not"
                + " something the source states"
        ),
        new Rule(
            "spring.sql.init.mode", "always"::equals,
            "schema.sql and data.sql then run on every startup, outside the migration tool and with no"
                + " record of what was applied, which reopens exactly the schema ownership that ddl-auto"
                + " and the migration-tool rule settle"
        ),
        new Rule(
            "spring.h2.console.enabled", "true"::equals,
            "this publishes a database console, and the security exemption written to make it work"
                + " usually outlives it"
        ),
        new Rule(
            "spring.jpa.show-sql", "true"::equals,
            "every statement is written to standard output on a hot path, which costs more than the query"
        ),
        new Rule(
            "spring.profiles.active", value -> !value.isEmpty(),
            "the artifact chooses its own environment here, so the same build cannot be promoted between"
                + " them; pass the profile at run time"
        )
    );

    private static final List<Required> REQUIRED = List.of(
        new Required(
            "spring.jpa", "spring.jpa.hibernate.ddl-auto",
            value -> "validate".equals(value) || "none".equals(value),
            "Hibernate may alter the schema on startup; set validate and let a migration tool own it"
        ),
        new Required(
            "spring.jpa", "spring.jpa.open-in-view", "false"::equals,
            "a database connection is held for the whole request including response writing, and every"
                + " lazy association loads wherever it is first touched"
        ),
        new Required(
            "spring.datasource.url", "spring.datasource.hikari.max-lifetime", _ -> true,
            "a connection killed by the database or a proxy stays in the pool and fails on its next use"
        ),
        new Required(
            "spring.datasource.url", "spring.datasource.hikari.leak-detection-threshold", _ -> true,
            "a leaked connection is never reported, so the pool drains with nothing saying why"
        ),
        new Required(
            "spring.datasource.url", "spring.datasource.hikari.maximum-pool-size", _ -> true,
            "the pool size decides how many requests are served at once and how much load the database is"
                + " asked to carry, and the default of ten settles both without anybody choosing either"
        ),
        new Required(
            "spring.datasource.url", "spring.datasource.hikari.connection-timeout", _ -> true,
            "a caller waiting for a connection waits the default thirty seconds before it is told it"
                + " cannot have one, so an exhausted pool arrives as latency rather than as an error"
        ),
        new Required(
            "server", "server.shutdown", "graceful"::equals,
            "an in-flight request is cut at the socket on every deploy, so a rolling restart drops traffic"
        )
    );

    /**
     * Every offence one configuration file carries.
     *
     * @param path          the repository-relative path, which every offence names
     * @param configuration the file already read
     * @return the offences, in the order the rules are declared
     */
    static List<String> offences(String path, SpringConfiguration configuration) {
        return Stream.of(
            WRONG_VALUE.stream().flatMap(rule -> wrongValue(path, configuration, rule)),
            REQUIRED.stream().flatMap(rule -> missing(path, configuration, rule)),
            configuration.settings().stream().flatMap(setting -> hygiene(path, setting))
        ).flatMap(stream -> stream).toList();
    }

    private static Stream<String> wrongValue(String path, SpringConfiguration read, Rule rule) {
        return read.declared(rule.key()).stream()
            .filter(setting -> rule.wrong().test(unquoted(setting.value())))
            .map(setting -> offence(path, setting.line(), rule.key() + ": " + rule.consequence()));
    }

    private static Stream<String> missing(String path, SpringConfiguration read, Required rule) {
        if (!read.configures(rule.subsystem())) {
            return Stream.empty();
        }
        Optional<SpringConfiguration.Setting> declared = read.declared(rule.key());
        boolean satisfied = declared.isPresent()
            && rule.right().test(unquoted(declared.get().value()));
        return satisfied
            ? Stream.empty()
            : Stream.of(
                offence(
                    path, declared.map(SpringConfiguration.Setting::line).orElse(1),
                    rule.key() + " is not set as it has to be: " + rule.consequence()
                )
            );
    }

    private static Stream<String> hygiene(String path, SpringConfiguration.Setting setting) {
        return Stream.concat(secret(path, setting), spelling(path, setting));
    }

    private static Stream<String> secret(String path, SpringConfiguration.Setting setting) {
        boolean literal = SENSITIVE.matcher(setting.key()).find()
            && !setting.value().isEmpty()
            && !PLACEHOLDER.matcher(setting.value()).matches();
        return literal
            ? Stream.of(
                offence(
                    path, setting.line(), setting.raw()
                        + " carries a literal secret, which ships inside the artifact; read it from the"
                        + " environment through a placeholder"
                )
            )
            : Stream.empty();
    }

    private static Stream<String> spelling(String path, SpringConfiguration.Setting setting) {
        return KEBAB.matcher(setting.raw()).find()
            ? Stream.of(
                offence(
                    path, setting.line(), setting.raw()
                        + " is not written in kebab-case, and Spring accepts every spelling, so two spellings"
                        + " of one key can sit here with only one of them being read"
                )
            )
            : Stream.empty();
    }

    private static String unquoted(String value) {
        String stripped = value.replace("\"", "").replace("'", "").strip();
        return stripped.toLowerCase(Locale.ROOT);
    }

    private static boolean wildcard(String value) {
        return value.contains("*");
    }

    private static String offence(String path, int line, String consequence) {
        return path + ": line " + line + ": " + consequence;
    }

    private record Rule(String key, Predicate<String> wrong, String consequence) {
    }

    private record Required(String subsystem, String key, Predicate<String> right, String consequence) {
    }
}
