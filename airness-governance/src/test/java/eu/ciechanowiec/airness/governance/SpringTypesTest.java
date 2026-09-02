package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringTypesTest {

    private static final List<Path> SOURCES = List.of(Path.of("src"));
    private static final String SOURCE = "src/main/java/sample/Account.java";
    private static final String MARKER = "theWordOnlyTheBodyHolds";
    private static final String ACCOUNT = """
        package sample;

        class Account {

            private static final String FIELD = "%s";
        }
        """.formatted(MARKER);

    @Test
    void namesADeclaredSourceByItsTypeAndItsPath() {
        assertEquals(
            "Account (%s)".formatted(SOURCE), declared().toString(),
            "what identifies a source is the type it declares and where it sits"
        );
    }

    @Test
    void keepsTheContentsOfASourceOutOfTheNameItIsPrintedBy() {
        assertFalse(
            declared().toString().contains(MARKER),
            "a diagnostic that names a source does not print the source"
        );
    }

    private static SpringTypes.Declared declared() {
        Path root = new GitFixture("declared-naming").write(SOURCE, ACCOUNT).root();
        return SpringTypes.over(root, JavaSources.under(root, SOURCES)).all().getFirst();
    }
}
