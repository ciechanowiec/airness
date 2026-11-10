package eu.ciechanowiec.airness.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A handler restricting itself to no HTTP method, which is what registers a mapping the chain has to be
 * read against for every method a caller can send, and recorded once under the first of them the chain
 * admits. It answers the address it was reached at, which no request ever asks for.
 */
@RestController
final class Any {

    @RequestMapping("/any")
    String any(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
