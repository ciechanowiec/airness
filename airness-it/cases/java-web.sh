#!/usr/bin/env sh

# A Spring MVC and Thymeleaf application is swept by the same installed browser harness a Node application is.
# Its frontend tooling project is the module itself: a package.json beside the pom, holding only the stylesheet
# the application serves, whose serve script launches the artifact this invocation packaged. The route
# inventory comes from the startup test's Spring evidence, so a page nothing links to, and a protected page
# answered by the sign-in page, are both reported rather than passed over.
#
# Extended binds Qodana and the vulnerability scan to package, which this case does not need, so it stops at
# prepare-package and packs the artifact with the two goals package would have run. Both are in the same
# invocation as the sweep, which is what lets the e2e gate hand the sweep the artifact this build packaged.
build_java_web_application() {
    java_web="$1"
    build_spring_application "${java_web}" java_web_assets
    build_spring_context_test "${java_web}"
    python3 - "${java_web}/pom.xml" <<'PY'
import sys
from pathlib import Path
pom = Path(sys.argv[1])
text = pom.read_text()
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
assert security in text
text = text.replace(security, security + thymeleaf, 1)
text = text.replace(
    "<airness.package.root>",
    "<airness.node.projects>.</airness.node.projects>\n        <airness.package.root>",
    1,
)
pom.write_text(text)
PY
    mkdir -p "${java_web}/src/main/resources/templates" "${java_web}/src/main/resources/static/css"
    cat > "${java_web}/src/main/java/com/example/Pages.java" <<'JAVA'
package com.example;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
    public String home() {
        return "home";
    }

    /**
     * Renders the page about the application.
     *
     * @return the view
     */
    @GetMapping("/about")
    public String about() {
        return "about";
    }

    /**
     * Renders a page the home page may not link to.
     *
     * @return the view
     */
    @GetMapping("/hidden")
    public String hidden() {
        return "hidden";
    }

    /**
     * Renders the account of the signed-in visitor.
     *
     * @return the view
     */
    @GetMapping("/account")
    public String account() {
        return "account";
    }

    /**
     * Renders the sign-in page.
     *
     * @return the view
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Renders the row the home page asks for through htmx.
     *
     * @return the view
     */
    @GetMapping("/fragment/row")
    public String row() {
        return "row";
    }
}
JAVA
    cat > "${java_web}/src/main/java/com/example/Status.java" <<'JAVA'
package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The state the application reports to machines.
 */
@RestController
public final class Status {

    /**
     * Answers with the state.
     *
     * @return the state
     */
    @GetMapping("/api/status")
    public String status() {
        return "{}";
    }
}
JAVA
    cat > "${java_web}/src/main/java/com/example/Failures.java" <<'JAVA'
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
    cat > "${java_web}/src/main/java/com/example/Security.java" <<'JAVA'
package com.example;

import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * What this application asks of a caller.
 */
@Configuration(proxyBeanMethods = false)
public final class Security {

    private static final String POLICY = "default-src 'self'; frame-ancestors 'none'";

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
                    .requestMatchers("/").permitAll()
                    .requestMatchers("/about").permitAll()
                    .requestMatchers("/hidden").permitAll()
                    .requestMatchers("/login").permitAll()
                    .requestMatchers("/fragment/row").permitAll()
                    .requestMatchers("/css/site.css").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            ).formLogin(form -> form.loginPage("/login")).headers(
                headers -> headers
                    .contentSecurityPolicy(policy -> policy.policyDirectives(POLICY))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            ).build();
    }
}
JAVA
    for page in home about hidden account; do
        case "${page}" in
            home) heading='Home'; body='<ul class="links">
            <li><a th:href="@{/about}">About</a></li>
            <li><a th:href="@{/account}">Account</a></li>
        </ul>
        <div hx-get="/fragment/row" hx-trigger="load">Loading the row</div>' ;;
            about) heading='About'; body='<p>An application rendered on the server.</p>' ;;
            hidden) heading='Hidden'; body='<p>A page no other page links to.</p>' ;;
            account) heading='Account'; body='<p>The account of the signed-in visitor.</p>' ;;
        esac
        cat > "${java_web}/src/main/resources/templates/${page}.html" <<HTML
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
    cat > "${java_web}/src/main/resources/templates/login.html" <<'HTML'
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Sign in</title>
    <link rel="stylesheet" th:href="@{/css/site.css}">
</head>
<body>
    <main>
        <h1>Sign in</h1>
        <form method="post" th:action="@{/login}">
            <label for="username">User name</label>
            <input id="username" name="username" type="text" autocomplete="username">
            <label for="password">Password</label>
            <input id="password" name="password" type="password" autocomplete="current-password">
            <button type="submit">Sign in</button>
        </form>
    </main>
</body>
</html>
HTML
    cat > "${java_web}/src/main/resources/templates/row.html" <<'HTML'
<p>A row the home page asks for</p>
HTML
    cat > "${java_web}/src/main/resources/static/css/site.css" <<'CSS'
body {
    margin: 0;
    background: #fff;
    color: #111827;
    font-family: sans-serif;
}

main {
    display: flex;
    flex-direction: column;
    gap: 24px;
    padding: 16px;
}

.links {
    display: flex;
    flex-direction: column;
    gap: 16px;
    margin: 0;
    padding: 0;
    list-style: none;
}

.links a {
    display: inline-block;
    padding: 12px 0;
}

form {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

input,
button {
    min-height: 32px;
}
CSS
    java_web_port="$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')"
    cat > "${java_web}/package.json" <<JSON
{
  "name": "java-web",
  "private": true,
  "scripts": {
    "serve": "\"\$JAVA_HOME/bin/java\" -jar \"\$AIRNESS_APPLICATION_ARTIFACT\" --server.port=\$PORT"
  },
  "devDependencies": {
    "@airness/node": "1.0.9-SNAPSHOT",
    "@playwright/test": "1.64.1"
  },
  "airness": {
    "serve": "serve",
    "serverUrl": "http://127.0.0.1:${java_web_port}"
  }
}
JSON
    cp "${repository}/airness-it/fixtures/node-next/pnpm-workspace.yaml" "${java_web}/pnpm-workspace.yaml"
    install_airness_node "${java_web}"
    prepare_maven java_web_sync browser "${java_web}" --quiet airness:assets-sync
    prepare_maven java_web_format browser "${java_web}" --quiet process-resources -Pformat -Dairness.enforce=false
    rm -rf "${java_web}/target"
    git -C "${java_web}" add --all
    git -C "${java_web}" commit --quiet \
        --message 'test(it): serve a Thymeleaf application to the browser sweep' \
        --message 'The tooling project holds only the stylesheet and launches the packaged artifact.'
}

# Packs the artifact inside the sweep's own invocation, as package would, and then runs e2e with the static
# node gates a CSS-only tooling project answers. Biome runs over the module root, where its source roots reach
# the Thymeleaf templates, whose th:href anchors it must leave to the template checks and the sweep.
sweep_java_web() {
    run_maven "$1" browser "${java_web}" -Pextended,delta \
        clean prepare-package jar:jar spring-boot:repackage@repackage-application \
        airness:node-gate@node-gate-types airness:node-gate@node-gate-biome airness:node-gate@node-gate-eslint \
        airness:node-gate@node-gate-knip airness:node-gate@node-gate-css airness:node-gate@node-gate-e2e
}

run_java_web_cases() {
    build_java_web_application "${scratch}/java-web"

    sweep_java_web java_web_uncovered
    expect_exit java_web_uncovered 'java web: uncovered inventory routes fail the sweep' 1
    expect_match java_web_uncovered 'java web: the sweep starts the packaged Java application' '\[airness-sweep\]'
    expect_match java_web_uncovered 'java web: a route no page links to is reported from the inventory' \
        'Pages#hidden \[route-unswept\]'
    expect_match java_web_uncovered 'java web: a protected route answered by the sign-in page is not covered' \
        'Pages#account \[route-unswept\]'
    expect_no_match java_web_uncovered 'java web: a JSON endpoint is not an HTML route' 'route-unswept.*/api/status'
    expect_no_match java_web_uncovered 'java web: an htmx fragment the home page requests is covered' \
        'route-unswept.*/fragment/row'

    python3 - "${java_web}" <<'PY'
import json
import re
import sys
from pathlib import Path
root = Path(sys.argv[1])
home = root / 'src/main/resources/templates/home.html'
# The links are added at the indentation the markup formatter gave their sibling. The sign-in page loses
# its only crawled path once the protected page is out of scope, so it gains a link of its own.
home.write_text(re.sub(
    r'^( *)(<li><a th:href="@\{/about\}">About</a></li>)$',
    lambda line: line[0] + ''.join(
        '\n' + line[1] + link
        for link in ('<li><a th:href="@{/hidden}">Hidden</a></li>', '<li><a th:href="@{/login}">Sign in</a></li>')
    ),
    home.read_text(),
    count=1,
    flags=re.MULTILINE,
))
manifest_file = root / 'package.json'
manifest = json.loads(manifest_file.read_text())
manifest['airness']['accessibilitySkipRoutes'] = [
    {'pattern': '/account', 'reason': 'signing in needs credentials this fixture does not provision'}
]
manifest_file.write_text(json.dumps(manifest, indent=2) + '\n')
PY
    git -C "${java_web}" add --all
    git -C "${java_web}" commit --quiet \
        --message 'test(it): cover every route of the Thymeleaf application' \
        --message 'The unlinked pages gain links and the protected page is declared out of scope with its reason.'
    sweep_java_web java_web_covered
    expect_exit java_web_covered \
        'java web: a CSS-only Thymeleaf application refusing inline style passes the shared sweep' 0
    expect_no_match java_web_covered 'java web: Biome leaves the Thymeleaf templates to the template checks' \
        'useValidAnchor|noLabelWithoutControl'
    expect_match java_web_covered 'java web: the script gates do not apply to a project with no script' \
        'types.*not applicable|not applicable.*types'
    java_web_discovery="${java_web}/node_modules/.airness/e2e/test-results/airness-sweep/discovery.json"
    if python3 -c 'import json, sys; sys.exit("/fragment/row" not in json.load(open(sys.argv[1]))["fragmentRoutes"])' \
        "${java_web_discovery}" 2> /dev/null; then
        pass 'java web: the htmx fragment is recorded as fragment coverage, apart from swept pages'
    else
        fail 'java web: the htmx fragment is recorded as fragment coverage, apart from swept pages' \
            "${java_web_discovery} lists no /fragment/row among its fragment routes"
    fi
}
