package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The public methods a bean leaves unguarded while guarding the rest of them.
 *
 * <p>An authorization annotation is the whole of what decides who may call a method of the application
 * layer. A method that carries none is reached by every caller the container admits, and nothing says
 * so: it compiles, it answers, no log records a decision that was never taken, and a test calling it as
 * an administrator passes while proving nothing about who else may. The defect is written by adding a
 * method to a class whose other methods are guarded, and it looks exactly like the methods around it.
 *
 * <p>A class guarding at least one of its public methods has taken on the obligation, so every other
 * public method of it is answerable too. A class guarding none has taken on nothing and is passed over
 * entirely, which is the same reading {@link SpringConfigurationRules} makes of a file that configures
 * nothing under a prefix. That is what keeps the collaborator of a startup runner, whose caller holds no
 * role at all, from being asked for an annotation that would deny it.
 *
 * <p>The question is asked of a service, a component and a repository, and not of a controller. What
 * stands in front of a handler is the filter chain, which {@link SpringEndpointRules} reads from the
 * application once it is built, and that rule passes over a handler carrying a guard. Reading a
 * controller here as well would put the two rules to the same method and let them disagree about it.
 *
 * <p>A method the container invokes itself is passed over. A scheduled method, a lifecycle callback and
 * an event listener run on a thread that carries no authentication, so a guard on one denies every
 * invocation of it or throws for want of a principal, and the only annotation that would not is one
 * asserting nothing about a method no caller reaches. Asking for an annotation that says nothing teaches
 * whoever reads the message to write one. An override is passed over for a different reason: the guard
 * may be declared on the method it overrides, in a file this rule is not reading, and Spring resolves it
 * from there.
 *
 * <p>A guard the source spells as an annotation of its own, composed from {@code @PreAuthorize} by the
 * project, is not one of the names below, so a class written entirely in those reads as unobliged and is
 * passed over. That is a miss rather than a false report, which is the direction every unresolvable
 * reading in this package is settled in.
 */
@UtilityClass
final class SpringGuardRules {

    // The stereotypes that make a class part of the application layer. A controller is deliberately not
    // among them, because what reaches a handler is settled by the filter chain and read from the
    // running application rather than from the source.
    private static final Pattern MANAGED = Pattern.compile("@(?:Service|Component|Repository)\\b");

    /*
     * Every annotation that states an authorization decision, which is the set the endpoint evidence
     * reads plus PermitAll. The difference is deliberate: that rule asks whether a caller is stopped, so
     * an annotation admitting everyone is no guard to it, and this one asks whether anybody decided, so
     * admitting everyone is a decision like any other.
     */
    private static final Pattern GUARD = Pattern.compile(
        "@(?:PreAuthorize|PostAuthorize|Secured|RolesAllowed|DenyAll|PermitAll|PreFilter|PostFilter)\\b"
    );

    // The annotations that say the caller is the container rather than a principal.
    private static final Pattern INVOKED = Pattern.compile(
        "@(?:EventListener|TransactionalEventListener|Scheduled|PostConstruct|PreDestroy|Bean|Override)\\b"
    );

    private static final Pattern TYPE = Pattern.compile("\\b(?:class|record|enum)\\s+(\\w+)");

    /*
     * A member declared public, read from the start of its line. The formatter writes every annotation on
     * a line of its own and the modifier order puts public first, so a public member opens its line
     * wherever one is written.
     *
     * What stands between the keyword and the name may hold no semicolon, no assignment and no brace, and
     * that is what tells a method from everything else spelled like one. A field with an initialiser
     * reaches its equals sign first, a field without one reaches its semicolon, and a nested type reaches
     * its brace.
     */
    private static final Pattern PUBLIC = Pattern.compile("(?m)^[ \\t]*public\\b([^;={]*?)\\b(\\w+)\\s*\\(");

    // A record header and a method both read as a name followed by a parameter list, and the keyword in
    // front of one is what tells them apart.
    private static final Pattern DECLARES = Pattern.compile("\\b(?:class|record|enum|interface)\\b");

    private static final char OPENS = '{';

    private static final char CLOSES = '}';

    /**
     * Every public method left unguarded in a class that guards its others.
     *
     * @param source the source as written
     * @return one offence per unguarded method, by line
     */
    static List<String> partiallyGuardedClasses(CharSequence source) {
        String code = JavaCode.blanked(source);
        return body(code)
            .filter(body -> managed(code, body))
            .stream()
            .flatMap(body -> reported(source, code, body))
            .toList();
    }

    // Whether this is a class the rule asks about at all. A guard written above the body covers every
    // method beneath it, so a class guarded as a whole has discharged the obligation already and nothing
    // under it is missing one.
    private static boolean managed(String code, Body body) {
        String heading = code.substring(0, body.opens());
        return MANAGED.matcher(heading).find() && !GUARD.matcher(heading).find();
    }

    private static Stream<String> reported(CharSequence source, String code, Body body) {
        Set<Integer> guarded = marked(code, GUARD);
        Set<Integer> invoked = marked(code, INVOKED);
        List<MatchResult> methods = methods(code, body);
        return methods.stream().anyMatch(method -> guarded.contains(method.start(2)))
            ? methods.stream()
                .filter(method -> !guarded.contains(method.start(2)))
                .filter(method -> !invoked.contains(method.start(2)))
                .map(method -> offence(source, body, method))
            : Stream.of();
    }

    /**
     * Every public method the outer class declares itself.
     *
     * <p>Depth decides it rather than a list of the kinds a type may be, because one reading of the
     * braces excludes a nested class, a nested enum, a nested annotation type, a local class and an
     * anonymous one at once, and an anonymous class declares no name that any other reading could match.
     *
     * @param code the source with comments and literals blanked
     * @param body the outer type and the range its body occupies
     * @return one match per public method, whose second group is the declared name
     */
    private static List<MatchResult> methods(String code, Body body) {
        return PUBLIC.matcher(code).results()
            .filter(method -> method.start() > body.opens() && method.end() < body.closes())
            .filter(method -> depth(code, body.opens(), method.start()) == 1)
            .filter(method -> !DECLARES.matcher(method.group(1)).find())
            .filter(method -> !body.name().equals(method.group(2)))
            .toList();
    }

    // Where every declaration the given annotation marks states its name, which is the offset a public
    // method is matched against.
    private static Set<Integer> marked(String code, Pattern annotation) {
        return SpringMembers.annotated(code, annotation).stream()
            .map(SpringMembers.Member::declaration)
            .collect(Collectors.toUnmodifiableSet());
    }

    // How many braces stand open between the body and the offset. A member of this class sits at one.
    private static int depth(String code, int from, int at) {
        return code.substring(from, at).chars().map(SpringGuardRules::step).sum();
    }

    private static int step(int character) {
        int opened = character == OPENS ? 1 : 0;
        return character == CLOSES ? -1 : opened;
    }

    private static Optional<Body> body(String code) {
        return TYPE.matcher(code).results().findFirst().flatMap(type -> opened(code, type));
    }

    private static Optional<Body> opened(String code, MatchResult type) {
        int opens = code.indexOf(OPENS, type.end());
        return opens < 0
            ? Optional.empty()
            : Optional.of(new Body(type.group(1), opens, SpringMembers.matching(code, opens)));
    }

    private static String offence(CharSequence source, Body body, MatchResult method) {
        return "line " + JavaCode.lineOf(source, method.start(2)) + ": " + body.name()
            + " guards other public methods with an authorization annotation and " + method.group(2)
            + " carries none, so every role the container admits into the bean reaches it, nothing errors,"
            + " nothing logs, and a test calling it as an administrator passes while proving nothing about"
            + " who else may. Guard it the way its siblings are guarded, or say that reaching it without a"
            + " role is what was meant, with @PreAuthorize(\"permitAll()\"), which the prePostEnabled"
            + " configuration every project has honours where the JSR-250 @PermitAll is read only by a"
            + " project that enabled that family as well";
    }

    /**
     * The outer type of one source, by name and by the range its body occupies.
     *
     * @param name   the declared type name, which tells a constructor from a method
     * @param opens  the offset its body opens at
     * @param closes the offset its body closes at
     */
    private record Body(String name, int opens, int closes) {
    }
}
