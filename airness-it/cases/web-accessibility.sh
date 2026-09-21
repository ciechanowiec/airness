#!/usr/bin/env sh

run_web_accessibility_cases() {
    audit_app="${scratch}/web-accessibility-app"
    mkdir -p "${audit_app}/src/main/java/com/example" "${audit_app}/src/test/java/com/example"
    cat > "${audit_app}/pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent-spring-boot</artifactId>
        <version>1.0.7-SNAPSHOT</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>web-accessibility-app</artifactId>
    <version>1.0.0</version>
    <properties><airness.package.root>com.example</airness.package.root></properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
POM
    cat > "${audit_app}/src/main/java/com/example/Application.java" <<'JAVA'
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A real application whose pages a test audits for accessibility.
 */
@SpringBootApplication(proxyBeanMethods = false)
public final class Application {
    /**
     * Starts the application.
     *
     * @param arguments what the command line carried
     */
    public static void main(String[] arguments) {
        SpringApplication.run(Application.class, arguments);
    }
}
JAVA
    # A test that writes the question itself, and asks for the failures alone. This is the shape all
    # three projects of the fleet had written separately, each unaware of the other two.
    cat > "${audit_app}/src/test/java/com/example/PageAccessibilityTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PageAccessibilityTest {

    private static final String RULES = "META-INF/resources/webjars/axe-core/4.13.0/axe.min.js";

    private static final String VIOLATIONS = """
        const done = arguments[arguments.length - 1];
        axe.run(document, {resultTypes: ['violations']})
           .then(report => done(report.violations));
        """;

    @Test
    void namesTheQuestionItAsks() {
        assertEquals(RULES.length(), RULES.length() + VIOLATIONS.length() - VIOLATIONS.length());
    }
}
JAVA
    git -C "${audit_app}" init --quiet
    git -C "${audit_app}" config user.name Fixture
    git -C "${audit_app}" config user.email fixture@example.invalid
    prepare_maven audit_assets spring "${audit_app}" --quiet airness:assets-sync

    run_maven audit_own_question spring "${audit_app}" airness:web-accessibility
    expect_exit audit_own_question 'spring: a project may not ask the accessibility rules its own question' 1
    expect_match audit_own_question 'spring: the refusal names what to run instead' 'AxeAudit[.]SCRIPT'
    expect_match audit_own_question 'spring: the refusal names the version it was pinned by' 'AxeLibrary[.]rules'
    expect_match audit_own_question 'spring: the refusal names the file and the line' 'PageAccessibilityTest[.]java: line'

    # The same test asking through the harness: the question and the version both come from one place.
    cat > "${audit_app}/src/test/java/com/example/PageAccessibilityTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.web.AxeAudit;
import eu.ciechanowiec.airness.web.AxeLibrary;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageAccessibilityTest {

    @Test
    void readsEveryVerdictTheRulesReport() {
        assertEquals(List.of(), AxeAudit.of(List.of()).problems(), AxeLibrary.version());
    }
}
JAVA
    run_maven audit_through_harness spring "${audit_app}" airness:web-accessibility
    expect_exit audit_through_harness 'spring: asking through the shipped audit breaks no rule' 0

    # The audit is a real dependency of the test JVM, so the consumer can run what it just declared.
    # Formatted first, as every case that reaches the lifecycle is: a fixture written by hand is not
    # written the way the formatter this harness ships writes it.
    prepare_maven audit_format spring "${audit_app}" process-resources -Pformat
    run_maven audit_runs spring "${audit_app}" test
    expect_exit audit_runs 'spring: the shipped audit is on the consumer test classpath' 0
}
