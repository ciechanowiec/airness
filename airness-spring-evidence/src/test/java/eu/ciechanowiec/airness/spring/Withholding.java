package eu.ciechanowiec.airness.spring;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * A type whose guard calls a method the bean it names declares without making it public, which the
 * expression engine cannot call.
 */
final class Withholding {

    @PreAuthorize("@withheld.granted(#reference)")
    String reached(String reference) {
        return reference.strip();
    }
}
