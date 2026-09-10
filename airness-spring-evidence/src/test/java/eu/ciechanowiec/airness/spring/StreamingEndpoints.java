package eu.ciechanowiec.airness.spring;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
final class StreamingEndpoints {

    private final AtomicInteger calls;

    StreamingEndpoints() {
        this.calls = new AtomicInteger();
    }

    @GetMapping("/direct")
    StreamingResponseBody direct() {
        this.calls.incrementAndGet();
        return output -> output.write("direct".getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/wrapped")
    ResponseEntity<StreamingResponseBody> wrapped() {
        this.calls.incrementAndGet();
        return ResponseEntity.ok().body(OutputStream::flush);
    }

    @GetMapping("/ordinary")
    ResponseEntity<String> ordinary() {
        return ResponseEntity.ok("ordinary");
    }

    int calls() {
        return this.calls.get();
    }
}
