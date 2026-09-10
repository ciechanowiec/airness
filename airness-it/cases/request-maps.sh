#!/usr/bin/env sh

run_request_map_cases() {
    request_map_app="$scratch/request-map-app"
    clone_tree "$spring_open_named" "$request_map_app"
    rm "$request_map_app/src/main/java/com/example/Orders.java"
    cat > "$request_map_app/src/main/java/com/example/Parameters.java" <<'JAVA'
package com.example;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Observable whole-request binding through the production Spring resolvers.
 */
@RestController
public final class Parameters {

    /**
     * Returns every submitted parameter and its first value.
     *
     * @param values the complete request parameters
     * @return the sorted parameters
     */
    @GetMapping("/parameters")
    public String all(@RequestParam Map<String, String> values) {
        return joined(values);
    }

    /**
     * Preserves every value supplied under each parameter name.
     *
     * @param values the complete repeated parameters
     * @return the sorted parameters with their values in submission order
     */
    @GetMapping("/repeated")
    public String repeated(@RequestParam MultiValueMap<String, String> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + String.join("/", entry.getValue()))
            .collect(Collectors.joining(","));
    }

    /**
     * Demonstrates that a whole-request map ignores individual-value settings.
     *
     * @param renamed the complete request parameters
     * @return the sorted parameters without a synthetic default value
     */
    @GetMapping("/ignored")
    public String ignored(@RequestParam(required = true, defaultValue = "fallback") Map<String, String> renamed) {
        return joined(renamed);
    }

    /**
     * Keeps an explicitly named map on Spring's individual-parameter path.
     *
     * @param payload the required named value
     * @return the converted named value
     */
    @GetMapping("/named")
    public String named(@RequestParam(name = "payload", required = true) Map<String, String> payload) {
        return joined(payload);
    }

    /**
     * Exercises the fixture's ordinary failure advice.
     *
     * @param message the explicit failure description
     * @return no normal response
     * @throws IllegalArgumentException for the requested failure
     */
    @GetMapping("/failure")
    public String failure(@RequestParam(name = "message", required = true) String message) {
        throw new IllegalArgumentException(message);
    }

    private static String joined(Map<String, String> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(","));
    }
}
JAVA
    cat > "$request_map_app/src/main/java/com/example/Security.java" <<'JAVA'
package com.example;

import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Names the public endpoints and preserves Spring's error responses.
 */
@Configuration(proxyBeanMethods = false)
public final class Security {

    /**
     * Builds the chain every request is decided by.
     *
     * @param http the chain under construction
     * @return the built chain
     */
    @Bean
    @SneakyThrows
    SecurityFilterChain chain(HttpSecurity http) {
        return http
            .authorizeHttpRequests(
                registry -> registry
                    .requestMatchers("/parameters", "/repeated", "/ignored", "/named", "/failure", "/error").permitAll()
                    .anyRequest().authenticated()
            )
            .build();
    }
}
JAVA
    cat > "$request_map_app/src/test/java/com/example/RequestMapsTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@ApplicationTest
class RequestMapsTest {

    @Test
    void collectsAllNamesAndTheFirstValue(@LocalServerPort int port) {
        HttpResponse<String> response = get(port, "/parameters?colour=blue&size=large&colour=green&empty=");
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        assertEquals("colour=blue,empty=,size=large", response.body());
    }

    @Test
    void retainsRepeatedValuesWithoutSplittingCommas(@LocalServerPort int port) {
        HttpResponse<String> response = get(port, "/repeated?tag=red%2Cgreen&tag=blue&scope=all");
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        assertEquals("scope=all,tag=red,green/blue", response.body());
    }

    @Test
    void acceptsEmptyMapsWithoutAnIndividualRequiredValue(@LocalServerPort int port) {
        assertEquals("", get(port, "/parameters").body());
        assertEquals("", get(port, "/repeated").body());
        HttpResponse<String> ignored = get(port, "/ignored");
        assertEquals(HttpStatus.OK.value(), ignored.statusCode());
        assertEquals("", ignored.body());
    }

    @Test
    void ignoresTheJavaVariableName(@LocalServerPort int port) {
        assertEquals(get(port, "/parameters?colour=blue").body(), get(port, "/ignored?colour=blue").body());
    }

    @Test
    void keepsNamedMapsOnTheNamedValuePath(@LocalServerPort int port) {
        assertEquals(HttpStatus.BAD_REQUEST.value(), get(port, "/named?colour=blue").statusCode());
        assertEquals("colour=blue", new Parameters().named(Map.of("colour", "blue")));
    }

    @Test
    void reachesTheFixtureFailureAdvice(@LocalServerPort int port) {
        HttpResponse<String> response = get(port, "/failure?message=fixture");
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
    # Normalize generated fixture layout through the installed formatter before verifying it.
    prepare_maven request_map_format spring "$request_map_app" process-resources -Pformat
    git -C "$request_map_app" add --all
    run_maven request_map_verify spring "$request_map_app" clean verify
    expect_exit request_map_verify 'spring: whole-request maps pass installed-parent verification' 0
    expect_match request_map_verify 'spring: the real HTTP request-map cases all execute' \
        'Tests run: 6, Failures: 0, Errors: 0, Skipped: 0.*RequestMapsTest'
    expect_match request_map_verify 'spring: whole-request maps reach a complete consumer verdict' 'BUILD SUCCESS'

    request_scalar_app="$scratch/request-scalar-app"
    clone_tree "$request_map_app" "$request_scalar_app"
    cat > "$request_scalar_app/src/main/java/com/example/Parameters.java" <<'JAVA'
package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * An individual parameter that still owes an explicit request contract.
 */
@RestController
public final class Parameters {

    /**
     * Echoes the individual value.
     *
     * @param value the individual request value
     * @return the supplied value
     */
    @GetMapping("/parameters")
    public String all(@RequestParam String value) {
        return value;
    }
}
JAVA
    run_maven request_scalar_contract spring "$request_scalar_app" checkstyle:check
    expect_exit request_scalar_contract 'spring: individual request values still fail without a contract' 1
    expect_match request_scalar_contract 'spring: individual values still require a name' 'AirnessSpringWebParameterIsNamed'
    expect_match request_scalar_contract 'spring: individual values still require requiredness' \
        'AirnessSpringWebParameterDeclaresRequiredness'

    request_named_app="$scratch/request-named-app"
    clone_tree "$request_map_app" "$request_named_app"
    perl -0pi -e 's|@RequestParam\(name = "payload", required = true\)|@RequestParam(name = "payload")|' \
        "$request_named_app/src/main/java/com/example/Parameters.java"
    run_maven request_named_contract spring "$request_named_app" checkstyle:check
    expect_exit request_named_contract 'spring: named maps still require requiredness' 1
    expect_match request_named_contract 'spring: named-map requiredness keeps its diagnostic' \
        'AirnessSpringWebParameterDeclaresRequiredness'
}
