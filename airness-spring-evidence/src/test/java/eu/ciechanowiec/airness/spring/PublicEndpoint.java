package eu.ciechanowiec.airness.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A handler the chain admits, declared for the mapping it registers. It answers the address it was
 * reached at, which no request ever asks for: the evidence this fixture serves puts a request to the
 * authorization chain rather than to a handler.
 */
@RestController
final class PublicEndpoint {

    @GetMapping("/public")
    String open(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
