package eu.ciechanowiec.airness.spring;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * A type whose guard names a bean the application declares nothing under, written on the type so that
 * a guard governing every method of one is read as well as a guard written on a single method.
 */
@PreAuthorize("@clearence.granted('reference')")
final class Misdirected {

    String reached(String reference) {
        return reference.strip();
    }
}
