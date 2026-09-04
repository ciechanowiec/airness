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
 * The redirects a handler builds out of a value the caller sent it.
 *
 * <p>A dispatch prefix hands the rest of its target to the browser as an address. Where the rest of it
 * is a value the request carried, whoever wrote the link chose where the reader goes, and the sign-in
 * they passed on the way is what makes the destination read as the next step of the flow rather than as
 * somewhere else entirely. Nothing fails: the redirect works for every honest link, and the one that
 * does not is a link the application never sees.
 *
 * <p>Only a target the value is the whole of is read. A prefix carrying a path of its own puts the value
 * into a segment beneath this application, which is a different question and not this one, so
 * {@code "redirect:%s/%s"} is passed over and {@code "redirect:%s"} is not.
 *
 * <p>A path variable is not read as a value the caller sent. Its value is a segment of a path this
 * application declared and the container matched, so a target built from one stays inside the address
 * space the mapping already states. Nor is a parameter that cannot hold an address: a converter refuses
 * anything that is not a UUID, an enum or a number before the handler is entered at all.
 *
 * <p>A value that went through a call of the project's own is passed over. What a handler validated is
 * not something one statement states, so a name handed to anything but the target itself is read as
 * having been worked out rather than written straight through. That is also the repair, and the escape a
 * handler takes deliberately by resolving the value and redirecting to what came back.
 *
 * <p>What the rule does not reach is named here rather than guessed at: a redirect written on the
 * servlet response, a location header carrying a URI, a dispatch prefix held in a constant, and a target
 * assembled across two statements. Each is the same defect and each would need a reading that guesses
 * which argument of which call is an address, so each is a miss rather than a false report.
 */
@UtilityClass
final class SpringRedirectRules {

    /*
     * A mapping written on a method. The optional group is the annotation's own opening parenthesis,
     * which is what tells an annotation carrying arguments from one written bare, so the reading can
     * step over the arguments rather than into them.
     */
    private static final Pattern MAPPING = Pattern.compile(
        "@(?:Request|Get|Post|Put|Delete|Patch)Mapping\\b(\\s*\\()?"
    );

    // Where the type is declared, which is what tells a mapping written on the class from one written on
    // a method. A class mapping states an address and answers no request itself.
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|record|enum)\\s+\\w+");

    // The annotations that bind a parameter to something the caller chose. A path variable is not among
    // them, because what it holds is a segment of a path this application declared.
    private static final Pattern BOUND = Pattern.compile(
        "@(?:RequestParam|RequestHeader|CookieValue|RequestAttribute)\\b"
    );

    // Only a parameter that could hold an address at all.
    private static final Pattern TEXTUAL = Pattern.compile("\\b(?:String|CharSequence)\\b");

    // A dispatch prefix that is the whole of its target, written bare or with one substitution and
    // nothing else, so that whatever stands beside it is the entire address.
    private static final Pattern WHOLE_TARGET = Pattern.compile("\"(?:redirect|forward):(?:%s)?\"");

    private static final Pattern VIEW = Pattern.compile("\\bnew\\s+RedirectView\\s*\\(");

    // The calls that put an argument straight into a target, so an argument of one is an operand of the
    // target rather than a value something else worked out.
    private static final Set<String> SUBSTITUTING = Set.of("concat", "format", "formatted", "RedirectView");

    private static final char PLUS = '+';

    private static final char COMMA = ',';

    private static final char OPENS = '(';

    private static final char STATEMENT = ';';

    /**
     * Every redirect target a handler builds out of a value the request carried.
     *
     * @param source the source as written
     * @return one offence per such target, by line
     */
    static List<String> requestBuiltRedirects(CharSequence source) {
        Readings reading = new Readings(source, JavaCode.blanked(source), JavaCode.withoutComments(source));
        int type = TYPE.matcher(reading.code()).results().findFirst().map(MatchResult::start)
            .orElseGet(reading.code()::length);
        return MAPPING.matcher(reading.code()).results()
            .filter(mapping -> mapping.start() > type)
            .flatMap(mapping -> built(reading, mapping))
            .distinct()
            .toList();
    }

    // The parameter list of the method this mapping is written on, reached by stepping over whatever
    // arguments the annotation itself carries rather than reading them as a parameter list of their own.
    private static Stream<String> built(Readings reading, MatchResult mapping) {
        int closes = mapping.start(1) < 0
            ? mapping.end()
            : SpringMembers.closing(reading.code(), mapping.end() - 1);
        return SpringParameters.after(reading.code(), closes).stream().flatMap(range -> handled(reading, range));
    }

    private static Stream<String> handled(Readings reading, SpringParameters.Range parameters) {
        Set<String> sent = sent(reading, parameters);
        return sent.isEmpty()
            ? Stream.of()
            : body(reading.code(), parameters.closes())
                .stream()
                .flatMap(body -> dispatches(reading, body).flatMap(at -> operands(reading, at, sent)));
    }

    /*
     * The body of the method whose parameters these are. A semicolon before the brace means the method
     * declared no body at all, and the brace found after it would belong to another method, so nothing is
     * answered rather than the wrong body being read.
     */
    private static Optional<SpringParameters.Range> body(String code, int closes) {
        int opens = code.indexOf('{', closes);
        boolean declared = opens >= 0 && code.lastIndexOf(STATEMENT, opens) < closes;
        return declared
            ? Optional.of(new SpringParameters.Range(opens, SpringMembers.matching(code, opens)))
            : Optional.empty();
    }

    // Every parameter of this handler holding a value the caller sent that could name an address.
    private static Set<String> sent(Readings reading, SpringParameters.Range parameters) {
        return SpringParameters.in(reading.read(), reading.code(), parameters).stream()
            .filter(parameter -> BOUND.matcher(parameter.text()).find())
            .filter(parameter -> TEXTUAL.matcher(parameter.text()).find())
            .map(SpringParameters.Parameter::name)
            .collect(Collectors.toUnmodifiableSet());
    }

    // Where inside this handler a whole dispatch target is written. The literals are read rather than
    // blanked, because the prefix this looks for is one.
    private static Stream<Integer> dispatches(Readings reading, SpringParameters.Range body) {
        String written = reading.read().substring(body.opens(), body.closes());
        return Stream.concat(WHOLE_TARGET.matcher(written).results(), VIEW.matcher(written).results())
            .map(found -> body.opens() + found.start());
    }

    /*
     * The statement the dispatch sits in, which is where its operands are. A statement ends at the next
     * semicolon and opens after the semicolon or the brace before it, counted over the blanked reading so
     * that a semicolon written inside a string ends nothing.
     */
    private static Stream<String> operands(Readings reading, int at, Set<String> sent) {
        String code = reading.code();
        int upTo = Math.max(code.indexOf(STATEMENT, at), at);
        int from = 1 + Math.max(code.lastIndexOf(STATEMENT, at), delimited(code, at));
        return sent.stream().flatMap(name -> uses(reading, code.substring(from, upTo), from, name));
    }

    private static int delimited(String code, int at) {
        return Math.max(code.lastIndexOf('{', at), code.lastIndexOf('}', at));
    }

    private static Stream<String> uses(Readings reading, String statement, int from, String name) {
        return Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(statement).results()
            .filter(use -> operand(reading.code(), from + use.start()))
            .map(use -> offence(reading.source(), from + use.start(), name));
    }

    /*
     * Whether the name is written into the target, or handed to something that works one out. The
     * character in front of it says which. A plus or a comma makes it an operand of the expression
     * itself, and a parenthesis makes it an argument, which is an operand only of the calls that put an
     * argument straight into a target. A call the source writes around the name is a step the value
     * passed through, and what it passed through is not something this statement states.
     */
    private static boolean operand(String code, int at) {
        int before = nonBlankBefore(code, at);
        char preceding = code.charAt(before);
        return preceding == PLUS || preceding == COMMA || substituting(code, before, preceding);
    }

    private static boolean substituting(String code, int before, char preceding) {
        return preceding == OPENS && SUBSTITUTING.contains(identifierBefore(code, before));
    }

    private static int nonBlankBefore(String code, int at) {
        int before = at;
        while (before > 0 && Character.isWhitespace(code.charAt(before - 1))) {
            before -= 1;
        }
        return Math.max(before - 1, 0);
    }

    // The identifier written immediately in front of an offset, and nothing where the text there is not
    // one. A qualifier before it is not read, so String.format and a bare format are one call here.
    private static String identifierBefore(String code, int at) {
        int start = at;
        while (start > 0 && Character.isJavaIdentifierPart(code.charAt(start - 1))) {
            start -= 1;
        }
        return code.substring(start, at);
    }

    private static String offence(CharSequence source, int at, String name) {
        return "line " + JavaCode.lineOf(source, at) + ": the whole of this redirect target is built from "
            + name + ", which the caller sends, so a crafted link sends a reader who has just signed in to"
            + " whatever host that value names, and the sign-in they passed on the way is what makes the"
            + " destination read as the next step of the flow. Redirect to a target this handler chose, or"
            + " resolve the value against the addresses the application declares and redirect to what came"
            + " back";
    }

    /**
     * One source in the three readings a rule of this class needs at once.
     *
     * @param source the source as written, which a line number is counted over
     * @param code   the source with comments and literals blanked, which structure is read from
     * @param read   the source with its one-line literals kept, which a dispatch prefix is read from
     */
    private record Readings(CharSequence source, String code, String read) {
    }
}
