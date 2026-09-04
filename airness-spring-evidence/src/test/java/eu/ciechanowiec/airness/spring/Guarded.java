package eu.ciechanowiec.airness.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A handler whose own annotation takes the decision off the chain. It answers the address it was reached
 * at, which no request ever asks for, since the evidence puts a request to the chain and not to a handler.
 */
@RestController
final class Guarded {

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/guarded")
    String guarded(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
