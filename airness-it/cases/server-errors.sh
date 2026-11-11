#!/usr/bin/env sh

# Writes the test that drives the refusal. The second argument says whether the test declares the error
# it provokes (declared), provokes it without declaring it (undeclared), or declares it and provokes
# nothing (idle).
write_refusals_test() {
    refusals_assertion='assertFalse'
    refusals_import=''
    refusals_declaration=''
    refusals_call='this.refusals.refuse("closed"), "a closed account is refused"'
    case "$2" in
        undeclared)
            ;;
        *)
            refusals_import='
import eu.ciechanowiec.airness.spring.ExpectedServerError;'
            refusals_declaration='
    @ExpectedServerError(logger = Refusals.class, reason = "the refusal is what the test drives")'
            ;;
    esac
    if [ "$2" = idle ]; then
        refusals_assertion='assertEquals'
        refusals_call='Refusals.class, this.refusals.getClass(), "the component is the one asked for"'
    fi
    cat > "$1/src/test/java/com/example/RefusalsTest.java" <<JAVA
package com.example;

import static org.junit.jupiter.api.Assertions.${refusals_assertion};
${refusals_import}
import org.junit.jupiter.api.Test;

@ApplicationTest
class RefusalsTest {

    private final Refusals refusals;

    RefusalsTest(Refusals refusals) {
        this.refusals = refusals;
    }

    @Test${refusals_declaration}
    void refusesAClosedAccount() {
        ${refusals_assertion}(${refusals_call});
    }
}
JAVA
    git -C "$1" add --all
    git -C "$1" commit --quiet --message "test(it): drive the refusal with the $2 test"
}

run_server_error_cases() {
    # Built here rather than cloned from the spring cases, so that the two can run in separate lanes. The
    # application gains one component that logs at ERROR, the way an application reports a failure it has
    # turned into an answer, and the dependency its code names to do so.
    errors_app="${scratch}/server-errors-app"
    build_spring_application "${errors_app}" server_errors_assets
    build_spring_context_test "${errors_app}"
    perl -0pi -e 's|    <dependencies>\n|    <dependencies>\n        <dependency>\n            <groupId>org.slf4j</groupId>\n            <artifactId>slf4j-api</artifactId>\n            <scope>compile</scope>\n        </dependency>\n|' \
        "${errors_app}/pom.xml"
    cat > "${errors_app}/src/main/java/com/example/Refusals.java" <<'JAVA'
package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Refuses what the application cannot do, and reports each refusal.
 */
@Component
public final class Refusals {

    private static final Logger LOG = LoggerFactory.getLogger(Refusals.class);

    /**
     * Refuses one request.
     *
     * @param reason why it is refused
     * @return false, always
     */
    public boolean refuse(String reason) {
        LOG.error("refused: {}", reason);
        return false;
    }
}
JAVA
    git -C "${errors_app}" add --all
    git -C "${errors_app}" commit --quiet --message 'test(it): add a component that reports its refusals'

    errors_undeclared="${scratch}/server-errors-undeclared"
    clone_tree "${errors_app}" "${errors_undeclared}"
    write_refusals_test "${errors_undeclared}" undeclared
    run_maven server_errors_undeclared spring "${errors_undeclared}" clean test
    expect_exit server_errors_undeclared 'server errors: an error the test did not declare fails it' 1
    expect_match server_errors_undeclared 'server errors: the failure names the test that did not declare it' \
        'com.example.RefusalsTest.refusesAClosedAccount did not declare'
    expect_match server_errors_undeclared 'server errors: the failure names the logger and what it reported' \
        'com.example.Refusals: refused: closed'

    # A whole lifecycle, so the declaration is proven to pass every check a consumer meets, and not only
    # the test phase: the annotation is named from a test-scope artifact the parent supplies.
    errors_declared="${scratch}/server-errors-declared"
    clone_tree "${errors_app}" "${errors_declared}"
    write_refusals_test "${errors_declared}" declared
    run_maven server_errors_declared spring "${errors_declared}" clean verify
    expect_exit server_errors_declared 'server errors: a declared error passes a full verification' 0

    errors_idle="${scratch}/server-errors-idle"
    clone_tree "${errors_app}" "${errors_idle}"
    write_refusals_test "${errors_idle}" idle
    run_maven server_errors_idle spring "${errors_idle}" clean test
    expect_exit server_errors_idle 'server errors: a declaration nothing provoked fails the test' 1
    expect_match server_errors_idle 'server errors: the failure names the declaration nothing provoked' \
        'refusesAClosedAccount declares server errors it never provoked'
}
