package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads the Java sources of one module for the Spring constructs that fail without saying so.
 *
 * <p>Every rule here needs something no analyzer configuration carries. One compares a package against
 * the root the project declared, which is a value the build supplies rather than one the source states.
 * One correlates a call with the declarations beside it, which the XPath the rule set is written in
 * cannot express. And one reads a call written as text in one file against a declaration in another,
 * which nothing reading a single file can answer at all. So they live here, where a check reads a file
 * and answers about the whole of it.
 *
 * <p>The goal that runs this is bound by {@code airness-parent-spring-boot} alone, so a project that is
 * not a Spring Boot one never asks either question.
 */
public final class SpringSourceCheck {

    private static final String ENTRY_POINT
        = "Spring application classes outside the declared package root";
    private static final String BEAN_CALLS
        = "Bean methods calling another bean method of the same class";
    private static final String SELF_INVOCATION
        = "Calls reaching a proxied method from inside the bean that declares it";
    private static final String CONSTRUCTOR_CALLS
        = "Calls reaching a proxied method while the bean is still being built";
    private static final String STATIC_HOLDERS
        = "Beans assigning a static field of their own";
    private static final String IDENTITY_EQUALITY
        = "Entities deciding equality by a generated identifier";
    private static final String ECHOED_EXCEPTIONS
        = "Exception handlers copying the exception into the response";
    private static final String UNTIMED_CLIENTS
        = "HTTP clients built with no connect or read timeout";
    private static final String OPEN_CHAINS
        = "Filter chains naming no terminal request matcher";
    private static final String CORS_CREDENTIALS
        = "Credentialed requests accepted from a wildcard origin";
    private static final String REPLACED_DATABASES
        = "Persistence tests run against a database the application never uses";
    private static final String QUERY_CONSTRUCTORS
        = "Queries constructing a record the module declares with the wrong number of arguments";
    private static final String UNNAMED_GUARD_PARAMETERS
        = "Security expressions reading a parameter the runtime cannot name";

    private final Path root;
    private final List<Path> sources;
    private final String packageRoot;

    /**
     * Creates a check over the Java sources one module holds.
     *
     * @param root        repository root the offences are reported relative to
     * @param sourceRoots source directories of the module
     * @param packageRoot the package every class of the project lives under
     */
    public SpringSourceCheck(Path root, Collection<Path> sourceRoots, String packageRoot) {
        this.root = root;
        this.sources = JavaSources.under(root, sourceRoots);
        this.packageRoot = packageRoot;
    }

    /**
     * How many sources the check read, which the goal refuses a zero of.
     *
     * @return the number of Java sources in scope
     */
    public int scanned() {
        return this.sources.size();
    }

    /**
     * Every Spring rule and the sources that break it.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(ENTRY_POINT, this.offences(this::entryPoint)),
            new Findings(BEAN_CALLS, this.offences(SpringSourceRules::calledBeanMethods)),
            new Findings(SELF_INVOCATION, this.offences(SpringProxyRules::selfInvocations)),
            new Findings(CONSTRUCTOR_CALLS, this.offences(SpringProxyRules::constructorInvocations)),
            new Findings(STATIC_HOLDERS, this.offences(SpringBodyRules::staticBeanHolders)),
            new Findings(IDENTITY_EQUALITY, this.offences(SpringBodyRules::generatedIdentityEquality)),
            new Findings(ECHOED_EXCEPTIONS, this.offences(SpringBodyRules::echoedExceptions)),
            new Findings(UNTIMED_CLIENTS, this.offences(SpringFileRules::untimedClients)),
            new Findings(OPEN_CHAINS, this.offences(SpringFileRules::openFilterChains)),
            new Findings(CORS_CREDENTIALS, this.offences(SpringFileRules::unscopedCorsCredentials)),
            new Findings(REPLACED_DATABASES, this.offences(SpringFileRules::replacedTestDatabases)),
            new Findings(QUERY_CONSTRUCTORS, this.queryConstructors()),
            new Findings(
                UNNAMED_GUARD_PARAMETERS, this.offences(SpringSecurityRules::unnamedSecurityParameters)
            )
        );
    }

    // The only rule here that reads the module rather than a file. What a query constructs is declared
    // somewhere else, so the sources are read once into the types they declare before any of them is
    // judged, which is the shape a cross-file question takes throughout this package.
    private List<String> queryConstructors() {
        SpringTypes types = SpringTypes.over(this.root, this.sources);
        Map<String, Integer> records = SpringQueryRules.records(types.all());
        return types.all().stream()
            .flatMap(declared -> named(declared, SpringQueryRules.mismatchedConstructors(declared, records)))
            .toList();
    }

    private static Stream<String> named(SpringTypes.Declared declared, Collection<String> offences) {
        return offences.stream().map(offence -> "%s: %s".formatted(declared.source(), offence));
    }

    private List<String> entryPoint(CharSequence source) {
        return SpringSourceRules.misplacedEntryPoint(source, this.packageRoot);
    }

    private List<String> offences(SpringRule rule) {
        return this.sources.stream()
            .flatMap(
                source -> Repository.readText(source).stream()
                    .flatMap(text -> rule.offences(text).stream())
                    .map(offence -> "%s: %s".formatted(this.root.relativize(source), offence))
            )
            .toList();
    }

    /**
     * One rule read over the text of one source.
     */
    @FunctionalInterface
    private interface SpringRule {

        List<String> offences(CharSequence source);
    }
}
