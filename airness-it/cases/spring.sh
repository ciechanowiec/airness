#!/usr/bin/env sh

run_spring_cases() {
spring_app="$scratch/spring-app"
mkdir -p "$spring_app/src/main/java/com/example" "$spring_app/src/test/java/com/example"
cat > "$spring_app/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent-spring-boot</artifactId>
        <version>1.0.7-SNAPSHOT</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>spring-app</artifactId>
    <version>1.0.0</version>
    <properties>
        <airness.coverage.excluded.classes>com.example.Application</airness.coverage.excluded.classes>
        <airness.package.root>com.example</airness.package.root>
    </properties>
    <dependencies>
        <!--
            Declared because the module is repackaged, which is what the model goal asks of a deployed
            application: without the actuator it publishes no liveness probe, no readiness probe and no
            metrics, and this fixture is the one consumer here that is actually started. It is written
            ahead of the starter below because the recipe set sorts a dependency list and this one has to
            arrive already sorted.
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>repackage-application</id>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
POM
cat > "$spring_app/src/main/java/com/example/package-info.java" <<'JAVA'
/**
 * A Spring Boot application built against the harness.
 */
@NullMarked
package com.example;

import org.jspecify.annotations.NullMarked;
JAVA
cat > "$spring_app/src/main/java/com/example/Application.java" <<'JAVA'
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The entry point, which component scanning starts at.
 */
@SpringBootApplication(proxyBeanMethods = false)
public final class Application {

    /**
     * Starts the container.
     *
     * @param args the command line arguments
     */
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
JAVA
cat > "$spring_app/src/main/java/com/example/Greetings.java" <<'JAVA'
package com.example;

import org.springframework.stereotype.Component;

/**
 * The greeting the application answers with.
 */
@Component
public final class Greetings {

    private final String salutation;

    /**
     * Creates a greeting source.
     */
    public Greetings() {
        this.salutation = "hello";
    }

    /**
     * Greets one name.
     *
     * @param name the name to greet
     * @return the greeting
     */
    public String greet(String name) {
        return this.salutation + ", " + name;
    }
}
JAVA
cat > "$spring_app/src/test/java/com/example/GreetingsTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreetingsTest {

    @Test
    void greetsTheNameItIsGiven() {
        assertEquals("hello, world", new Greetings().greet("world"), "the salutation precedes the name");
    }
}
JAVA
cat > "$spring_app/AGENTS.md" <<'INSTRUCTIONS'
# Consumer instructions

Run the Maven verification before committing a change.
INSTRUCTIONS
git -C "$spring_app" init --quiet
git -C "$spring_app" config user.name Fixture
git -C "$spring_app" config user.email fixture@example.invalid
prepare_maven spring_assets spring "$spring_app" --quiet airness:assets-sync
# No format step, deliberately, and the fixture verifies without one. That is the guard on the Java 25
# recipe set: spring-boot-starter-test carries Mockito transitively, and while the upstream migration
# wired Mockito's agent into surefire, every Spring Boot project failed its first build until it had
# accepted that wiring into its own project file. A format step here would absorb the same thing
# silently if it ever came back.
git -C "$spring_app" add --all
git -C "$spring_app" commit --quiet \
    --message 'test(it): create a Spring Boot consumer fixture' \
    --message 'The fixture carries one bean and one entry point, so a consumer build has something to report on.'

run_maven spring_unit_missing spring "$spring_app" clean verify
expect_exit spring_unit_missing 'spring: unit tests alone do not prove application startup' 1
expect_match spring_unit_missing 'spring: the missing current-run startup evidence is explicit' \
    'Spring application context not started by this build'
run_maven spring_missing_report_only spring "$spring_app" \
    clean test airness:spring-context -Dairness.enforce=false
expect_exit spring_missing_report_only \
    'spring: missing startup evidence remains visible in report-only mode' 0
expect_match spring_missing_report_only \
    'spring: report-only still names the missing current-run evidence' \
    'Spring application context not started by this build'

# The marker is composed, so neither the test class nor the evidence goal has to name @SpringBootTest.
# Its classes member deliberately names the real production application: an explicit source that still
# performs the production component scan is valid, while the narrowed source case below is not.
cat > "$spring_app/src/test/java/com/example/ApplicationTest.java" <<'JAVA'
package com.example;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

/**
 * Marks a test that starts the production application.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public @interface ApplicationTest {
}
JAVA
cat > "$spring_app/src/test/java/com/example/ContextTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

@ApplicationTest
class ContextTest {

    private final Greetings greetings;

    ContextTest(Greetings greetings) {
        this.greetings = greetings;
    }

    @Test
    void startsTheProductionApplication() {
        assertEquals("hello, context", this.greetings.greet("context"), "the context supplies its component");
    }
}
JAVA
git -C "$spring_app" add --all
git -C "$spring_app" commit --quiet \
    --message 'test(it): prove the Spring application reaches ready'

run_maven spring_verify spring "$spring_app" clean verify
expect_exit spring_verify 'spring: a conforming Spring Boot application verifies' 0
expect_match spring_verify 'spring: the conforming lifecycle reaches a build verdict' 'BUILD SUCCESS'
# The model goal is bound rather than invoked, so this is where that binding is proven. Every other case
# runs a goal from the command line, which says nothing about the phase a consumer would meet it at, and
# this is the one consumer here that runs a whole lifecycle. It passing is the other half of the claim:
# the fixture declares the actuator a repackaged module has to, so the rule is satisfiable as well as real.
if grep -q 'airness-spring-model' "$(execution_log spring_verify)"; then
    pass 'spring: the model goal runs from its validate binding rather than from a command line'
else
    fail 'spring: the model goal never ran in a full consumer build'
fi
if grep -qx 'com.example.Application' "$spring_app/target/airness/spring-context.evidence"; then
    pass 'spring: a composed context test records the production application source'
else
    fail 'spring: the ready production application left no exact runtime evidence'
fi

# Reaching ready is not enough on its own. The source of that ready run must be the production
# application, so an explicit test-only configuration cannot stand in for the component scan.
spring_narrow="$scratch/spring-narrow"
clone_tree "$spring_app" "$spring_narrow"
cat > "$spring_narrow/src/test/java/com/example/NarrowConfiguration.java" <<'JAVA'
package com.example;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * A context deliberately narrower than the production application.
 */
@EnableAutoConfiguration
@TestConfiguration(proxyBeanMethods = false)
public class NarrowConfiguration {

    /**
     * Identifies this deliberately narrow configuration.
     *
     * @return the configuration identity
     */
    public String identity() {
        return "narrow";
    }
}
JAVA
cat > "$spring_narrow/src/test/java/com/example/ContextTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

class ContextTest {

    @Test
    void startsOnlyTheNarrowConfiguration() {
        SpringApplication application = new SpringApplication(NarrowConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run()) {
            assertTrue(context.isActive(), "the narrow context reached ready");
        }
    }
}
JAVA
run_maven spring_narrow spring "$spring_narrow" clean test airness:spring-context
expect_exit spring_narrow 'spring: a ready test-only context is not application evidence' 1
expect_match spring_narrow 'spring: narrowed evidence names the missing production source' \
    'contains no current run that reached ready with this production application'

# A role named in a guard is a string the engine compares with what a caller holds, and one no enum
# declares is granted to nobody. The build refuses it rather than waiting for the request that finds out.
spring_roles="$scratch/spring-roles"
clone_tree "$spring_app" "$spring_roles"
cat > "$spring_roles/src/main/java/com/example/Guarded.java" <<'JAVA'
package com.example;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * A service guarded by a role nothing declares.
 */
@Service
public class Guarded {

    /**
     * Answers only to a role nobody holds.
     *
     * @return the answer
     */
    @PreAuthorize("hasRole('NOBODY')")
    public String answer() {
        return "answer";
    }
}
JAVA
git -C "$spring_roles" add --all
run_maven spring_roles spring "$spring_roles" airness:spring-reactor
expect_exit spring_roles 'spring: a guard naming a role no enum declares fails the reactor goal' 1
expect_match spring_roles 'spring: the offence names the role and the missing enum' \
    "names the role 'NOBODY'.*enum implementing GrantedAuthority"

# The test profile satisfies a placeholder in every test, so a key it alone declares ships missing.
spring_placeholder="$scratch/spring-placeholder"
clone_tree "$spring_app" "$spring_placeholder"
mkdir -p "$spring_placeholder/src/main/resources"
cat > "$spring_placeholder/src/main/resources/application.yml" <<'YAML'
example:
  zone: UTC
YAML
cat > "$spring_placeholder/src/main/java/com/example/Clock.java" <<'JAVA'
package com.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A component reading one key the base file declares and one it does not.
 */
@Component
public class Clock {

    private final String zone;
    private final String greeting;

    /**
     * Reads the keys.
     *
     * @param zone     the zone the base file declares
     * @param greeting the greeting nothing declares
     */
    public Clock(@Value("${example.zone}") String zone, @Value("${example.greeting}") String greeting) {
        this.zone = zone;
        this.greeting = greeting;
    }
}
JAVA
git -C "$spring_placeholder" add --all
run_maven spring_placeholder spring "$spring_placeholder" airness:spring-configuration
expect_exit spring_placeholder \
    'spring: a placeholder the base configuration does not declare fails the configuration goal' 1
expect_match spring_placeholder 'spring: the offence names the undeclared key alone' \
    'Clock.java: line 21: the placeholder reads example.greeting'

# Both components compile and every source analyzer accepts them. The production context is the one
# authority on the collision, and its own diagnostic names the derived bean name and both definitions.
spring_collision="$scratch/spring-collision"
clone_tree "$spring_app" "$spring_collision"
mkdir -p "$spring_collision/src/main/java/com/example/one" \
    "$spring_collision/src/main/java/com/example/two"
for feature in one two; do
    cat > "$spring_collision/src/main/java/com/example/$feature/package-info.java" <<'JAVA'
/**
 * A feature contributing one component to the collision fixture.
 */
@NullMarked
package com.example.FEATURE;

import org.jspecify.annotations.NullMarked;
JAVA
    sed -i.bak "s/FEATURE/$feature/" \
        "$spring_collision/src/main/java/com/example/$feature/package-info.java"
    rm "$spring_collision/src/main/java/com/example/$feature/package-info.java.bak"
    cat > "$spring_collision/src/main/java/com/example/$feature/Numbering.java" <<'JAVA'
package com.example.FEATURE;

import org.springframework.stereotype.Component;

/**
 * A component whose simple name collides with the other feature's component.
 */
@Component
public final class Numbering {
}
JAVA
    sed -i.bak "s/FEATURE/$feature/" \
        "$spring_collision/src/main/java/com/example/$feature/Numbering.java"
    rm "$spring_collision/src/main/java/com/example/$feature/Numbering.java.bak"
done
run_maven spring_collision spring "$spring_collision" clean test
expect_exit spring_collision 'spring: colliding component names fail the real context test' 1
expect_match spring_collision 'spring: the real context names both colliding bean definitions' \
    "ConflictingBeanDefinitionException.*bean name 'numbering'"

spring_jar="$spring_app/target/spring-app-1.0.0.jar"
if unzip -l "$spring_jar" 2>/dev/null | grep -q 'jspecify'; then
    pass 'spring: the repackaged archive carries the annotations the container reads'
else
    fail 'spring: the repackaged archive omits the annotations the container reads'
fi
if unzip -l "$spring_jar" 2>/dev/null | grep -q 'airness-spring-evidence'; then
    fail 'spring: test-only context evidence leaked into the application archive'
else
    pass 'spring: context evidence stays out of the application archive'
fi
spring_run="$scratch/spring-app-run.log"
spring_started="$(date +%s)"
java -jar "$spring_jar" --server.port=0 --spring.main.banner-mode=off > "$spring_run" 2>&1 &
spring_pid=$!
spring_waited=0
while [ "$spring_waited" -lt 90 ]; do
    if grep -qE 'Started Application|Application run failed' "$spring_run" 2>/dev/null; then
        break
    fi
    sleep 1
    spring_waited=$((spring_waited + 1))
done
kill "$spring_pid" 2>/dev/null || true
wait "$spring_pid" 2>/dev/null || true
spring_seconds="$(($(date +%s) - spring_started))"
physical_executions=$((physical_executions + 1))
if grep -q 'Started Application' "$spring_run"; then
    record_timing "$spring_seconds" spring spring_application 0 application java -jar "$spring_jar"
    pass 'spring: the repackaged archive starts from a package-private main'
else
    record_timing "$spring_seconds" spring spring_application 1 application java -jar "$spring_jar"
    fail 'spring: the repackaged archive did not start'
    sed -n '1,220p' "$spring_run" >&2
fi

}
