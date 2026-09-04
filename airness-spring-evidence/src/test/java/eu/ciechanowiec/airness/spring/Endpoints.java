package eu.ciechanowiec.airness.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Three ordinary handlers of an application, declared for the mappings they register.
 *
 * <p>Each answers what it was reached by, and no request ever asks for it: the evidence this fixture
 * serves puts a request to the authorization chain rather than to a handler, so what a handler would
 * have written is never read. What matters about them is the mappings they register.
 */
@RestController
final class Endpoints {

    @GetMapping("/named/{name}")
    String named(String name) {
        return name;
    }

    @PostMapping("/named")
    String created(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @GetMapping("/uncovered")
    String uncovered(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
