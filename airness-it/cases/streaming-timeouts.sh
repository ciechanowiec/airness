#!/usr/bin/env sh

run_streaming_timeout_cases() {
    # Built here rather than cloned from the spring cases, so that the two can run in separate lanes.
    # This is the same application the open-named case builds, minus the endpoint: the chain and the
    # advice a web module owes stay, and streaming controllers take the place of the plain one. The
    # endpoint is staged before it is removed, exactly as cloning that fixture used to leave it.
    streaming_app="${scratch}/streaming-app"
    build_spring_application "${streaming_app}" streaming_assets
    build_spring_context_test "${streaming_app}"
    write_spring_web_module "${streaming_app}" '/api/orders'
    git -C "${streaming_app}" add --all
    rm "${streaming_app}/src/main/java/com/example/Orders.java"
    cat > "${streaming_app}/src/main/java/com/example/Streams.java" <<'JAVA'
package com.example;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Writes bytes through both return types supported by Spring MVC.
 */
@RestController
public final class Streams {

    /**
     * Writes a direct response.
     *
     * @return the response writer
     */
    @GetMapping("/stream/direct")
    public StreamingResponseBody direct() {
        return Streams::write;
    }

    /**
     * Writes a response with an explicit status.
     *
     * @return the wrapped response writer
     */
    @GetMapping("/stream/wrapped")
    public ResponseEntity<StreamingResponseBody> wrapped() {
        return ResponseEntity.ok().body(Streams::write);
    }

    /**
     * Exercises the ordinary failure advice.
     *
     * @return no normal response
     * @throws IllegalArgumentException for the requested failure
     */
    @GetMapping("/stream/failure")
    public String failure() {
        throw new IllegalArgumentException("Requested fixture failure");
    }

    @SneakyThrows
    private static void write(OutputStream output) {
        output.write("streamed".getBytes(StandardCharsets.UTF_8));
    }
}
JAVA
    cat > "${streaming_app}/src/main/java/com/example/Security.java" <<'JAVA'
package com.example;

import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Opens only the fixture's named HTTP endpoints.
 */
@Configuration(proxyBeanMethods = false)
public final class Security {

    /**
     * Builds the chain used by the real HTTP requests.
     *
     * @param http the chain under construction
     * @return the built chain
     */
    @Bean
    @SneakyThrows
    SecurityFilterChain chain(HttpSecurity http) {
        return http.authorizeHttpRequests(
            registry -> registry
                .requestMatchers("/stream/direct", "/stream/wrapped", "/stream/failure", "/error").permitAll()
                .anyRequest().authenticated()
        ).build();
    }
}
JAVA
    cat > "${streaming_app}/src/test/java/com/example/StreamsTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@ApplicationTest
class StreamsTest {

    @Test
    void receivesDirectStreamingBytes(@LocalServerPort int port) {
        HttpResponse<String> response = get(port, "/stream/direct");
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        assertEquals("streamed", response.body());
    }

    @Test
    void receivesWrappedStreamingBytes(@LocalServerPort int port) {
        HttpResponse<String> response = get(port, "/stream/wrapped");
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        assertEquals("streamed", response.body());
    }

    @Test
    void reachesTheFailureAdvice(@LocalServerPort int port) {
        HttpResponse<String> response = get(port, "/stream/failure");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.statusCode());
        assertEquals("failed", response.body());
    }

    @SneakyThrows
    private static HttpResponse<String> get(int port, String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }
}
JAVA
    mkdir -p "${streaming_app}/src/main/resources" "${streaming_app}/src/test/resources"
    git -C "${streaming_app}" add --all
    run_maven streaming_missing spring "${streaming_app}" clean prepare-package
    expect_exit streaming_missing 'spring: streaming requires a production timeout policy' 1
    expect_match streaming_missing 'spring: missing streaming policy identifies the endpoint' 'Streams#direct'
    expect_match streaming_missing 'spring: an inherited container timeout is diagnosed' \
        'Streaming endpoints inheriting the servlet container timeout'
    expect_no_match streaming_missing 'spring: missing streaming policy stops before Qodana' 'qodana:.*:scan'

    printf 'spring.mvc.async.request-timeout=0\n' > "${streaming_app}/src/test/resources/application.properties"
    run_maven streaming_test_only spring "${streaming_app}" clean prepare-package
    expect_exit streaming_test_only 'spring: a test-only timeout cannot satisfy production policy' 1
    expect_match streaming_test_only 'spring: test settings are distinguished from production policy' \
        'Streaming endpoints without a production timeout policy'
    expect_no_match streaming_test_only 'spring: explicit test timeout is not called inherited' \
        'Streaming endpoints inheriting the servlet container timeout'

    rm "${streaming_app}/src/test/resources/application.properties"
    printf "spring.mvc.async.request-timeout=\${STREAMING_TIMEOUT:30s}\n" \
        > "${streaming_app}/src/main/resources/application.properties"
    run_maven streaming_property spring "${streaming_app}" clean prepare-package
    expect_exit streaming_property 'spring: a production placeholder supplies an explicit finite timeout' 0
    expect_match streaming_property 'spring: real HTTP exercises both streaming return types' \
        'Tests run: 3, Failures: 0, Errors: 0, Skipped: 0.*StreamsTest'
    run_maven streaming_stale spring "${streaming_app}" airness:spring-streaming-timeouts
    expect_exit streaming_stale 'spring: a new Maven run cannot reuse old streaming evidence' 1
    expect_match streaming_stale 'spring: stale evidence requires a current assessment' \
        'Streaming timeouts without a current runtime assessment'

    printf 'spring.mvc.async.request-timeout=0\n' > "${streaming_app}/src/main/resources/application-production.properties"
    rm "${streaming_app}/src/main/resources/application.properties"
    run_maven streaming_profile spring "${streaming_app}" clean prepare-package -Dspring.profiles.active=production
    expect_exit streaming_profile 'spring: a loaded production profile may explicitly disable the deadline' 0
    run_maven streaming_inactive spring "${streaming_app}" clean prepare-package
    expect_exit streaming_inactive 'spring: an inactive profile cannot supply a streaming policy' 1
    rm "${streaming_app}/src/main/resources/application-production.properties"

    write_streaming_configurer 'configurer.setDefaultTimeout(0).registerCallableInterceptors();'
    git -C "${streaming_app}" add --all
    run_maven streaming_java spring "${streaming_app}" clean prepare-package
    expect_exit streaming_java 'spring: an active production callback may explicitly disable the deadline' 0

    write_streaming_configurer 'if (!Boolean.getBoolean("fixture.streaming.disabled")) {
            configurer.setDefaultTimeout(0);
        }'
    cat > "${streaming_app}/src/test/java/com/example/ConditionalCallbackTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

class ConditionalCallbackTest {

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void theDisabledBranchLeavesTheChoiceUnset() {
        String key = "fixture.streaming.disabled";
        Optional<String> previous = Optional.ofNullable(System.getProperty(key));
        System.setProperty(key, "true");
        try {
            AsyncSupportConfigurer configurer = new AsyncSupportConfigurer();
            new StreamingConfiguration().configureAsyncSupport(configurer);
            assertNull(new DirectFieldAccessor(configurer).getPropertyValue("timeout"));
        } finally {
            previous.ifPresentOrElse(value -> System.setProperty(key, value), () -> System.clearProperty(key));
        }
    }
}
JAVA
    run_maven streaming_conditional spring "${streaming_app}" clean prepare-package
    expect_exit streaming_conditional 'spring: conditional callbacks fail when their policy cannot be verified' 1
    expect_match streaming_conditional 'spring: unsupported callbacks are diagnosed explicitly' \
        'Streaming timeout configuration cannot be verified'

    rm "${streaming_app}/src/test/java/com/example/ConditionalCallbackTest.java"
    write_streaming_configurer 'applyTimeout(configurer);'
    python3 - "${streaming_app}/src/main/java/com/example/StreamingConfiguration.java" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
index = text.rfind('}')
path.write_text(text[:index] + '''
    private static void applyTimeout(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(0);
    }
}
''')
PY
    run_maven streaming_delegate spring "${streaming_app}" clean prepare-package
    expect_exit streaming_delegate 'spring: delegated timeout setup is not guessed safe' 1
    expect_match streaming_delegate 'spring: delegated setup remains visibly unsupported' \
        'Streaming timeout configuration cannot be verified'

    cat > "${streaming_app}/src/main/java/com/example/StreamingConfiguration.java" <<'JAVA'
package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Supplies an anonymous timeout callback that cannot be tied to a named source declaration.
 */
@Configuration(proxyBeanMethods = false)
public final class StreamingConfiguration {

    /**
     * Creates the callback used by production MVC.
     *
     * @return the anonymous callback
     */
    @Bean
    WebMvcConfigurer timeoutPolicy() {
        return new WebMvcConfigurer() {

            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setDefaultTimeout(0);
            }
        };
    }
}
JAVA
    run_maven streaming_anonymous spring "${streaming_app}" clean prepare-package
    expect_exit streaming_anonymous 'spring: anonymous timeout callbacks require an assessable declaration' 1
    expect_match streaming_anonymous 'spring: anonymous timeout setup is explicitly unsupported' \
        'Streaming timeout configuration cannot be verified'

    write_streaming_configurer 'configurer.setDefaultTimeout(0).registerCallableInterceptors();'
    git -C "${streaming_app}" add --all
    git -C "${streaming_app}" commit --quiet --message 'test(it): exercise streaming timeout governance' \
        --message 'Real HTTP requests exercise direct and wrapped streaming with an explicit production timeout.'
    run_maven streaming_extended spring "${streaming_app}" clean verify -Pextended
    expect_exit streaming_extended 'spring: streaming passes the complete Extended consumer lifecycle' 0
    expect_match streaming_extended 'spring: streaming Extended executes Qodana' 'Analysis results: 0 problem detected'
    expect_match streaming_extended 'spring: streaming Extended inspects the final packaged artifact' \
        'airness:.*:artifact-content'
}

write_streaming_configurer() {
    cat > "${streaming_app}/src/main/java/com/example/StreamingConfiguration.java" <<JAVA
package com.example;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Chooses a timeout for the production MVC dispatcher.
 */
@Configuration(proxyBeanMethods = false)
public final class StreamingConfiguration implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        $1
    }
}
JAVA
}
