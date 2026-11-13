#!/usr/bin/env sh

# A Spring MVC and Thymeleaf application is swept by the same installed browser harness a Node application is.
# Its frontend tooling project is the module itself: a package.json beside the pom, holding only the stylesheet
# the application serves, whose serve script launches the artifact this invocation packaged. The route
# inventory comes from the startup test's Spring evidence, so a page nothing links to, and a protected page
# answered by the sign-in page, are both reported rather than passed over. Once the module holds a sign-in
# setup, the sweep visits it signed in as well, and reaches the protected page itself; the account page links to
# an address that signs out on a GET, which the signed-in crawl stops at and names.
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
    mkdir -p "${java_web}/src/main/resources/templates" "${java_web}/src/main/resources/static/css" \
        "${java_web}/src/main/resources/static/img"
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
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * What this application asks of a caller.
 */
@Configuration(proxyBeanMethods = false)
public final class Security {

    private static final String POLICY = "default-src 'self'; frame-ancestors 'none'";

    // Two years, in seconds, sent on every response: the default writer answers a secure request alone, which
    // behind a proxy that terminates TLS is never one, and a browser ignores the header over plain HTTP.
    private static final long TRANSPORT_SECURITY = 63_072_000L;

    private static final String VISITOR = "visitor";

    /**
     * Finds the one visitor the sign-in setup signs in as, whose password is encoded when the application starts.
     *
     * @return the visitors
     */
    @Bean
    UserDetailsService visitors() {
        String encoded = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("fixture-visitor-password");
        return name -> {
            if (!VISITOR.equals(name)) {
                throw new UsernameNotFoundException(name);
            }
            return User.withUsername(name).password(encoded).roles("USER").build();
        };
    }

    /**
     * Builds the chain every request is decided by.
     *
     * @param http the chain under construction
     * @return the built chain
     */
    @Bean
    @SneakyThrows
    SecurityFilterChain chain(HttpSecurity http) {
        RequestMatcher exit = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/bye");
        return http
            .authorizeHttpRequests(
                registry -> registry
                    .requestMatchers("/").permitAll()
                    .requestMatchers("/about").permitAll()
                    .requestMatchers("/hidden").permitAll()
                    .requestMatchers("/login").permitAll()
                    .requestMatchers("/fragment/row").permitAll()
                    .requestMatchers("/css/site.css").permitAll()
                    .requestMatchers("/img/mark.svg").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            ).formLogin(form -> form.loginPage("/login")).logout(
                out -> out.logoutRequestMatcher(exit).logoutSuccessUrl("/")
            ).headers(
                headers -> headers
                    .contentSecurityPolicy(policy -> policy.policyDirectives(POLICY))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .httpStrictTransportSecurity(
                        transport -> transport.requestMatcher(AnyRequestMatcher.INSTANCE)
                            .maxAgeInSeconds(TRANSPORT_SECURITY)
                    )
            ).build();
    }
}
JAVA
    # The session cookie the sign-in form's token opens is marked Secure, which the sweep asks of every cookie
    # the production server sets.
    cat > "${java_web}/src/main/resources/application.properties" <<'PROPERTIES'
server.servlet.session.cookie.secure=true
server.shutdown=graceful
PROPERTIES
    for page in home about hidden account; do
        case "${page}" in
            home) heading='Home'; body='<img class="mark" src="/img/mark.svg" alt="The mark of the application" width="48" height="48">
        <ul class="links">
            <li><a th:href="@{/about}">About</a></li>
            <li><a th:href="@{/account}">Account</a></li>
        </ul>
        <div hx-get="/fragment/row" hx-trigger="load">Loading the row</div>' ;;
            about) heading='About'; body='<p>An application rendered on the server.</p>' ;;
            hidden) heading='Hidden'; body='<p>A page no other page links to.</p>' ;;
            account) heading='Account'; body='<p>The account of the signed-in visitor.</p>
        <ul class="links">
            <li><a th:href="@{/bye}">Sign out</a></li>
        </ul>' ;;
            *) printf 'No template is written for the page %s\n' "${page}" >&2; return 1 ;;
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
    <a class="skip-link" href="#content">Skip to the content</a>
    <main id="content">
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
    cat > "${java_web}/src/main/resources/static/img/mark.svg" <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><title>The mark of the application</title><circle cx="24" cy="24" r="20" fill="#1d4ed8"/></svg>
SVG
    cat > "${java_web}/src/main/resources/static/css/site.css" <<'CSS'
body {
    margin: 0;
    background: #fff;
    color: #111827;
    font-family: sans-serif;
}

.skip-link {
    position: absolute;
    top: 8px;
    left: 8px;
    display: inline-flex;
    align-items: center;
    min-height: 44px;
    padding: 8px 16px;
    background: #fff;
    color: #111827;
    border: 1px solid #111827;
    transform: translateY(-200%);
}

.skip-link:focus {
    outline: 3px solid #1d4ed8;
    outline-offset: 2px;
    transform: none;
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
    display: inline-flex;
    align-items: center;
    min-width: 44px;
    min-height: 44px;
    padding: 12px 0;
    transition:
        color 120ms ease-out,
        background-color 120ms ease-out;
}

form {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

input,
button {
    min-height: 44px;
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
    "@airness/node": "1.0.12-SNAPSHOT",
    "@playwright/test": "1.64.1",
    "@types/node": "26.6.6"
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
# The stylesheet's multi-value transition is laid out as Biome formats it; the Java parent's markup formatter,
# which the fixture's format run applies, must leave stylesheets to the node project that checks them.
# Each page opens with a skip link parked above the page until focus brings it in, which a pointer cannot reach
# at rest and which is not therefore obscured.
# The home page draws an image, whose reserved space the layout scan measures without writing a style attribute
# the page's policy would refuse.
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
    expect_match java_web_uncovered 'java web: the script gates do not apply to a project with no script' \
        'types.*not applicable|not applicable.*types'

    python3 - "${java_web}" <<'PY'
import re
import sys
from pathlib import Path
root = Path(sys.argv[1])
home = root / 'src/main/resources/templates/home.html'
# The links are added at the indentation the markup formatter gave their sibling.
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
setup = root / 'tests/e2e/sign-in.setup.ts'
setup.parent.mkdir(parents=True, exist_ok=True)
setup.write_text("""import { saveSweepSession } from '@airness/node/a11y'
import { expect, test } from '@playwright/test'

test('signs in as the visitor the sweep visits the application as', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('User name').fill('visitor')
  await page.getByLabel('Password').fill('fixture-visitor-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible()
  await saveSweepSession(page)
})
""")
# A TypeScript setup makes the module a script project, which compiles under the harness compiler options.
(root / 'tsconfig.json').write_text('''{
  "extends": "@airness/node/tsconfig.json",
  "include": ["**/*.ts", "**/*.tsx", "**/*.mts", "**/*.cts"],
  "exclude": ["node_modules", "dist", "target"]
}
''')
PY
    git -C "${java_web}" add --all
    git -C "${java_web}" commit --quiet \
        --message 'test(it): visit the Thymeleaf application signed in' \
        --message 'The unlinked pages gain links, and a sign-in setup hands the sweep a session.'
    sweep_java_web java_web_session_ended
    expect_exit java_web_session_ended 'java web: a page that ends the session on a GET fails the sweep' 1
    expect_match java_web_session_ended 'java web: the signed-in crawl names the page that ended its session' \
        'signed in: visiting /bye ended the signed-in session'

    python3 - "${java_web}" <<'PY'
import json
import sys
from pathlib import Path
manifest_file = Path(sys.argv[1]) / 'package.json'
manifest = json.loads(manifest_file.read_text())
manifest['airness']['accessibilitySkipRoutes'] = [
    {'pattern': '/bye', 'reason': 'the fixture ends the session on a GET to prove the sweep stops there'}
]
manifest_file.write_text(json.dumps(manifest, indent=2) + '\n')
PY
    git -C "${java_web}" add --all
    git -C "${java_web}" commit --quiet \
        --message 'test(it): cover every route of the Thymeleaf application' \
        --message 'The page that ends the session is declared out of scope with its reason.'
    # The declared port is held by another listener for the whole run, as a developer's server or another
    # verification would hold it: the sweep serves the application on a port of its own run instead.
    python3 -c 'import socket, sys, time; s = socket.socket(); s.bind(("127.0.0.1", int(sys.argv[1]))); s.listen(); time.sleep(3600)' \
        "${java_web_port}" &
    java_web_holder=$!
    sweep_java_web java_web_covered
    kill "${java_web_holder}" 2> /dev/null || true
    wait "${java_web_holder}" 2> /dev/null || true
    expect_exit java_web_covered \
        'java web: a CSS-only Thymeleaf application refusing inline style passes the shared sweep' 0
    expect_no_match java_web_covered 'java web: a server holding the declared port leaves the run on a port of its own' \
        'browser endpoint is not free|in use; stop the existing server'
    expect_no_match java_web_covered 'java web: Biome leaves the Thymeleaf templates to the template checks' \
        'useValidAnchor|noLabelWithoutControl'
    java_web_sweep="${java_web}/node_modules/.airness/e2e/test-results/airness-sweep"
    if python3 -c 'import json, sys; sys.exit("/fragment/row" not in json.load(open(sys.argv[1]))["fragmentRoutes"])' \
        "${java_web_sweep}/discovery.json" 2> /dev/null; then
        pass 'java web: the htmx fragment is recorded as fragment coverage, apart from swept pages'
    else
        fail 'java web: the htmx fragment is recorded as fragment coverage, apart from swept pages' \
            "${java_web_sweep}/discovery.json lists no /fragment/row among its fragment routes"
    fi
    # The signed-in visit reached the protected page, proved its session with it, and handed its passes the
    # protected page without the public pages, which look the same signed in.
    if python3 - "${java_web_sweep}/signed-in/discovery.json" 2> /dev/null <<'PY'
import json
import sys
report = json.load(open(sys.argv[1]))
assert '/account' in report['visitedRoutes'] and '/about' in report['visitedRoutes']
assert report['probeRoute'] == '/account'
assert '/about' not in report['locations']
PY
    then
        pass 'java web: the signed-in visit judges the protected page and leaves the unchanged public pages'
    else
        java_web_signed_in="$(cat "${java_web_sweep}/signed-in/discovery.json" 2> /dev/null \
            || printf 'no signed-in discovery')"
        fail 'java web: the signed-in visit judges the protected page and leaves the unchanged public pages' \
            "${java_web_signed_in}"
    fi
    run_service_wrapper_cases
}

# The disposable services a test wrapper starts, through the installed @airness/node: a command that reaches a
# PostgreSQL of its own run on a port Docker chose, a run stopped by SIGTERM, whose containers are removed
# all the same, and a run killed outright, whose guard removes them. The wrapper and its probe live in the
# scratch directory, outside any governed tree.
# The containers the services cases left behind: nothing once each run removed its own. A Docker that cannot be
# asked answers with its error instead, which fails the check rather than passing it as nothing left.
# The containers this suite's runs left, by the project name only this suite uses, so that another suite on
# the machine, such as one of another worktree, neither shows here nor is taken for a leftover of this one.
service_leftovers() {
    docker ps --all --filter "name=${AIRNESS_IT_SERVICES_PROJECT}-" --format '{{.Names}} {{.Status}}' 2>&1 || true
}

run_service_wrapper_cases() {
    service_wrapper="${scratch}/service-wrapper.mjs"
    service_probe="${scratch}/service-probe.mjs"
    service_holding="${scratch}/service-holding"
    AIRNESS_IT_SERVICES_PROJECT="airness-it-services-$(basename "${scratch}" | sed 's/^[.]airness-it-//' \
        | tr '[:upper:]' '[:lower:]')"
    export AIRNESS_IT_SERVICES_PROJECT
    cat > "${service_wrapper}" <<'JS'
import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'

const located = createRequire(`${process.env.AIRNESS_IT_FIXTURE}/package.json`).resolve('@airness/node/services')
const services = await import(pathToFileURL(located).href)
const secret = services.randomSecret()
process.exitCode = await services.withServices({
  project: process.env.AIRNESS_IT_SERVICES_PROJECT,
  containers: [
    {
      key: 'db',
      image: 'postgres:18-alpine',
      environment: { POSTGRES_PASSWORD: secret },
      ports: ['5432/tcp'],
      ready: ['pg_isready', '--host=127.0.0.1', '--username=postgres'],
    },
  ],
  environment: (running) => ({ IT_DATABASE_PORT: String(running.port('db', '5432/tcp')) }),
})
JS
    cat > "${service_probe}" <<'JS'
import { writeFileSync } from 'node:fs'
import { connect } from 'node:net'

const socket = connect(Number(process.env.IT_DATABASE_PORT), '127.0.0.1')
socket.once('error', () => process.exit(1))
socket.once('connect', () => {
  socket.destroy()
  if (process.argv[2] === undefined) {
    process.exit(0)
  }
  writeFileSync(process.argv[2], 'holding\n')
  setTimeout(() => process.exit(0), 600_000)
})
JS
    service_log="${scratch}/service-wrapper.log"
    if AIRNESS_IT_FIXTURE="${java_web}" "${node_home}/bin/node" "${service_wrapper}" -- \
        "${node_home}/bin/node" "${service_probe}" > "${service_log}" 2>&1; then
        pass 'services: the wrapped command reaches a disposable PostgreSQL on a port Docker chose'
    else
        service_output="$(tail -n 20 "${service_log}")"
        fail 'services: the wrapped command reaches a disposable PostgreSQL on a port Docker chose' \
            "${service_output}"
    fi
    service_left="$(service_leftovers)"
    if [ -z "${service_left}" ]; then
        pass 'services: the run removes the containers it started'
    else
        fail 'services: the run removes the containers it started' "${service_left}"
    fi

    rm -f "${service_holding}"
    AIRNESS_IT_FIXTURE="${java_web}" "${node_home}/bin/node" "${service_wrapper}" -- \
        "${node_home}/bin/node" "${service_probe}" "${service_holding}" > "${service_log}" 2>&1 &
    service_pid=$!
    service_waited=0
    while [ ! -f "${service_holding}" ] && [ "${service_waited}" -lt 180 ] && kill -0 "${service_pid}" 2> /dev/null; do
        sleep 1
        service_waited=$((service_waited + 1))
    done
    kill -TERM "${service_pid}" 2> /dev/null || true
    service_status=0
    wait "${service_pid}" || service_status=$?
    if [ "${service_status}" -eq 143 ] && [ -f "${service_holding}" ]; then
        pass 'services: a run stopped by SIGTERM passes the signal on and answers with it'
    else
        service_output="$(tail -n 20 "${service_log}")"
        fail 'services: a run stopped by SIGTERM passes the signal on and answers with it' \
            "exit ${service_status}: ${service_output}"
    fi
    service_left="$(service_leftovers)"
    if [ -z "${service_left}" ]; then
        pass 'services: a stopped run removes the containers it started'
    else
        fail 'services: a stopped run removes the containers it started' "${service_left}"
    fi

    # A wrapper ended by SIGKILL removes nothing itself, as one ended with the process group a package manager
    # runs it in does not, so it is the run's guard that removes the container, within moments and without
    # waiting for a later run. The probe the killed wrapper started outlives it and is ended here.
    rm -f "${service_holding}"
    AIRNESS_IT_FIXTURE="${java_web}" "${node_home}/bin/node" "${service_wrapper}" -- \
        "${node_home}/bin/node" "${service_probe}" "${service_holding}" > "${service_log}" 2>&1 &
    service_pid=$!
    service_waited=0
    while [ ! -f "${service_holding}" ] && [ "${service_waited}" -lt 180 ] && kill -0 "${service_pid}" 2> /dev/null; do
        sleep 1
        service_waited=$((service_waited + 1))
    done
    kill -KILL "${service_pid}" 2> /dev/null || true
    wait "${service_pid}" 2> /dev/null || true
    service_waited=0
    service_left="$(service_leftovers)"
    while [ -n "${service_left}" ] && [ "${service_waited}" -lt 20 ]; do
        sleep 1
        service_waited=$((service_waited + 1))
        service_left="$(service_leftovers)"
    done
    pkill -f "${service_probe}" 2> /dev/null || true
    if [ -f "${service_holding}" ] && [ -z "${service_left}" ]; then
        pass 'services: the guard removes the containers of a run killed outright'
    else
        service_output="$(tail -n 20 "${service_log}")"
        fail 'services: the guard removes the containers of a run killed outright' \
            "${service_left} ${service_output}"
    fi
}
