package eu.ciechanowiec.airness.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A handler restricting itself to no HTTP method behind a chain that admits a caller by only one method
 * other than the one a caller would try first. It answers the address it was reached at, which no request
 * ever asks for.
 */
@RestController
final class Webhook {

    @RequestMapping("/webhooks/payment")
    String payment(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
