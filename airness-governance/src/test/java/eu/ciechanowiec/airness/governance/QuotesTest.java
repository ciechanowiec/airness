package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Quotation marks around a value are spelling, and only a matching pair is removed.
 */
class QuotesTest {

    @Test
    void removesAMatchingPairOfEitherKind() {
        assertEquals("mongo:7", Quotes.stripped("\"mongo:7\""), "double quotation marks are removed");
        assertEquals("mongo:7", Quotes.stripped(" 'mongo:7' "), "single ones are, with the blank space around them");
    }

    @Test
    void leavesAnUnmatchedOrAbsentQuotationMarkAlone() {
        assertEquals("\"mongo:7'", Quotes.stripped("\"mongo:7'"), "a mismatched pair is part of the value");
        assertEquals("mongo:7", Quotes.stripped("mongo:7"), "and an unquoted value is itself");
    }
}
