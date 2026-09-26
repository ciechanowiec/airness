package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * How a fragment's argument list is read, apart from any file that declares one.
 */
class FragmentSignatureTest {

    @Test
    void countsTheArgumentsAFragmentDeclares() {
        assertEquals(4, FragmentSignature.arguments("field(label, name, control, error)"));
    }

    @Test
    void countsACommaInsideALiteralSubstitutionAsNoSeparator() {
        assertEquals(
            4, FragmentSignature.arguments("header('Room', ${name}, |Code ${code}, ${floor}|, ~{:: actions})"),
            "a substitution the engine composes is one value, and the comma in it is part of the sentence"
        );
    }

    @Test
    void readsTheNamesAFragmentDeclaresItsArgumentsUnder() {
        assertEquals(
            List.of("label", "name", "control"), FragmentSignature.names("field(label, name, control)"),
            "a fragment binds what it declares, in the order it declares it"
        );
    }

    @Test
    void readsNoNamesForAFragmentThatDeclaresNoList() {
        assertEquals(List.of(), FragmentSignature.names("rooms"), "a fragment taking nothing binds nothing");
    }

    @Test
    void readsNoNamesForAFragmentWhoseListIsEmpty() {
        assertEquals(List.of(), FragmentSignature.names("rooms()"), "an empty list declares no name at all");
    }

    @Test
    void countsNoArgumentsForAFragmentThatDeclaresNoList() {
        assertEquals(0, FragmentSignature.arguments("rooms"));
    }

    @Test
    void countsNoArgumentsForAFragmentWhoseListIsEmpty() {
        assertEquals(0, FragmentSignature.arguments("rooms()"));
    }

    @Test
    void readsACommaInsideAQuotedLiteralAsPartOfOneArgument() {
        assertEquals(2, FragmentSignature.arguments("header('Rooms, and what they seat', actions)"));
    }

    @Test
    void readsACommaInsideANestedCallAsPartOfOneArgument() {
        assertEquals(2, FragmentSignature.arguments("pill(status(room, now), tone)"));
    }

    @Test
    void countsNoArgumentsForAListThatIsNeverClosed() {
        assertEquals(0, FragmentSignature.arguments("field(label, name"));
    }

    @Test
    void readsACommaInsideACollectionAsPartOfOneArgument() {
        assertEquals(2, FragmentSignature.arguments("table(${ {'Room', 'Floor'} }, rows)"));
    }

    @Test
    void readsACommaInsideAnIndexAsPartOfOneArgument() {
        assertEquals(2, FragmentSignature.arguments("cell(columns[first, second], tone)"));
    }

    @Test
    void readsACommaInsideADoubleQuotedLiteralAsPartOfOneArgument() {
        assertEquals(2, FragmentSignature.arguments("header(\"Rooms, and what they seat\", actions)"));
    }

    @Test
    void namesAFragmentThatDeclaresNoArgumentList() {
        assertEquals("rooms", FragmentSignature.name("rooms"));
    }

    @Test
    void namesTheFragmentByWhatPrecedesItsArgumentList() {
        assertEquals("field", FragmentSignature.name("field(label, name)"));
    }
}
