package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringPlaceholdersTest {

    @Test
    void acceptsAValueTakenWhollyFromTheEnvironment() {
        List<String> environmental = List.of(
            "", "${DB_PASSWORD}", "${DB_PASSWORD:}", "${DB_PASSWORD:${FALLBACK_PASSWORD}}",
            "${DB_PASSWORD:${FALLBACK_PASSWORD:}}", "${DB_USER}${DB_PASSWORD}"
        );

        assertEquals(
            List.of(), environmental.stream().filter(value -> !SpringPlaceholders.environmentOnly(value)).toList(),
            "a placeholder with no default, an empty one or another placeholder carries nothing of its own"
        );
    }

    @Test
    void refusesAValueThatCarriesALiteral() {
        List<String> literal = List.of(
            "secret", "${DB_PASSWORD:secret}", "${DB_PASSWORD:${FALLBACK_PASSWORD:secret}}",
            "prefix-${DB_PASSWORD}", "${DB_PASSWORD}-suffix", "${DB_PASSWORD", "${DB_PASSWORD:{secret}}"
        );

        assertEquals(
            List.of(), literal.stream().filter(SpringPlaceholders::environmentOnly).toList(),
            "a literal outside the placeholders, or as a default at any depth, ships inside the artifact"
        );
    }
}
