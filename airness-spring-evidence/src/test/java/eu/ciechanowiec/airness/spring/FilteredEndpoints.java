package eu.ciechanowiec.airness.spring;

import java.util.List;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Collection filters with and without a separate authorization decision.
 */
@RestController
final class FilteredEndpoints {

    @GetMapping("/records/post")
    @PostFilter("true")
    List<String> filteredOutput() {
        return List.of("record");
    }

    @PostMapping("/records/pre")
    @PreFilter("true")
    List<String> filteredInput(List<String> records) {
        return records;
    }

    @GetMapping("/records/guarded")
    @PreAuthorize("isAuthenticated()")
    @PostFilter("true")
    List<String> guardedOutput() {
        return List.of("record");
    }
}
