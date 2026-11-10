package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An assertion library whose calls carry no assertion prefix, recognised only in a source that names it.
 *
 * <p>Most assertions are recognised by name wherever they are written: a call starting with {@code assert},
 * {@code expect} or {@code verify}, a call to {@code fail}, and the {@code assert} statement. A few libraries
 * assert through a name that means something else in other code, such as the {@code then} of a BDD assertion,
 * which a reactive pipeline also declares. Such a call counts only in a source that names the library, so a
 * {@code Mono.then()} in a source that names none of them is not read as an assertion.
 *
 * @param library the qualified name whose presence in the code of a source puts the library in use
 * @param call    a call of the library that judges an outcome
 */
record AssertionLibrary(Pattern library, Pattern call) {

    /*
     * The assertion statement is a keyword, so the word stands for nothing else wherever it is code. The
     * names are matched as calls, and a helper named with one of their prefixes is an assertion wherever it
     * is declared, which is the convention the Java guide documents.
     */
    private static final Pattern NAMED = Pattern.compile(
        "\\b(?:(?:assert\\w*|fail|expect\\w*|verify\\w*)\\s*\\(|assert\\b)"
    );
    /*
     * The entry points of the BDD assertions of AssertJ. A source may declare a helper of its own that starts
     * with "then", and such a helper is followed into rather than taken for an assertion, so the names are
     * the library's own rather than every name with that prefix.
     */
    private static final String BDD_ENTRY = "then(?:Code|ThrownBy|Object|NoException|\\w*Exception\\w*)?\\s*\\(";
    /*
     * What follows an entry point within one statement. A chain may run through any number of calls before
     * the one that judges, and it ends at the semicolon closing the statement.
     */
    private static final String SAME_STATEMENT = "[^;]*?\\.\\s*";
    private static final List<AssertionLibrary> LIBRARIES = List.of(
        new AssertionLibrary(
            "org\\.assertj\\.core\\.api\\.\\w*BDDAssertions\\b",
            "(?<![\\w.])" + BDD_ENTRY + "|\\bBDDAssertions\\s*\\.\\s*then\\w*\\s*\\("
        ),
        new AssertionLibrary("org\\.assertj\\.core\\.api\\.\\w*BDDSoftAssertions\\b", "\\.\\s*" + BDD_ENTRY),
        new AssertionLibrary("org\\.mockito\\.BDDMockito\\b", "\\bthen\\s*\\(" + SAME_STATEMENT + "should\\w*\\s*\\("),
        new AssertionLibrary("\\borg\\.awaitility\\.", "\\.\\s*until\\w*\\s*\\("),
        new AssertionLibrary(
            "\\bio\\.restassured\\.", "\\.\\s*then\\s*\\(\\s*\\)" + SAME_STATEMENT
                + "(?:statusCode|statusLine|body|contentType|header|headers|cookie|cookies|time|spec)\\s*\\("
        )
    );

    private AssertionLibrary(String library, String call) {
        this(Pattern.compile(library), Pattern.compile(call));
    }

    /**
     * Every call that asserts in a source: the names recognised wherever they are written, and the calls of
     * each library the source names.
     *
     * @param code the code of a Java source, with its literals and comments blanked
     * @return one pattern matching any assertion of that source
     */
    static Pattern assertions(CharSequence code) {
        return Pattern.compile(
            Stream.concat(
                Stream.of(NAMED),
                LIBRARIES.stream().filter(entry -> entry.library.matcher(code).find()).map(AssertionLibrary::call)
            ).map(Pattern::pattern).collect(Collectors.joining("|"))
        );
    }
}
