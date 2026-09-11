#!/usr/bin/env sh

run_template_message_cases() {
    message_app="${scratch}/template-message-app"
    mkdir -p "${message_app}/src/main/java/com/example" "${message_app}/src/test/java/com/example" \
        "${message_app}/src/main/resources/templates"
    cat > "${message_app}/pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent-spring-boot</artifactId>
        <version>1.0.7-SNAPSHOT</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>template-message-app</artifactId>
    <version>1.0.0</version>
    <properties><airness.package.root>com.example</airness.package.root></properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
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
    cat > "${message_app}/src/main/java/com/example/Application.java" <<'JAVA'
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A real application whose template is intentionally not rendered by its test.
 */
@SpringBootApplication(proxyBeanMethods = false)
public final class Application {
    /**
     * Starts the application whose ready context supplies message evidence.
     *
     * @param arguments the startup arguments
     */
    static void main(String[] arguments) {
        SpringApplication.run(Application.class, arguments);
    }
}
JAVA
    cat > "${message_app}/src/main/java/com/example/package-info.java" <<'JAVA'
/**
 * A real application supplying template-message evidence.
 */
@NullMarked
package com.example;

import org.jspecify.annotations.NullMarked;
JAVA
    cat > "${message_app}/src/test/java/com/example/ApplicationTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ApplicationTest {
    private final ApplicationAvailability availability;

    ApplicationTest(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @Test
    void reachesReadyWithoutRenderingTheTemplate() {
        assertEquals(ReadinessState.ACCEPTING_TRAFFIC, this.availability.getReadinessState());
    }
}
JAVA
    cat > "${message_app}/src/main/resources/templates/page.html" <<'HTML'
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body><p th:text="#{browse.more}">More</p></body>
</html>
HTML
    printf 'browse.next=Next page\n' > "${message_app}/src/main/resources/messages.properties"
    git -C "${message_app}" init --quiet
    git -C "${message_app}" config user.name Fixture
    git -C "${message_app}" config user.email fixture@example.invalid
    prepare_maven message_assets spring "${message_app}" --quiet airness:assets-sync
    prepare_maven message_format spring "${message_app}" process-resources -Pformat
    git -C "${message_app}" add --all
    git -C "${message_app}" commit --quiet --message 'test(it): create a template message consumer' \
        --message 'The application starts but its template contains an undefined literal message key.'

    run_maven message_parity_alone spring "${message_app}" airness:message-parity
    expect_exit message_parity_alone 'spring: one message bundle has no parity disagreement' 0
    run_maven message_missing spring "${message_app}" clean prepare-package
    expect_exit message_missing 'spring: the bound message goal rejects an unrendered missing key' 1
    expect_match message_missing 'spring: missing messages name the template and key' 'templates/page[.]html.*browse[.]more.*missing from base'

    printf 'browse.next=Next page\nbrowse.more=More\n' > "${message_app}/src/main/resources/messages.properties"
    run_maven message_supplied spring "${message_app}" prepare-package
    expect_exit message_supplied 'spring: a current success supersedes stale missing-message evidence' 0
    expect_match message_supplied 'spring: the ordinary source was actually checked' '1 checked lookup[(]s[)], 0 unassessed'

    printf 'browse.next=Next page\n' > "${message_app}/src/main/resources/messages.properties"
    printf 'spring.messages.use-code-as-default-message=true\n' > "${message_app}/src/main/resources/application.properties"
    run_maven message_default_code spring "${message_app}" prepare-package
    expect_exit message_default_code 'spring: code-as-default cannot hide an undefined message key' 1
    run_maven message_report_only spring "${message_app}" test airness:spring-template-messages -Dairness.enforce=false
    expect_exit message_report_only 'spring: message report-only preserves the finding without enforcement' 0
    expect_match message_report_only 'spring: message report-only still names the missing key' 'browse[.]more.*missing from base'

    printf 'spring.messages.basename=labels\n' > "${message_app}/src/main/resources/application.properties"
    printf 'browse.more=Unrelated\n' > "${message_app}/src/main/resources/messages.properties"
    printf 'browse.next=Next page\n' > "${message_app}/src/main/resources/labels.properties"
    run_maven message_selected_bundle spring "${message_app}" prepare-package
    expect_exit message_selected_bundle 'spring: an unrelated bundle cannot satisfy the selected source' 1
    printf 'browse.more=Configured label\n' > "${message_app}/src/main/resources/labels.properties"
    run_maven message_configured_bundle spring "${message_app}" prepare-package
    expect_exit message_configured_bundle 'spring: the configured basename supplies the key' 0

    printf 'spring.thymeleaf.prefix=file:/outside-scope/\n' > "${message_app}/src/main/resources/application.properties"
    run_maven message_unassessed spring "${message_app}" prepare-package
    expect_exit message_unassessed 'spring: unsupported resolution is not guessed to be missing' 0
    expect_match message_unassessed 'spring: unsupported resolution is explicitly reported' 'Unassessed template messages.*classpath HTML'
}
