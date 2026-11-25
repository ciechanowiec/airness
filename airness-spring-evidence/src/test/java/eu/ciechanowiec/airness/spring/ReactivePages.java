package eu.ciechanowiec.airness.spring;

import eu.ciechanowiec.airness.Justification;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The handlers of a reactive application that renders pages beside some that do not, declared for the
 * mappings they register. No request ever reaches them, so what each would return is never read.
 */
@Controller
@Justification(
    "A handler that renders a page answers with the name of its view, which is the same on every request by"
        + " design. What each handler here returns is never read, only the mapping it registers."
)
@SuppressWarnings("SameReturnValue")
final class ReactivePages {

    @GetMapping({"/orders", "/orders/{id}"})
    String order() {
        return "order";
    }

    @PostMapping("/orders")
    String placed() {
        return "placed";
    }

    @GetMapping("/orders.json")
    @ResponseBody
    String listed() {
        return "[]";
    }

    @GetMapping("/receipt")
    ResponseEntity<String> receipt() {
        return ResponseEntity.ok("receipt");
    }

    @GetMapping(value = "/feed", produces = MediaType.APPLICATION_JSON_VALUE)
    String feed() {
        return "feed";
    }

    @GetMapping("/error")
    String error() {
        return "error";
    }
}
