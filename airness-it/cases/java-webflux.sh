#!/usr/bin/env sh

# A Spring WebFlux and Thymeleaf application is swept like a servlet one: its startup test records the routes of
# its annotated handlers, and discovery requests every route that names one concrete address and no link reaches.
# The page a visitor lands on after signing out elsewhere is linked from no page, and it is swept in full, every
# pass in every engine, with no specification of the project's own. A route with a variable names no address the
# sweep could request, so it is reported until a link reaches a concrete one.
#
# Like the servlet case, the run stops at prepare-package and packs the artifact with the two goals package would
# have run, in the same invocation as the sweep.
build_java_webflux_application() {
    java_webflux="$1"
    build_spring_application "${java_webflux}" java_webflux_assets
    build_spring_context_test "${java_webflux}"
    python3 - "${java_webflux}/pom.xml" <<'PY'
import sys
from pathlib import Path
pom = Path(sys.argv[1])
text = pom.read_text()
servlet = "<artifactId>spring-boot-starter-webmvc</artifactId>"
security = """            <artifactId>spring-boot-starter-security</artifactId>
            <scope>compile</scope>
        </dependency>
"""
thymeleaf = """        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
            <scope>compile</scope>
        </dependency>
"""
assert servlet in text and security in text
text = text.replace(servlet, "<artifactId>spring-boot-starter-webflux</artifactId>", 1)
text = text.replace(security, security + thymeleaf, 1)
text = text.replace(
    "<airness.package.root>",
    "<airness.node.projects>.</airness.node.projects>\n        <airness.package.root>",
    1,
)
pom.write_text(text)
PY
    mkdir -p "${java_webflux}/src/main/resources/templates" "${java_webflux}/src/main/resources/static/css"
    cat > "${java_webflux}/src/main/java/com/example/Pages.java" <<'JAVA'
package com.example;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

/**
 * The pages the application renders.
 */
@Controller
public final class Pages {

    /**
     * Renders the home page.
     *
     * @return the view
     */
    @GetMapping("/")
    public Mono<String> home() {
        return Mono.just("home");
    }

    /**
     * Renders the page a visitor lands on once signed out, which no page links to.
     *
     * @return the view
     */
    @GetMapping("/account/signed-out")
    public Mono<String> signedOut() {
        return Mono.just("signed-out");
    }

    /**
     * Renders one order.
     *
     * @return the view
     */
    @GetMapping("/orders/{id}")
    public Mono<String> order() {
        return Mono.just("order");
    }
}
JAVA
    cat > "${java_webflux}/src/main/java/com/example/Security.java" <<'JAVA'
package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.web.server.WebFilter;

/**
 * What this application asks of a caller: nothing, since every page it renders is public, and the headers every
 * answer carries.
 */
@Configuration(proxyBeanMethods = false)
public final class Security {

    private static final String POLICY = "default-src 'self'; frame-ancestors 'none'";

    // Two years, in seconds, sent on every response: the reactive writer answers a secure request alone, which
    // behind a proxy that terminates TLS is never one, and a browser ignores the header over plain HTTP.
    private static final String TRANSPORT_SECURITY = "max-age=63072000";

    /**
     * Builds the chain every request is decided by.
     *
     * @param http the chain under construction
     * @return the built chain
     */
    @Bean
    SecurityWebFilterChain chain(ServerHttpSecurity http) {
        return http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .headers(
                headers -> headers.contentSecurityPolicy(policy -> policy.policyDirectives(POLICY))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            ).build();
    }

    /**
     * Declares transport security on every answer.
     *
     * @return the filter
     */
    @Bean
    WebFilter transportSecurity() {
        return (exchange, chain) -> {
            exchange.getResponse().getHeaders().set("Strict-Transport-Security", TRANSPORT_SECURITY);
            return chain.filter(exchange);
        };
    }
}
JAVA
    cat > "${java_webflux}/src/main/java/com/example/Failures.java" <<'JAVA'
package com.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The answer every unhandled failure of this module is turned into.
 */
@RestControllerAdvice
public final class Failures {

    /**
     * Answers a failure without naming what it was.
     *
     * @return the answer
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> failed() {
        return ResponseEntity.internalServerError().body("failed");
    }
}
JAVA
    printf 'server.shutdown=graceful\n' > "${java_webflux}/src/main/resources/application.properties"
    for page in home signed-out order; do
        case "${page}" in
            home) heading='Home'; body='<p>An application rendered by a reactive server.</p>' ;;
            signed-out) heading='Signed out'; body='<p>You signed out. Close this window or sign in again.</p>' ;;
            order) heading='Order'; body='<p>One order of the visitor.</p>' ;;
            *) printf 'No template is written for the page %s\n' "${page}" >&2; return 1 ;;
        esac
        cat > "${java_webflux}/src/main/resources/templates/${page}.html" <<HTML
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${heading}</title>
    <link rel="stylesheet" th:href="@{/css/site.css}">
</head>
<body>
    <main>
        <h1>${heading}</h1>
        ${body}
    </main>
</body>
</html>
HTML
    done
    cat > "${java_webflux}/src/main/resources/static/css/site.css" <<'CSS'
body {
    margin: 0;
    background: #fff;
    color: #111827;
    font-family: sans-serif;
    text-wrap: pretty;
}

main {
    display: flex;
    flex-direction: column;
    gap: 24px;
    max-width: 60ch;
    padding: 16px;
}

.links {
    margin: 0;
    padding: 0;
    list-style: none;
}

.links a {
    display: inline-flex;
    align-items: center;
    min-width: 44px;
    min-height: 44px;
}
CSS
    java_webflux_port="$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')"
    cat > "${java_webflux}/package.json" <<JSON
{
  "name": "java-webflux",
  "private": true,
  "scripts": {
    "serve": "\"\$JAVA_HOME/bin/java\" -jar \"\$AIRNESS_APPLICATION_ARTIFACT\" --server.port=\$PORT"
  },
  "devDependencies": {
    "@airness/node": "1.0.14-SNAPSHOT",
    "@playwright/test": "1.64.1",
    "@types/node": "26.6.6"
  },
  "airness": {
    "serve": "serve",
    "serverUrl": "http://127.0.0.1:${java_webflux_port}"
  }
}
JSON
    cp "${repository}/airness-it/fixtures/node-next/pnpm-workspace.yaml" "${java_webflux}/pnpm-workspace.yaml"
    install_airness_node "${java_webflux}"
    prepare_maven java_webflux_sync browser "${java_webflux}" --quiet airness:assets-sync
    prepare_maven java_webflux_format browser "${java_webflux}" --quiet process-resources -Pformat -Dairness.enforce=false
    rm -rf "${java_webflux}/target"
    git -C "${java_webflux}" add --all
    git -C "${java_webflux}" commit --quiet \
        --message 'test(it): serve a reactive Thymeleaf application to the browser sweep' \
        --message 'The page a visitor lands on after signing out is linked from no page.'
}

# The first argument names the run, and the rest are the profiles; the gates are those of the servlet case.
sweep_java_webflux() {
    sweep_run="$1"
    shift
    run_maven "${sweep_run}" browser "${java_webflux}" "$@" \
        clean prepare-package jar:jar spring-boot:repackage@repackage-application \
        airness:node-gate@node-gate-types airness:node-gate@node-gate-biome airness:node-gate@node-gate-eslint \
        airness:node-gate@node-gate-knip airness:node-gate@node-gate-css airness:node-gate@node-gate-e2e
}

run_java_webflux_cases() {
    build_java_webflux_application "${scratch}/java-webflux"
    java_webflux_sweep="${java_webflux}/node_modules/.airness/e2e/test-results/airness-sweep"

    sweep_java_webflux java_webflux_pattern -Pextended,delta
    expect_exit java_webflux_pattern 'java webflux: a route with a variable no link reaches fails the sweep' 1
    expect_file_match "${java_webflux}/target/airness/spring-context.evidence" \
        'java webflux: the startup test records the routes of the reactive handlers' \
        "$(printf '^html-route\t/account/signed-out\tPages#signedOut$')"
    expect_match java_webflux_pattern 'java webflux: the finding says a pattern names no address to request' \
        'Pages#order \[route-unswept\] the accessibility sweep never reached .?"/orders/\[id\].?", a pattern with a variable that names no concrete address'
    expect_no_match java_webflux_pattern 'java webflux: the page no link reaches is requested from the inventory' \
        'Pages#signedOut \[route-unswept\]'

    python3 - "${java_webflux}" <<'PY'
import sys
from pathlib import Path
home = Path(sys.argv[1]) / 'src/main/resources/templates/home.html'
text = home.read_text()
paragraph = '<p>An application rendered by a reactive server.</p>'
assert paragraph in text
home.write_text(text.replace(
    paragraph,
    paragraph + '\n        <ul class="links">\n            <li><a th:href="@{/orders/1}">The first order</a></li>\n        </ul>',
    1,
))
PY
    prepare_maven java_webflux_link_format browser "${java_webflux}" --quiet process-resources -Pformat \
        -Dairness.enforce=false
    rm -rf "${java_webflux}/target"
    git -C "${java_webflux}" add --all
    git -C "${java_webflux}" commit --quiet \
        --message 'test(it): link the first order of the reactive application' \
        --message 'A link reaches a concrete address of the order route, which the inventory names only as a pattern.'
    sweep_java_webflux java_webflux_full -Pextended
    expect_exit java_webflux_full \
        'java webflux: an unlinked page passes the complete sweep with no specification of the project' 0
    expect_file_match "${java_webflux_sweep}/coverage.txt" \
        'java webflux: the coverage summary names the page discovery requested from the inventory' \
        '^  seeded: /account/signed-out$'
    # Every pass and every engine visited the seeded page, as each visits a page a link reaches. A pass that visits
    # no page at all, as the dark-scheme pass does where no stylesheet answers a dark scheme, is not judged.
    if python3 - "${java_webflux_sweep}/report.json" <<'PY_PASSES'
import json
import sys
report = json.load(open(sys.argv[1]))
visited = {}
for entry in report['passes']:
    key = (entry['pass'], entry['engine'])
    visited[key] = visited.get(key, set()) | set(entry['locations'])
assert {engine for _, engine in visited} == {'chromium', 'firefox', 'webkit'}, sorted(visited)
missed = sorted(key for key, locations in visited.items() if locations and '/account/signed-out' not in locations)
assert not missed, missed
assert {'visitor': 'anonymous', 'route': '/account/signed-out'} in report['seeded'], report['seeded']
PY_PASSES
    then
        pass 'java webflux: every pass in every engine visits the page discovery requested from the inventory'
    else
        fail 'java webflux: every pass in every engine visits the page discovery requested from the inventory' \
            "${java_webflux_sweep}/report.json lists a pass or engine that did not visit /account/signed-out"
    fi
}
