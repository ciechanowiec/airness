package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * How a link expression is read apart from any file that carries one.
 */
class TemplateLinkExpressionsTest {

    @Test
    void readsWhatOneLinkCarries() {
        assertEquals(List.of("/rooms"), TemplateLinkCheck.links("@{/rooms}"));
    }

    @Test
    void readsEveryLinkWrittenInOneValue() {
        assertEquals(List.of("/rooms", "/clients"), TemplateLinkCheck.links("@{/rooms} and @{/clients}"));
    }

    @Test
    void countsPastThePathVariablesALinkNests() {
        assertEquals(
            List.of("/rooms/{reference}(reference=${room.reference()})"),
            TemplateLinkCheck.links("@{/rooms/{reference}(reference=${room.reference()})}")
        );
    }

    @Test
    void stopsALinkAtTheBraceThatClosesItRatherThanAtTheEndOfTheValue() {
        assertEquals(
            List.of("/rooms"),
            TemplateLinkCheck.links("~{:: link('Rooms', @{/rooms}, ${sections.current()})}")
        );
    }

    @Test
    void readsNoLinkOutOfOneNobodyCloses() {
        assertEquals(List.of(), TemplateLinkCheck.links("@{/rooms"));
    }

    @Test
    void readsNoLinkOutOfAValueThatCarriesNone() {
        assertEquals(List.of(), TemplateLinkCheck.links("drawn=${artwork}"));
    }

    @Test
    void readsTheVariableExpressionsALinkInterpolates() {
        assertEquals(List.of("drawn"), TemplateLinkCheck.expressions("${drawn}"));
    }

    @Test
    void readsASelectionExpressionLikeAVariableOne() {
        assertEquals(List.of("chosen"), TemplateLinkCheck.expressions("*{chosen}"));
    }

    @Test
    void readsEveryExpressionOneLinkInterpolates() {
        assertEquals(
            List.of("room.reference()", "tone"),
            TemplateLinkCheck.expressions("/rooms/${room.reference()}/${tone}")
        );
    }

    @Test
    void readsNoExpressionOutOfAnAddressThatIsOnlyAnAddress() {
        assertEquals(List.of(), TemplateLinkCheck.expressions("/rooms/new"));
    }

    @Test
    void readsNoExpressionOutOfOneNobodyCloses() {
        assertEquals(List.of(), TemplateLinkCheck.expressions("/rooms/${reference"));
    }

    @Test
    void namesABeanAnExpressionReachesFor() {
        assertEquals(Optional.of("a bean"), TemplateLinkCheck.reached("@artwork.thumbnail(code)"));
    }

    @Test
    void namesAStaticClassAnExpressionReachesFor() {
        assertEquals(Optional.of("a static class"), TemplateLinkCheck.reached("T(java.lang.System).getenv('HOME')"));
    }

    @Test
    void namesAnInstantiationAnExpressionReachesFor() {
        assertEquals(Optional.of("an instantiation"), TemplateLinkCheck.reached("new java.lang.String(code)"));
    }

    @Test
    void namesNothingForAnExpressionThatOnlyReadsTheModel() {
        assertEquals(Optional.empty(), TemplateLinkCheck.reached("room.reference()"));
    }

    @Test
    void readsNoBeanOutOfAnAddressWrittenInsideAQuotedLiteral() {
        assertEquals(Optional.empty(), TemplateLinkCheck.reached("'events@example.com'"));
    }
}
