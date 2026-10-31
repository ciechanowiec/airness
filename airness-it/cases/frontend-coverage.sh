#!/usr/bin/env sh

# The frontend inventory and the web-interface requirement are both settled at validate, from the repository
# alone, so every case here stops there and no browser, server or Node toolchain is involved.
run_frontend_coverage_cases() {
    new_consumer frontend-unwired
    frontend_unwired="${consumer_directory}"
    mkdir -p "${frontend_unwired}/src/main/resources/static/js" "${frontend_unwired}/src/main/resources/static/css"
    printf 'export const ready = true\n' > "${frontend_unwired}/src/main/resources/static/js/browse.js"
    printf '.pill {\n    color: #000000;\n}\n' > "${frontend_unwired}/src/main/resources/static/css/theme.css"
    git -C "${frontend_unwired}" add --all
    run_maven frontend_unwired node "${frontend_unwired}" validate
    expect_exit frontend_unwired 'frontend: a Java module with authored resources and no manifest fails early' 1
    expect_match frontend_unwired 'frontend: the failure names the missing wiring' \
        'Frontend resources have no applicable Airness source checks; declare a frontend tooling project'
    expect_match frontend_unwired 'frontend: the failure names the unchecked script' \
        'static/js/browse[.]js lies in no declared node project'
    expect_match frontend_unwired 'frontend: the failure names the unchecked stylesheet' \
        'static/css/theme[.]css lies in no declared node project'

    new_consumer frontend-roots
    frontend_roots="${consumer_directory}"
    mkdir -p "${frontend_roots}/frontend/src" "${frontend_roots}/frontend/public" \
        "${frontend_roots}/src/main/resources/static/vendor" "${frontend_roots}/src/main/resources/static/generated"
    printf '{\n  "name": "frontend-roots",\n  "private": true\n}\n' > "${frontend_roots}/frontend/package.json"
    printf '.pill {\n    color: #000000;\n}\n' > "${frontend_roots}/frontend/src/site.css"
    printf '.pill {\n    color: #000000;\n}\n' > "${frontend_roots}/frontend/public/theme.css"
    printf '@color: #000000;\n' > "${frontend_roots}/frontend/src/legacy.less"
    printf 'var htmx = {};\n' > "${frontend_roots}/src/main/resources/static/vendor/htmx.min.js"
    printf 'export const bundled = true\n' > "${frontend_roots}/src/main/resources/static/generated/bundle.js"
    write_node_consumer "${frontend_roots}" 'frontend' ''
    git -C "${frontend_roots}" add --all
    run_maven frontend_roots node "${frontend_roots}" airness:assets-sync validate \
        -Dairness.generated.files=src/main/resources/static/generated/bundle.js
    expect_exit frontend_roots 'frontend: a manifest beside an unchecked resource does not cover it' 1
    expect_match frontend_roots 'frontend: a resource outside the checked roots is named with the roots' \
        'frontend/public/theme[.]css lies in the node project frontend but outside its checked source roots'
    expect_match frontend_roots 'frontend: a vendor directory and a minified name are no evidence of ownership' \
        'static/vendor/htmx[.]min[.]js lies in no declared node project.*take it from a dependency'
    expect_match frontend_roots 'frontend: a stylesheet language no gate reads is refused' \
        'frontend/src/legacy[.]less is written in less, which no Airness gate reads'
    expect_no_match frontend_roots 'frontend: a stylesheet under a checked root is covered' 'frontend/src/site[.]css'
    expect_no_match frontend_roots 'frontend: a declared generated file is not treated as authored' \
        'generated/bundle[.]js'

    new_consumer frontend-stylesheets
    frontend_stylesheets="${consumer_directory}"
    mkdir -p "${frontend_stylesheets}/src/main/resources/static/css"
    printf '.pill {\n    color: #000000;\n}\n' > "${frontend_stylesheets}/src/main/resources/static/css/theme.css"
    printf '{\n  "name": "frontend-stylesheets",\n  "private": true,\n  "airness": { "sourceRoots": ["src/main/resources/static"] }\n}\n' \
        > "${frontend_stylesheets}/package.json"
    write_node_consumer "${frontend_stylesheets}" '.' ''
    git -C "${frontend_stylesheets}" add --all
    run_maven frontend_stylesheets node "${frontend_stylesheets}" airness:assets-sync validate
    expect_exit frontend_stylesheets 'frontend: a Java module whose tooling project checks its static resources passes' 0
    expect_match frontend_stylesheets 'frontend: the inventory states its reach' '1 authored frontend file[(]s[)] found'

    frontend_web="${scratch}/frontend-web"
    build_spring_application "${frontend_web}" frontend_web_assets
    run_maven frontend_service node "${frontend_web}" validate
    expect_exit frontend_service 'web interface: a service that serves no markup passes validate' 0
    expect_match frontend_service 'web interface: a service without markup is legitimately not applicable' \
        'Web interface: not applicable, as it serves no markup'
    cat > "${frontend_web}/src/main/java/com/example/Pages.java" <<'JAVA'
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
}
JAVA
    git -C "${frontend_web}" add --all
    run_maven frontend_web_unwired node "${frontend_web}" validate
    expect_exit frontend_web_unwired 'web interface: an HTML application without a browser integration fails' 1
    expect_match frontend_web_unwired 'web interface: the failure names the evidence and the launch contract' \
        'serves HTML [(]src/main/java/com/example/Pages[.]java declares a @Controller[)].*AIRNESS_APPLICATION_ARTIFACT'
}
