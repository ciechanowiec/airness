package eu.ciechanowiec.airness.spring;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Literal at signs and quoted bean references in real security annotations.
 */
final class QuotedGuards {

    @PreAuthorize("authentication.name == 'person@example.com'")
    String email(String name) {
        return name;
    }

    @PreAuthorize("authentication.name == 'person''@example.com' or authentication.name == \"person\"\"@example.com\"")
    String escaped(String name) {
        return name;
    }

    @PreAuthorize("@clearance.granted('person@example.com')")
    String delegated(String name) {
        return name;
    }

    @PreAuthorize("@'permission.bean'.granted('person@example.com') and @\"permission.bean\".open")
    String quoted(String name) {
        return name;
    }

    @PreAuthorize("@'permission''bean' != null")
    String escapedBean(String name) {
        return name;
    }
}
