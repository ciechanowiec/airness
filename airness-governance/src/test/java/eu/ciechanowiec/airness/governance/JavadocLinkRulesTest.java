package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The link resolver reports a resolvable type left as prose or as {@code @code}, accepts one already
 * linked, and reads past the regions where a name needs no link of its own.
 */
class JavadocLinkRulesTest {

    private static final int LONG_TEXT_BLOCK_LENGTH = 20_000;
    private static final Set<String> RESOLVE = Set.of("Vault", "Base64", "Settings", "IOException");
    private static final Set<String> NAMED = Set.of("Vault", "Record");
    private static final Set<String> JAVA_LANG = Set.of("Long", "Error", "Number", "String", "Record", "Thread");

    private static List<String> unlinked(CharSequence source) {
        return JavadocLinkRules.unlinked(source, RESOLVE::contains, _ -> false);
    }

    private static List<String> unlinkedWithJavaLang(CharSequence source) {
        return JavadocLinkRules.unlinked(source, NAMED::contains, JAVA_LANG::contains);
    }

    /**
     * Wraps {@code body} in a Javadoc comment on a throwaway class. A text block keeps the opening and
     * closing markers on separate lines, so the rule that forbids a single-line Javadoc does not read
     * this fixture as one.
     */
    private static String document(String body) {
        return """
            /**
             * %s
             */
            final class X {}""".formatted(body);
    }

    @Test
    void flagsAResolvableTypeLeftAsProse() {
        assertEquals(List.of("Vault"), unlinked(document("The Vault stores every token.")), "prose must link");
    }

    // No Java type name carries a hyphen, so a name opening a compound is a word. Read as a type, it made
    // the verdict turn on an unrelated import: one file was reported for a sentence that passed in the
    // file beside it, the difference being that the first imported a type the sentence never meant.
    @Test
    void readsANameOpeningAHyphenatedCompoundAsAnOrdinaryWord() {
        assertTrue(
            unlinked(document("A Vault-relative path names where the entry sits.")).isEmpty(),
            "a compound adjective is prose, and no link could ever be written for it"
        );
    }

    @Test
    void flagsAResolvableTypeWrittenAsCodeRatherThanLinked() {
        assertEquals(
            List.of("Base64"), unlinked(document("The original is {@code Base64}-encoded.")),
            "@code is for what a link cannot reach, not for a type that resolves"
        );
    }

    @Test
    void acceptsATypeThatIsAlreadyLinked() {
        assertTrue(unlinked(document("The {@link Vault} stores every token.")).isEmpty(), "a link is the point");
    }

    @Test
    void acceptsAnAdjectivalMentionOnceItIsLinked() {
        assertTrue(
            unlinked(document("The original is {@link Base64}-encoded.")).isEmpty(),
            "a type used as an adjective still resolves, so linking it is both possible and required"
        );
    }

    @Test
    void ignoresANameThatDoesNotResolveFromThisFile() {
        assertTrue(
            unlinked(document("Talks to Postgres over TLS and returns JSON.")).isEmpty(),
            "a word is only a type reference when the file could resolve it"
        );
    }

    @Test
    void readsPastTheTargetOfATagTheJavadocToolLinksItself() {
        assertTrue(
            unlinked(document("Reads it.\n * @throws IOException when the file is unreadable")).isEmpty(),
            "@throws links its own target, so wrapping it would be wrong rather than missing"
        );
    }

    @Test
    void readsPastACodeSample() {
        assertTrue(
            unlinked(document("Usage:\n * <pre>\n * Vault vault = new Vault();\n * </pre>")).isEmpty(),
            "a sample is code, and code names types without linking them"
        );
    }

    @Test
    void reportsEachNameOnceEvenWhenRepeated() {
        assertEquals(
            List.of("Settings"), unlinked(document("Settings win last. Settings start from defaults.")),
            "one fix per name, not one per mention"
        );
    }

    @Test
    void readsAJavaLangNameOpeningASentenceAsAnOrdinaryWord() {
        List<String> bodies = List.of(
            "Long names are cut short. Error codes are kept.", "Number of entries read.",
            "Error Prone reads it.", "Reads it.\n * Thread count is fixed.", "Reads it.  Long ones wrap.",
            "Reads it.\n *\n * <p>Error codes are kept.", "<P>Number of retries.", "Reads it!\n * Long ones wrap.",
            "Reads it? Long ones wrap.", "Lists:\n * <ul>\n * <li>Long names\n * </ul>"
        );
        for (String body : bodies) {
            assertTrue(unlinkedWithJavaLang(document(body)).isEmpty(), "a capitalized opening is a word: " + body);
        }
    }

    @Test
    void readsAJavaLangNameElsewhereAndANamedTypeAnywhereAsAType() {
        assertEquals(List.of("String"), unlinkedWithJavaLang(document("Returns the String it read.")), "mid-sentence");
        assertEquals(
            List.of("String"), unlinkedWithJavaLang(document("Reads a java.lang.String value.")),
            "a stop with no space after it ends no sentence"
        );
        assertEquals(
            List.of("Vault", "Record"), unlinkedWithJavaLang(document("Vault stores it. Record keeps it.")),
            "a type the file names itself is a type wherever it stands, even one that shadows java.lang"
        );
    }

    @Test
    void ignoresJavadocMarkersInsideAStringLiteral() {
        String marker = "/" + "** Vault */";
        assertTrue(
            unlinked(
                "final class X { String value = \"" + marker + "\"; }"
            ).isEmpty(),
            "a string that resembles Javadoc is still program data"
        );
    }

    @Test
    void ignoresJavadocMarkersPastALongTextBlockBody() {
        String marker = "/" + "** Vault */";
        String source = """
            final class X {
                String value = \"""
                    %s
                    %s
                    \""";
            }
            """.formatted("x".repeat(LONG_TEXT_BLOCK_LENGTH), marker);
        assertTrue(
            unlinked(source).isEmpty(),
            "a long text block stays one token without consuming regex stack or exposing its fixture"
        );
    }
}
