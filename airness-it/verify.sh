#!/usr/bin/env sh
# Verifies published inheritance from isolated consumer repositories. No Airness tracked file is edited.
set -eu

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT INT TERM
failures=0

new_consumer() {
    name="$1"
    directory="$scratch/$name"
    mkdir -p "$directory/src/main/java/com/example" "$directory/src/test/java/com/example"
    cat > "$directory/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>consumer</artifactId>
  <version>9.4.2</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.apache.commons</groupId>
      <artifactId>commons-lang3</artifactId>
      <version>3.20.0</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>2.0.17</version>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <version>3.27.7</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <version>5.23.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <profiles>
    <profile>
      <id>drift-pinned-asset</id>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-antrun-plugin</artifactId>
            <version>3.2.0</version>
            <executions>
              <execution>
                <id>drift-pinned-asset</id>
                <phase>process-resources</phase>
                <goals>
                  <goal>run</goal>
                </goals>
                <configuration>
                  <target>
                    <echo file="${project.basedir}/.gitattributes" append="true">drift</echo>
                  </target>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
</project>
POM
    cat > "$directory/AGENTS.md" <<'INSTRUCTIONS'
# Consumer instructions

Run the Maven verification before committing a change.
INSTRUCTIONS
    cat > "$directory/src/main/java/com/example/Example.java" <<'JAVA'
package com.example;

/** A small consumer fixture. */
public final class Example {

    private Example() {
    }

    /**
     * Supplies a stable value.
     *
     * @return the value
     */
    public static int value() {
        return 1;
    }
}
JAVA
    cat > "$directory/src/main/java/com/example/Coordinate.java" <<'JAVA'
package com.example;

/**
 * A Java 25 syntax fixture for the inherited formatter and import sorter.
 *
 * @param x horizontal coordinate
 * @param y vertical coordinate
 */
public record Coordinate(int x, int y) {
}
JAVA
    cat > "$directory/src/main/java/com/example/RewriteLogging.java" <<'JAVA'
package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exercises logging modernization inherited from Airness. */
final class RewriteLogging {

    private static final Logger LOGGER = LoggerFactory.getLogger(RewriteLogging.class);

    private RewriteLogging() {
    }

    /**
     * Logs a value.
     *
     * @param value value to log
     */
    static void log(String value) {
        LOGGER.info("value " + value);
    }
}
JAVA
    cat > "$directory/src/main/java/com/example/RewriteApache.java" <<'JAVA'
package com.example;

import org.apache.commons.lang3.StringUtils;

/** Exercises Apache Commons modernization inherited from Airness. */
final class RewriteApache {

    private RewriteApache() {
    }

    /**
     * Checks whether text is blank.
     *
     * @param value text to check
     * @return whether the text is blank
     */
    static boolean blank(String value) {
        return StringUtils.isBlank(value);
    }
}
JAVA
    cat > "$directory/src/test/java/com/example/RewriteTestingTest.java" <<'JAVA'
package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/** Exercises test-framework cleanup inherited from Airness. */
class RewriteTestingTest {

    @Test
    void cleansAssertionsAndVerification() {
        assertThat("").hasSize(0);
        Runnable dependency = mock(Runnable.class);
        dependency.run();
        verify(dependency, times(1)).run();
    }
}
JAVA
    git -C "$directory" init --quiet
    git -C "$directory" config user.name Fixture
    git -C "$directory" config user.email fixture@example.invalid
    (cd "$directory" && mvn --quiet clean package -Dairness.enforce=false >/dev/null)
    (cd "$directory" && mvn --quiet process-resources -Pformat -Dairness.enforce=false >/dev/null)
    git -C "$directory" add --all
    git -C "$directory" commit --quiet --message 'test(it): create an isolated consumer fixture'
    printf '%s\n' "$directory"
}

run_case() {
    label="$1"
    expected="$2"
    pattern="$3"
    directory="$4"
    shift 4
    log="$scratch/$(printf '%s' "$label" | tr ' /:' '____').log"
    set +e
    (cd "$directory" && mvn --batch-mode --no-transfer-progress "$@") >"$log" 2>&1
    status=$?
    set -e
    matched=0
    if grep -Eq "$pattern" "$log"; then
        matched=1
    fi
    if [ "$status" -eq "$expected" ] && [ "$matched" -eq 1 ]; then
        printf 'ok       %s\n' "$label"
    else
        printf 'FAILED   %s (exit %s, expected %s, pattern /%s/: %s)\n' \
            "$label" "$status" "$expected" "$pattern" "$matched" >&2
        sed -n '1,220p' "$log" >&2
        failures=$((failures + 1))
    fi
}

consumer="$(new_consumer consumer)"

if grep -Fq 'LOGGER.info("value {}", value);' "$consumer/src/main/java/com/example/RewriteLogging.java"; then
    echo 'ok       rewrite: SLF4J best practices reach consumers'
else
    echo 'FAILED   rewrite: SLF4J best practices did not reach consumers' >&2
    failures=$((failures + 1))
fi

if grep -Fq 'StringUtils.isBlank' "$consumer/src/main/java/com/example/RewriteApache.java"; then
    echo 'FAILED   rewrite: Apache Commons cleanup did not reach consumers' >&2
    failures=$((failures + 1))
else
    echo 'ok       rewrite: Apache Commons cleanup reaches consumers'
fi

if grep -Fq 'assertThat("").isEmpty();' "$consumer/src/test/java/com/example/RewriteTestingTest.java" \
    && grep -Fq 'verify(dependency).run();' "$consumer/src/test/java/com/example/RewriteTestingTest.java"; then
    echo 'ok       rewrite: AssertJ and Mockito cleanup reaches consumers'
else
    echo 'FAILED   rewrite: test-framework cleanup did not reach consumers' >&2
    failures=$((failures + 1))
fi

# The two agent files are a fixed contract. Legacy properties must not be able to redirect or disable
# either check, and the package lifecycle must restore the exact Claude entry.
expected_claude="$scratch/expected-claude"
printf '@AGENTS.md\n' > "$expected_claude"
if cmp -s "$expected_claude" "$consumer/CLAUDE.md"; then
    echo 'ok       instructions: package writes the exact Claude entry'
else
    echo 'FAILED   instructions: package wrote the wrong Claude entry' >&2
    failures=$((failures + 1))
fi
mv "$consumer/AGENTS.md" "$scratch/consumer-AGENTS.md"
run_case 'instructions: AGENTS is mandatory' 1 'mandatory AGENTS.md file is missing' \
    "$consumer" airness:entry-files -Dairness.instruction.file=NONE -Dairness.entry.files=NONE
mv "$scratch/consumer-AGENTS.md" "$consumer/AGENTS.md"
printf '@AGENTS.md\nRun Maven first.\n' > "$consumer/CLAUDE.md"
run_case 'instructions: CLAUDE has exact content' 1 'must contain exactly @AGENTS.md' \
    "$consumer" airness:entry-files -Dairness.instruction.file=NONE -Dairness.entry.files=NONE
(cd "$consumer" && mvn --quiet package -Dairness.enforce=false >/dev/null)
if cmp -s "$expected_claude" "$consumer/CLAUDE.md"; then
    echo 'ok       assets: package restores drifted pinned content'
else
    echo 'FAILED   assets: package did not restore drifted pinned content' >&2
    failures=$((failures + 1))
fi
run_case 'assets: later pinned change fails tree verification' 1 \
    'Build plugins changed committable files|working tree content differs' \
    "$consumer" -Pdrift-pinned-asset airness:assets-sync airness:tree-snapshot \
    antrun:run@drift-pinned-asset airness:tree-verify

# The child's version is deliberately unrelated to Airness. skipTests must compile and package while
# bypassing every inherited check and ordinary test.
run_case 'skip: independent child version packages' 0 'BUILD SUCCESS' "$consumer" clean package -DskipTests
if grep -Eq 'rule\(s\) reported findings|Missing current-build JaCoCo evidence|Java sources that do not match' \
    "$scratch/skip__independent_child_version_packages.log"; then
    echo 'FAILED   skip: harness findings were shown' >&2
    failures=$((failures + 1))
else
    echo 'ok       skip: no harness findings are shown'
fi

# A real JUnit failure is inherited without child dependency declarations. Default enforcement fails;
# report-only shows the same failure and continues through later harness checks.
cat > "$consumer/src/test/java/com/example/ExampleTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Exercises report-only test handling. */
class ExampleTest {

    @Test
    void reportsFailure() {
        assertEquals(2, Example.value());
    }
}
JAVA
run_case 'default: test finding fails' 1 'Failures: 1|BUILD FAILURE' "$consumer" clean package
run_case 'report-only: test finding is visible' 0 'Failures: 1' "$consumer" clean package -Dairness.enforce=false
if grep -Eq 'Missing current-build JaCoCo evidence|PMD Failure|Banned typography|Java sources that do not match' \
    "$scratch/report-only__test_finding_is_visible.log"; then
    echo 'ok       report-only: later harness checks also run'
else
    echo 'FAILED   report-only: later harness checks did not run' >&2
    failures=$((failures + 1))
fi

# A suppression still needs a real explanation. Exercise the custom PMD rule directly so the failing
# JUnit fixture above cannot stop the lifecycle before PMD runs.
cat > "$consumer/src/main/java/com/example/BlankJustification.java" <<'JAVA'
package com.example;

import eu.ciechanowiec.airness.Justification;

/** Exercises justification validation. */
@Justification(" ")
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class BlankJustification {

    private BlankJustification() {
    }
}
JAVA
run_case 'default: blank justification fails' 1 'JustificationNeedsText' "$consumer" pmd:check
run_case 'report-only: blank justification is visible' 0 'JustificationNeedsText' \
    "$consumer" pmd:check -Dairness.enforce=false
rm "$consumer/src/main/java/com/example/BlankJustification.java"

# Compilation failures are not findings and remain fatal in report-only mode.
cat > "$consumer/src/main/java/com/example/Broken.java" <<'JAVA'
package com.example;

final class Broken {
    this is not Java
}
JAVA
run_case 'report-only: compilation remains fatal' 1 'COMPILATION ERROR|BUILD FAILURE' \
    "$consumer" clean package -Dairness.enforce=false
rm "$consumer/src/main/java/com/example/Broken.java"

# A dependency built in the same reactor has no reason to exist in an external registry yet. The
# freshness goal must recognize its resolved coordinates without requesting Maven Central metadata.
reactor="$scratch/reactor"
mkdir -p "$reactor/library" "$reactor/application"
cat > "$reactor/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>reactor</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <modules>
    <module>library</module>
    <module>application</module>
  </modules>
</project>
POM
cat > "$reactor/library/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>reactor</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>library</artifactId>
</project>
POM
cat > "$reactor/application/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>reactor</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>application</artifactId>
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>library</artifactId>
      <version>${project.version}</version>
    </dependency>
  </dependencies>
</project>
POM
run_case 'freshness: same-reactor dependency needs no registry metadata' 0 'BUILD SUCCESS' \
    "$reactor" airness:dependency-freshness

# Production code without tests cannot deactivate coverage merely by omitting src/test/java.
untested="$(new_consumer untested)"
rm -rf "$untested/src/test"
(cd "$untested" && mvn --quiet clean compile -DskipTests >/dev/null)
run_case 'coverage: no-test production module fails' 1 'Missing current-build JaCoCo evidence' \
    "$untested" airness:coverage-evidence
run_case 'coverage: no-test finding reports without failing' 0 'Missing current-build JaCoCo evidence' \
    "$untested" clean package -Dairness.enforce=false

# Full-history protection is a Maven goal, not a hook, and rejects a shallow consumer repository.
shallow="$scratch/shallow"
git clone --quiet --depth 1 "file://$untested" "$shallow"
run_case 'history: shallow clone is rejected by Maven' 1 'This is a shallow clone' \
    "$shallow" airness:require-full-history

# Published assets must contain neither documentation nor Git-hook material.
assets="$HOME/.m2/repository/eu/ciechanowiec/airness-assets/1.0.0-SNAPSHOT/airness-assets-1.0.0-SNAPSHOT.jar"
listing="$scratch/assets.txt"
jar tf "$assets" > "$listing"
if grep -Eq '(^|/)(\.vale|\.docs|docinfo|README|githooks|lint-docs)' "$listing"; then
    echo 'FAILED   assets: documentation or hooks leaked into the published jar' >&2
    failures=$((failures + 1))
else
    echo 'ok       assets: published jar contains no documentation or hooks'
fi

if [ "$failures" -ne 0 ]; then
    printf '\n%s integration case(s) failed.\n' "$failures" >&2
    exit 1
fi
