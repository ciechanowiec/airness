package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageParityCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String WRITTEN_IN = "src/main/resources/messages.properties";

    private static final String TRANSLATED = "src/main/resources/messages_pl.properties";

    private static final String TWO_NAMES = """
        room.name=Name
        room.code=Code
        """;

    private static final String TWO_NAMES_TRANSLATED = """
        room.name=Nazwa
        room.code=Kod
        """;

    private static final String ONE_NAME_TRANSLATED = """
        room.name=Nazwa
        """;

    @Test
    void leavesABundleWhoseLanguagesDeclareTheSameNamesAlone() {
        Path root = new GitFixture("parity-agreed")
            .write(WRITTEN_IN, TWO_NAMES)
            .write(TRANSLATED, TWO_NAMES_TRANSLATED)
            .root();
        assertTrue(Verdicts.clean(findings(root)), "two languages declaring one set of names is what the rule wants");
    }

    @Test
    void reportsANameOneLanguageDeclaresAndAnotherOmits() {
        Path root = new GitFixture("parity-omitted")
            .write(WRITTEN_IN, TWO_NAMES)
            .write(TRANSLATED, ONE_NAME_TRANSLATED)
            .root();
        assertEquals(1, omissions(root).size(), "a name missing from one language is reported once");
    }

    @Test
    void namesTheOmittedNameAndTheLanguageThatLacksIt() {
        Path root = new GitFixture("parity-named")
            .write(WRITTEN_IN, TWO_NAMES)
            .write(TRANSLATED, ONE_NAME_TRANSLATED)
            .root();
        assertTrue(
            omissions(root).getFirst().startsWith("src/main/resources/messages_pl.properties: room.code"),
            "an offence names the file that lacks the name and the name it lacks"
        );
    }

    @Test
    void readsAnOmissionOfTheLanguageTheProjectWritesInToo() {
        Path root = new GitFixture("parity-added")
            .write(WRITTEN_IN, ONE_NAME_TRANSLATED)
            .write(TRANSLATED, TWO_NAMES_TRANSLATED)
            .root();
        assertTrue(
            omissions(root).getFirst().startsWith("src/main/resources/messages.properties: room.code"),
            "a name a translation carries and the base has dropped is the same defect from the other side"
        );
    }

    @Test
    void readsALanguageQualifiedByACountryAsTheSameBundle() {
        Path root = new GitFixture("parity-country")
            .write(WRITTEN_IN, TWO_NAMES)
            .write("src/main/resources/messages_pl_PL.properties", ONE_NAME_TRANSLATED)
            .root();
        assertEquals(
            1, omissions(root).size(), "a language written for one country is still that language's file"
        );
    }

    @Test
    void leavesABundleWrittenInOneLanguageAlone() {
        Path root = new GitFixture("parity-alone").write(WRITTEN_IN, TWO_NAMES).root();
        assertTrue(Verdicts.clean(findings(root)), "a project of one language has nothing to disagree with");
    }

    @Test
    void readsTwoDirectoriesAsTwoBundlesRatherThanOne() {
        Path root = new GitFixture("parity-directories")
            .write("src/main/resources/one/messages.properties", TWO_NAMES)
            .write("src/main/resources/two/messages.properties", ONE_NAME_TRANSLATED)
            .root();
        assertTrue(Verdicts.clean(findings(root)), "two bundles of one name in two directories are two bundles");
    }

    @Test
    void readsASuffixThatNamesNoLanguageAsPartOfTheName() {
        Path root = new GitFixture("parity-suffix")
            .write("src/main/resources/report.properties", TWO_NAMES)
            .write("src/main/resources/report_summary.properties", ONE_NAME_TRANSLATED)
            .root();
        assertTrue(Verdicts.clean(findings(root)), "a summary is not a tongue, so the two files are two bundles");
    }

    @Test
    void reportsANameDeclaredTwiceInOneFile() {
        String twice = """
            room.name=Name
            room.code=Code
            room.name=Name again
            """;
        Path root = new GitFixture("parity-repeated").write(WRITTEN_IN, twice).root();
        assertTrue(
            repeats(root).getFirst().startsWith("src/main/resources/messages.properties:3: room.name"),
            "an offence names the file, the line of the second declaration, and the name"
        );
    }

    @Test
    void readsNoNameOutOfAValueCarriedOntoTheNextLine() {
        String carried = """
            room.name=A name that runs on \\
            room.code=and ends here
            """;
        Path root = new GitFixture("parity-carried")
            .write(WRITTEN_IN, carried)
            .write(TRANSLATED, ONE_NAME_TRANSLATED)
            .root();
        assertTrue(Verdicts.clean(findings(root)), "a continued value is part of a value and declares nothing");
    }

    @Test
    void readsAValueCarriedOntoTheNextLineUnderEitherLineEnding() {
        String carried = "room.name=A name that runs on \\\r\nroom.code=and ends here\r\n";
        Path root = new GitFixture("parity-carried-returns")
            .write(WRITTEN_IN, carried)
            .write(TRANSLATED, ONE_NAME_TRANSLATED)
            .root();
        assertTrue(
            Verdicts.clean(findings(root)),
            "a carriage return closing the line does not stop the carry from being read"
        );
    }

    @Test
    void readsNoNameOutOfACommentInEitherSpelling() {
        String commented = """
            # room.code=Code
            ! room.floor=Floor
            room.name=Name
            """;
        Path root = new GitFixture("parity-commented")
            .write(WRITTEN_IN, commented)
            .write(TRANSLATED, ONE_NAME_TRANSLATED)
            .root();
        assertTrue(Verdicts.clean(findings(root)), "a comment declares nothing, in both spellings a bundle allows");
    }

    @Test
    void readsASeparatorWrittenIntoANameAsPartOfThatName() {
        Path root = new GitFixture("parity-escaped")
            .write(WRITTEN_IN, "room\\:name=Name\n")
            .write(TRANSLATED, ONE_NAME_TRANSLATED)
            .root();
        assertTrue(
            omissions(root).stream().anyMatch(offence -> offence.contains("room:name")),
            "an escaped separator is carried into the name, and the escape itself is not"
        );
    }

    @Test
    void namesTheRepairAsWellAsTheDefect() {
        Path root = new GitFixture("parity-repair")
            .write(WRITTEN_IN, TWO_NAMES)
            .write(TRANSLATED, ONE_NAME_TRANSLATED)
            .root();
        assertTrue(
            omissions(root).getFirst().contains("Declare it here, or remove it from every language of the bundle"),
            "no document states this rule, so the offence is where a reader learns what it wants"
        );
    }

    private static List<String> omissions(Path root) {
        return Verdicts.offences(findings(root), "one language declares and another omits");
    }

    private static List<String> repeats(Path root) {
        return Verdicts.offences(findings(root), "declared more than once");
    }

    private static List<Findings> findings(Path root) {
        return new MessageParityCheck(root, RESOURCES).findings();
    }
}
