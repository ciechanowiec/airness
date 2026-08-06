#!/usr/bin/env sh
# Verifies published inheritance from isolated consumer repositories. No Airness tracked file is edited.
set -eu

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT INT TERM
failures=0

new_consumer() {
    name="$1"
    packaging="${2-jar}"
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
      <id>reactor-child</id>
      <modules>
        <module>child</module>
      </modules>
    </profile>
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
    if [ "$packaging" = 'pom' ]; then
        perl -0pi -e \
            's{(<artifactId>consumer</artifactId>\n  <version>9[.]4[.]2</version>)}{$1\n  <packaging>pom</packaging>}' \
            "$directory/pom.xml"
    fi
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
    cat > "$directory/src/main/java/com/example/FormatFixture.java" <<'JAVA'
package com.example;

/** Exercises source application through the inherited format profile. */
public final class FormatFixture {
private FormatFixture() {}

/**
 * Supplies a stable value.
 *
 * @return the value
 */
public static int value() { return 1; }
}
JAVA
    cat > "$directory/src/main/java/com/example/ProtocolPath.java" <<'JAVA'
package com.example;

/**
 * Holds a protocol path that resembles a fully qualified Java type.
 */
public final class ProtocolPath {

    private final String value;

    /**
     * Creates the protocol-path fixture.
     */
    public ProtocolPath() {
        this.value = "/agent.v1.AgentService/Run";
    }

    /**
     * Supplies the path.
     *
     * @return the protocol path
     */
    public String value() {
        return this.value;
    }
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
    (cd "$directory" && mvn --quiet airness:assets-sync >/dev/null)
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

managed="$scratch/managed-version"
mkdir -p "$managed"
cat > "$managed/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>managed-version</artifactId>
  <version>1.0.0</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
    <exec-maven-plugin.version>1</exec-maven-plugin.version>
    <central-publishing-maven-plugin.version>1</central-publishing-maven-plugin.version>
    <versions-maven-plugin.version>1</versions-maven-plugin.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>1</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-source-plugin</artifactId>
        <version>1</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-gpg-plugin</artifactId>
        <version>1</version>
      </plugin>
      <plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>versions-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
POM
run_case 'coordinates: child ownership is rejected' 1 'Airness (owns|supplies) this' \
    "$managed" airness:check-parameters
run_case 'versions: inherited pin drives update reports' 0 'versions:2\.21\.0:display-dependency-updates' \
    "$consumer" versions:display-dependency-updates

if grep -Fq '    public static int value() {' "$consumer/src/main/java/com/example/FormatFixture.java"; then
    echo 'ok       format: inherited profile applies source formatting'
else
    echo 'FAILED   format: inherited profile did not apply source formatting' >&2
    failures=$((failures + 1))
fi
run_case 'format: unchanged sources pass enforcement' 0 'BUILD SUCCESS' \
    "$consumer" airness:source-formatting
run_case 'checkstyle: protocol paths are not Java types' 0 'BUILD SUCCESS' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/ProtocolPath.java'
cat > "$consumer/src/main/java/com/example/Qualified.java" <<'JAVA'
package com.example;

/** Exercises the fully qualified type rule. */
final class Qualified {

    private final java.util.List<String> values = java.util.List.of();
}
JAVA
run_case 'checkstyle: qualified Java types are rejected' 1 'Unnecessary fully-qualified type name' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/Qualified.java'
rm "$consumer/src/main/java/com/example/Qualified.java"
cat > "$consumer/src/main/java/com/example/UnusedLambda.java" <<'JAVA'
package com.example;

import java.util.Optional;

/** Exercises the unnamed lambda parameter rule. */
final class UnusedLambda {

    private UnusedLambda() {
    }

    /**
     * Supplies a stable value.
     *
     * @return the value
     */
    static String value() {
        return Optional.of("input").map(content -> "value").orElseThrow();
    }
}
JAVA
run_case 'checkstyle: unused lambda parameters are rejected' 1 \
    'Unused lambda parameter.*should be unnamed' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/UnusedLambda.java'
rm "$consumer/src/main/java/com/example/UnusedLambda.java"

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
# either check, and explicit synchronization must materialize the exact Claude entry.
expected_claude="$scratch/expected-claude"
printf '@AGENTS.md\n' > "$expected_claude"
expected_java_version="$scratch/expected-java-version"
printf '25\n' > "$expected_java_version"
if cmp -s "$expected_claude" "$consumer/CLAUDE.md"; then
    echo 'ok       instructions: sync writes the exact Claude entry'
else
    echo 'FAILED   instructions: sync wrote the wrong Claude entry' >&2
    failures=$((failures + 1))
fi
if cmp -s "$expected_java_version" "$consumer/.java-version"; then
    echo 'ok       assets: sync writes the pinned Java version'
else
    echo 'FAILED   assets: sync wrote the wrong Java version' >&2
    failures=$((failures + 1))
fi
mv "$consumer/AGENTS.md" "$scratch/consumer-AGENTS.md"
run_case 'instructions: AGENTS is mandatory' 1 'mandatory AGENTS.md file is missing' \
    "$consumer" airness:entry-files -Dairness.instruction.file=NONE -Dairness.entry.files=NONE
mv "$scratch/consumer-AGENTS.md" "$consumer/AGENTS.md"
printf '@AGENTS.md\nRun Maven first.\n' > "$consumer/CLAUDE.md"
run_case 'instructions: CLAUDE has exact content' 1 'must contain exactly @AGENTS.md' \
    "$consumer" airness:entry-files -Dairness.instruction.file=NONE -Dairness.entry.files=NONE
printf '24\n' > "$consumer/.java-version"
run_case 'assets: package rejects drifted pinned content' 1 \
    'Files the harness owns that this project changed or is missing|\.java-version' \
    "$consumer" package
if grep -Fqx '24' "$consumer/.java-version"; then
    echo 'ok       assets: failed package leaves drifted content untouched'
else
    echo 'FAILED   assets: failed package rewrote drifted pinned content' >&2
    failures=$((failures + 1))
fi
(cd "$consumer" && mvn --quiet airness:assets-sync >/dev/null)
if cmp -s "$expected_java_version" "$consumer/.java-version"; then
    echo 'ok       assets: explicit sync restores drifted pinned content'
else
    echo 'FAILED   assets: explicit sync did not restore drifted pinned content' >&2
    failures=$((failures + 1))
fi
run_case 'assets: later pinned change fails tree verification' 1 \
    'Build plugins changed committable files|working tree content differs' \
    "$consumer" -Pdrift-pinned-asset airness:assets-sync airness:tree-snapshot \
    antrun:run@drift-pinned-asset airness:tree-verify

# Maven finishes the root module before starting its child. The tree net therefore needs a fresh
# snapshot and verification pair in every module, or a child plugin can edit the repository after the
# root verification has already passed.
multimodule="$(new_consumer multimodule pom)"
git -C "$multimodule" rm -r --quiet -- src
mkdir -p "$multimodule/child/src/test/java/com/example"
cat > "$multimodule/child/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>test-only-child</artifactId>
  <version>2.0.0</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-antrun-plugin</artifactId>
        <version>3.2.0</version>
        <executions>
          <execution>
            <id>mutate-from-child</id>
            <phase>process-resources</phase>
            <goals>
              <goal>run</goal>
            </goals>
            <configuration>
              <target>
                <echo file="${project.basedir}/../.gitattributes" append="true">child drift</echo>
              </target>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
POM
cat > "$multimodule/child/src/test/java/com/example/OnlyTest.java" <<'JAVA'
package com.example;

/** A test-only module fixture. */
final class OnlyTest {
}
JAVA
(cd "$multimodule" && mvn --quiet editorconfig:format -Preactor-child >/dev/null)
git -C "$multimodule" add --all
git -C "$multimodule" commit --quiet --message 'test(it): add the reactor child fixture'
run_case 'tree: a child-module mutation fails the reactor build' 1 \
    'Build plugins changed committable files|working tree content differs' \
    "$multimodule" clean package -Preactor-child
git -C "$multimodule" restore .gitattributes
run_case 'mutation: a test-only module needs no PIT report' 0 'BUILD SUCCESS' \
    "$multimodule/child" airness:mutation-baseline

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
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
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
  <properties>
    <lombok.version>1</lombok.version>
  </properties>
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
run_case 'coordinates: module ownership is rejected' 1 'Remove child property lombok.version' \
    "$reactor" airness:check-parameters

# A consumer inherits both update reporting and the freshness verdict through every parent level.
stale_grandparent="$scratch/stale-grandparent"
mkdir -p "$stale_grandparent"
cat > "$stale_grandparent/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>stale-grandparent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <properties>
    <picocli.version>2.0.0</picocli.version>
  </properties>
</project>
POM
(cd "$stale_grandparent" && mvn --quiet --non-recursive install -DskipTests)

middle_parent="$scratch/middle-parent"
mkdir -p "$middle_parent"
cat > "$middle_parent/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>stale-grandparent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>middle-parent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>
</project>
POM
(cd "$middle_parent" && mvn --quiet --non-recursive install -DskipTests)

ancestry_consumer="$scratch/ancestry-consumer"
mkdir -p "$ancestry_consumer"
cat > "$ancestry_consumer/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>middle-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>ancestry-consumer</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <dependencies>
    <dependency>
      <groupId>info.picocli</groupId>
      <artifactId>picocli</artifactId>
      <version>${picocli.version}</version>
    </dependency>
  </dependencies>
</project>
POM
run_case 'freshness: indirect parent update fails the consumer' 1 \
    '\[com.example:ancestry-consumer\] info.picocli:picocli' \
    "$ancestry_consumer" airness:dependency-freshness

# Maven can resolve a parent directly from the filesystem without installing it. Freshness discovery
# must follow that resolved model rather than constructing a path under the local repository.
relative_parent="$scratch/relative-parent"
mkdir -p "$relative_parent/child"
cat > "$relative_parent/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>relative-parent</artifactId>
  <version>987.654-SNAPSHOT</version>
  <packaging>pom</packaging>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
  <dependencies>
    <dependency>
      <groupId>info.picocli</groupId>
      <artifactId>picocli</artifactId>
      <version>2.0.0</version>
    </dependency>
  </dependencies>
</project>
POM
cat > "$relative_parent/child/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>relative-parent</artifactId>
    <version>987.654-SNAPSHOT</version>
  </parent>
  <artifactId>relative-child</artifactId>
</project>
POM
run_case 'freshness: uninstalled relative parent is scanned' 1 \
    '\[com.example:relative-parent\] info.picocli:picocli' \
    "$relative_parent/child" airness:dependency-freshness

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
