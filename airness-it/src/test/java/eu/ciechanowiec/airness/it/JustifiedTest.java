package eu.ciechanowiec.airness.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Covers one fixture class and no other, which is what gives the per-class coverage floor something to
 * object to.
 *
 * <p>A module-wide floor would be satisfied here or not depending on arithmetic across the whole
 * module, and either answer would say nothing about which class is untested. Per class, this one is
 * covered and its neighbours are not, and the report names them.
 */
class JustifiedTest {

    @Test
    void readsTheAddressTheSuppressedRuleObjectsTo() {
        assertEquals("127.0.0.1", new Justified().address(), "the fixture returns the loopback address");
    }
}
