package eu.ciechanowiec.airness.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A type whose annotation guards every handler it declares. The handler answers nothing, because no
 * request is ever put to it.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
final class GuardedType {

    @GetMapping("/typed")
    String typed(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
