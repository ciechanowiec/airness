package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * How a fragment's argument list is read, apart from any file that declares one.
 */
class TemplateFragmentArgumentsTest {

    @Test
    void countsTheArgumentsAFragmentDeclares() {
        assertEquals(4, TemplateFragmentCheck.arguments("field(label, name, control, error)"));
    }

    @Test
    void countsNoArgumentsForAFragmentThatDeclaresNoList() {
        assertEquals(0, TemplateFragmentCheck.arguments("rooms"));
    }

    @Test
    void countsNoArgumentsForAFragmentWhoseListIsEmpty() {
        assertEquals(0, TemplateFragmentCheck.arguments("rooms()"));
    }

    @Test
    void readsACommaInsideAQuotedLiteralAsPartOfOneArgument() {
        assertEquals(2, TemplateFragmentCheck.arguments("header('Rooms, and what they seat', actions)"));
    }

    @Test
    void readsACommaInsideANestedCallAsPartOfOneArgument() {
        assertEquals(2, TemplateFragmentCheck.arguments("pill(status(room, now), tone)"));
    }

    @Test
    void countsNoArgumentsForAListThatIsNeverClosed() {
        assertEquals(0, TemplateFragmentCheck.arguments("field(label, name"));
    }

    @Test
    void readsACommaInsideACollectionAsPartOfOneArgument() {
        assertEquals(2, TemplateFragmentCheck.arguments("table(${ {'Room', 'Floor'} }, rows)"));
    }

    @Test
    void readsACommaInsideAnIndexAsPartOfOneArgument() {
        assertEquals(2, TemplateFragmentCheck.arguments("cell(columns[first, second], tone)"));
    }

    @Test
    void readsACommaInsideADoubleQuotedLiteralAsPartOfOneArgument() {
        assertEquals(2, TemplateFragmentCheck.arguments("header(\"Rooms, and what they seat\", actions)"));
    }

    @Test
    void namesAFragmentThatDeclaresNoArgumentList() {
        assertEquals("rooms", TemplateFragmentCheck.name("rooms"));
    }

    @Test
    void namesTheFragmentByWhatPrecedesItsArgumentList() {
        assertEquals("field", TemplateFragmentCheck.name("field(label, name)"));
    }
}
