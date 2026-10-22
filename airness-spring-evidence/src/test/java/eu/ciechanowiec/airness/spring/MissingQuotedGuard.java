package eu.ciechanowiec.airness.spring;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * A genuine missing bean with a quoted name beside a literal at sign.
 */
final class MissingQuotedGuard {

    @PreAuthorize("@'missing.bean'.allowed('person@example.com')")
    String reached(String name) {
        return name;
    }
}
