package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The roles a security annotation names, read against the roles the project declares.
 *
 * <p>A role inside {@code hasRole('ADMIN')} is a string the engine compares with the authorities a caller
 * holds. Nothing compiles it, and a role nobody is ever granted is not an error the engine reports: the
 * comparison is false for every caller, so the guard denies everyone, or admits everyone when it is
 * written around a negation. Both compile, both pass review, and neither is a line in any log.
 *
 * <p>The declaration the string is read against is an enum implementing {@code GrantedAuthority}, which
 * is the ordinary way a project states its roles once and derives every authority from them. A literal
 * matches a constant of that enum with or without the {@code ROLE_} prefix, since the engine accepts
 * either spelling. A project naming a role in an annotation while declaring no such enum is refused
 * rather than passed over: a security rule that quietly checks nothing is worse than one that asks
 * for the declaration it needs, and the declaration costs one clause on an enum the project holds.
 *
 * <p>Test sources are read for the literals they name and never for the set they are read against,
 * because a test that grants a role the application never grants proves something about a caller that
 * does not exist.
 */
@UtilityClass
final class SpringRoleRules {

    private static final Pattern ROLE_ENUM = Pattern.compile(
        "\\benum\\s+(\\w+)\\s+implements\\b[^{]*\\bGrantedAuthority\\b[^{]*\\{"
    );
    private static final Pattern EXPRESSION = Pattern.compile(
        "@(?:PreAuthorize|PostAuthorize|PreFilter|PostFilter)\\s*\\("
    );
    private static final Pattern LISTED = Pattern.compile("@(?:Secured|RolesAllowed)\\s*\\(");
    private static final Pattern MOCKED = Pattern.compile("@WithMockUser\\s*\\(");
    private static final Pattern CALL = Pattern.compile(
        "\\b(?:hasRole|hasAnyRole|hasAuthority|hasAnyAuthority)\\s*\\(([^)]*)\\)"
    );
    private static final Pattern SINGLE_QUOTED = Pattern.compile("'([^']*)'");
    private static final Pattern DOUBLE_QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern GRANTED = Pattern.compile(
        "\\b(?:roles|authorities)\\s*=\\s*(\\{[^}]*}|\"[^\"]*\")"
    );
    private static final Pattern GROUP = Pattern.compile("[({][^(){}]*[)}]");
    private static final Pattern ANNOTATION = Pattern.compile("@\\w+");
    private static final String PREFIX = "ROLE_";

    /**
     * Every role a security annotation names that no declared role enum holds.
     *
     * @param types every source of the build, already read
     * @return one offence per undeclared role, by source and line
     */
    static List<String> undeclaredRoles(SpringTypes types) {
        List<RoleSet> declared = types.all().stream()
            .filter(SpringTypes.Declared::production)
            .flatMap(type -> roleSets(type).stream())
            .toList();
        Set<String> constants = declared.stream()
            .flatMap(set -> set.constants().stream())
            .collect(Collectors.toUnmodifiableSet());
        String enums = declared.stream().map(RoleSet::name).collect(Collectors.joining(", "));
        return types.all().stream()
            .flatMap(type -> named(type).stream())
            .filter(role -> !matches(role.role(), constants))
            .map(role -> offence(role, declared.isEmpty(), enums))
            .toList();
    }

    private static boolean matches(String role, Set<String> constants) {
        boolean plain = constants.contains(role);
        boolean prefixed = role.startsWith(PREFIX) && constants.contains(role.substring(PREFIX.length()));
        return plain || prefixed;
    }

    private static List<RoleSet> roleSets(SpringTypes.Declared type) {
        String code = type.code();
        return ROLE_ENUM.matcher(code).results()
            .map(found -> new RoleSet(found.group(1), constants(code, found.end() - 1)))
            .toList();
    }

    /*
     * The constants are the identifiers written at the top of the body up to its first semicolon, each
     * possibly followed by an argument list or a body of its own, which is taken out before the names
     * are read so an argument written as a constant is not counted as one.
     */
    private static Set<String> constants(String code, int opening) {
        int closing = SpringMembers.matching(code, opening);
        String body = code.substring(opening + 1, closing);
        String flattened = body;
        while (GROUP.matcher(flattened).find()) {
            flattened = GROUP.matcher(flattened).replaceAll(" ");
        }
        int end = flattened.indexOf(';');
        String listed = end < 0 ? flattened : flattened.substring(0, end);
        return Stream.of(ANNOTATION.matcher(listed).replaceAll(" ").split(","))
            .map(String::strip)
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static List<Named> named(SpringTypes.Declared type) {
        String code = type.code();
        String read = type.quoted();
        Stream<Named> expressed = arguments(code, read, EXPRESSION).stream().flatMap(
            argument -> CALL.matcher(argument.text()).results().flatMap(
                call -> literals(type, argument.at() + call.start(1), call.group(1), SINGLE_QUOTED)
            )
        );
        Stream<Named> listed = arguments(code, read, LISTED).stream().flatMap(
            argument -> literals(type, argument.at(), argument.text(), DOUBLE_QUOTED)
        );
        Stream<Named> granted = arguments(code, read, MOCKED).stream().flatMap(
            argument -> GRANTED.matcher(argument.text()).results().flatMap(
                member -> literals(type, argument.at() + member.start(1), member.group(1), DOUBLE_QUOTED)
            )
        );
        return Stream.of(expressed, listed, granted).flatMap(stream -> stream).toList();
    }

    private static List<Argument> arguments(String code, String read, Pattern annotation) {
        return annotation.matcher(code).results()
            .map(found -> argument(code, read, found))
            .toList();
    }

    private static Argument argument(String code, String read, MatchResult found) {
        int opens = found.end() - 1;
        int closes = SpringMembers.closing(code, opens);
        return new Argument(opens + 1, read.substring(opens + 1, closes));
    }

    private static Stream<Named> literals(SpringTypes.Declared type, int at, String text, Pattern quoted) {
        return quoted.matcher(text).results()
            .map(literal -> new Named(type, at + literal.start(1), literal.group(1)));
    }

    private static String offence(Named named, boolean undeclared, String enums) {
        String reason = undeclared
            ? "no production enum implements GrantedAuthority, so the build holds no role set to read"
                + " it against; declare the roles as an enum implementing GrantedAuthority"
            : "no constant of " + enums + " declares it, so no caller is ever granted it and the guard"
                + " decides against every caller without a line in any log";
        return named.type().source() + ": line " + JavaCode.lineOf(named.type().text(), named.at())
            + ": the security annotation names the role '" + named.role() + "', and " + reason;
    }

    private record RoleSet(String name, Set<String> constants) {
    }

    private record Argument(int at, String text) {
    }

    private record Named(SpringTypes.Declared type, int at, String role) {
    }
}
