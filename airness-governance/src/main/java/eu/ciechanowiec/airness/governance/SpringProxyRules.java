package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Reports the calls that reach a proxied method without passing the proxy.
 *
 * <p>Spring advises a bean by wrapping it, and the wrapper only sees a call that arrives from outside
 * the object. A call made from one method of the bean to another goes straight to the method, so the
 * transaction does not open, the retry does not arm, the cache is not consulted, and the security
 * annotation authorizes nobody. Nothing raises, because from the container's side nothing went wrong.
 *
 * <p>A call from a constructor is the same defect one step earlier. The proxy does not exist until the
 * bean is fully built, so a call made while it is being built runs raw whatever the annotation says.
 *
 * <p>A qualified call is left alone unless it is qualified by {@code this}. Reaching the same method on
 * another bean is how the defect is repaired, so reporting it would report the fix.
 */
@UtilityClass
final class SpringProxyRules {

    private static final Pattern PROXIED = Pattern.compile(
        "@(?:Transactional|Async|Cacheable|CachePut|CacheEvict|Retryable|Validated|PreAuthorize"
            + "|PostAuthorize|Secured)\\b"
    );
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|record|enum)\\s+(\\w+)");
    private static final String DOT = ".";
    private static final String THIS = "this";
    private static final String NEW = "new";

    /**
     * Every call from elsewhere in the type to a method the container proxies.
     *
     * @param source the Java source to read
     * @return one offence per call, in the order they are written
     */
    static List<String> selfInvocations(CharSequence source) {
        String code = JavaCode.blanked(source);
        return calls(code, SpringMembers.annotated(code, PROXIED)).stream()
            .map(
                call -> offence(
                    source, call, "never passes the proxy, so the annotation on it is not honoured"
                )
            )
            .toList();
    }

    /**
     * Every call to a proxied method made while the bean is still being constructed.
     *
     * @param source the Java source to read
     * @return one offence per call, in the order they are written
     */
    static List<String> constructorInvocations(CharSequence source) {
        String code = JavaCode.blanked(source);
        List<SpringMembers.Member> constructors = constructors(code);
        return calls(code, SpringMembers.annotated(code, PROXIED)).stream()
            .filter(call -> encloses(constructors, call))
            .map(
                call -> offence(
                    source, call, "runs before the proxy exists, so the annotation on it is not honoured"
                )
            )
            .toList();
    }

    private static List<MatchResult> calls(String code, List<SpringMembers.Member> proxied) {
        List<String> names = proxied.stream().map(SpringMembers.Member::name).toList();
        Set<Integer> declarations = proxied.stream()
            .map(SpringMembers.Member::declaration)
            .collect(Collectors.toUnmodifiableSet());
        return SpringMembers.callsWithin(code, 0, code.length(), names).stream()
            .filter(call -> !declarations.contains(call.start(1)))
            .filter(call -> reachesThisObject(code, call.start(1)))
            .toList();
    }

    // The marker has to be zero width. SpringMembers reads the declaration that follows what it matched,
    // so a marker consuming the constructor name would leave it reading the first parameter type instead.
    private static List<SpringMembers.Member> constructors(String code) {
        Matcher declared = TYPE.matcher(code);
        if (!declared.find()) {
            return List.of();
        }
        Pattern marker = Pattern.compile("(?=\\b" + Pattern.quote(declared.group(1)) + "\\s*\\()");
        return SpringMembers.annotated(code, marker).stream()
            .filter(member -> !NEW.equals(wordBefore(code, member.declaration())))
            .toList();
    }

    private static boolean encloses(List<SpringMembers.Member> members, MatchResult call) {
        return members.stream()
            .anyMatch(member -> call.start() >= member.start() && call.end() <= member.end());
    }

    // A call written bare, or written on this, is a call that never leaves the object. A call qualified by
    // anything else reaches another bean, which is the repair rather than the defect.
    private static boolean reachesThisObject(String code, int at) {
        String preceding = code.substring(0, at).stripTrailing();
        return !preceding.endsWith(DOT) || THIS.equals(wordBefore(code, preceding.length() - 1));
    }

    // The identifier immediately before an offset, ignoring whitespace, or an empty string when the text
    // there is not an identifier.
    private static String wordBefore(String code, int at) {
        int end = at;
        while (end > 0 && Character.isWhitespace(code.charAt(end - 1))) {
            end -= 1;
        }
        int start = end;
        while (start > 0 && Character.isJavaIdentifierPart(code.charAt(start - 1))) {
            start -= 1;
        }
        return code.substring(start, end);
    }

    private static String offence(CharSequence source, MatchResult call, String consequence) {
        return "line " + JavaCode.lineOf(source, call.start(1)) + ": the call to " + call.group(1)
            + " " + consequence;
    }
}
