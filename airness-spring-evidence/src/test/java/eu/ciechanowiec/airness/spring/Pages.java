package eu.ciechanowiec.airness.spring;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The handlers of an application that renders pages beside some that do not, declared for the mappings
 * they register. No request ever reaches them, so what each would return is never read.
 */
@Controller
final class Pages {

    @GetMapping({"/rooms", "/rooms/{id}"})
    String room() {
        return "room";
    }

    @RequestMapping("/any")
    String any() {
        return "any";
    }

    @PostMapping("/rooms")
    String created() {
        return "redirect:/rooms";
    }

    @GetMapping("/rooms/{id}/count")
    @ResponseBody
    String count() {
        return "1";
    }

    @GetMapping("/rooms/{id}/download")
    ResponseEntity<byte[]> download() {
        return ResponseEntity.ok(new byte[0]);
    }

    @GetMapping(value = "/rooms/feed", produces = "application/json")
    String feed() {
        return "feed";
    }

    @GetMapping("/error")
    String error() {
        return "error";
    }
}
