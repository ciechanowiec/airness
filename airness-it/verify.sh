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
    <version>1.0.4</version>
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
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>2.0.17</version>
      <scope>compile</scope>
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
    cat > "$directory/src/main/java/com/example/package-info.java" <<'JAVA'
/**
 * Isolated consumer types used to exercise the inherited harness.
 */
package com.example;
JAVA
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
private static final float FRACTION = 0.75f;

private FormatFixture() {}

/**
 * Supplies a stable value.
 *
 * @return the value
 */
public static float value() { return FRACTION; }
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
        invoke(dependency);
        verify(dependency, times(1)).run();
    }

    private static void invoke(Runnable dependency) {
        dependency.run();
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
    # A body, because the slow profile reads this history back and a non-trivial change needs one.
    git -C "$directory" commit --quiet \
        --message 'test(it): create an isolated consumer fixture' \
        --message 'The fixture carries one source per rule the harness enforces, so a consumer build has something to report on.'
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

install_graph_artifact() {
    artifact="$1"
    version="$2"
    dependency_artifact="${3-}"
    dependency_version="${4-}"
    directory="$scratch/graph-$artifact-$version"
    mkdir -p "$directory/src/main/resources/fixture"
    printf '%s\n' "$artifact-$version" > "$directory/src/main/resources/fixture/$artifact.txt"
    if [ "$artifact" = 'leaf' ] && [ "$version" = '1.0.0' ] \
        || [ "$artifact" = 'bridge-one' ]; then
        mkdir -p "$directory/src/main/resources/shared"
        printf 'duplicate fixture\n' > "$directory/src/main/resources/shared/Duplicate.class"
    fi
    cat > "$directory/pom.xml" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example.airnessit</groupId>
  <artifactId>$artifact</artifactId>
  <version>$version</version>
POM
    if [ -n "$dependency_artifact" ]; then
        cat >> "$directory/pom.xml" <<POM
  <dependencies>
    <dependency>
      <groupId>com.example.airnessit</groupId>
      <artifactId>$dependency_artifact</artifactId>
      <version>$dependency_version</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
POM
    fi
    cat >> "$directory/pom.xml" <<'POM'
</project>
POM
    (cd "$directory" && mvn --quiet install -DskipTests)
}

consumer="$(new_consumer consumer)"

# Publication safety is inherited separately from the ordinary test verdict. A release can skip an
# already completed verification, but it cannot publish mutable coordinates or incomplete metadata.
snapshot_parent="$scratch/snapshot-parent"
snapshot_consumer="$scratch/snapshot-consumer"
mkdir -p "$snapshot_parent" "$snapshot_consumer"
cat > "$snapshot_parent/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.4</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>snapshot-parent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
</project>
POM
cat > "$snapshot_consumer/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>snapshot-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../snapshot-parent/pom.xml</relativePath>
  </parent>
  <artifactId>snapshot-consumer</artifactId>
  <version>1.0.0</version>
</project>
POM
run_case 'release: a snapshot parent is rejected' 1 'SNAPSHOT' \
    "$snapshot_consumer" enforcer:enforce@airness-release-coordinates -Prelease
publication_metadata="$scratch/publication-metadata"
mkdir -p "$publication_metadata"
cat > "$publication_metadata/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>publication-metadata</artifactId>
  <version>1.0.0</version>
</project>
POM
run_case 'publication: required metadata is rejected' 1 'Maven publication project metadata' \
    "$publication_metadata" \
    eu.ciechanowiec:airness-maven-plugin:1.0.4:publication-metadata

managed="$scratch/managed-version"
mkdir -p "$managed"
cat > "$managed/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.4</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>managed-version</artifactId>
  <version>1.0.0</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
    <exec-maven-plugin.version>1</exec-maven-plugin.version>
    <central-publishing-maven-plugin.version>1</central-publishing-maven-plugin.version>
    <dependency-check-maven.version>1</dependency-check-maven.version>
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
        <groupId>org.owasp</groupId>
        <artifactId>dependency-check-maven</artifactId>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-checkstyle-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
POM
run_case 'coordinates: child ownership is rejected' 1 'Airness (owns|supplies) this' \
    "$managed" airness:check-parameters
run_case 'versions: inherited pin drives update reports' 0 'versions:2\.21\.0:display-dependency-updates' \
    "$consumer" versions:display-dependency-updates
run_case 'vulnerabilities: consumer inherits the public daily feed' 0 \
    'DependencyCheck_Builder/nvd_cache/nvdcve-\{0\}[.]json[.]gz' \
    "$consumer" help:effective-pom -Pextended

# A declaration that does not name one exact version, and a pom that names one coordinate twice. Both
# leave the build to decide what it resolves, and neither leaves a trace in the pom that says so.
declarations="$scratch/declarations"
mkdir -p "$declarations"
cat > "$declarations/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.4</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>declarations</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.apache.commons</groupId>
      <artifactId>commons-lang3</artifactId>
      <version>[3.0,4.0)</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>
POM
run_case 'declarations: a version range is rejected' 1 'banned dynamic version' \
    "$declarations" enforcer:enforce@airness-enforce-dependencies
perl -0pi -e 's{<version>\[3[.]0,4[.]0\)</version>\n      <scope>compile</scope>}{<version>3.20.0</version>\n      <scope>compile</scope>\n    </dependency>\n    <dependency>\n      <groupId>org.apache.commons</groupId>\n      <artifactId>commons-lang3</artifactId>\n      <version>3.19.0</version>\n      <scope>compile</scope>}' \
    "$declarations/pom.xml"
run_case 'declarations: a coordinate declared twice is rejected' 1 'duplicate dependency declaration' \
    "$declarations" enforcer:enforce@airness-enforce-dependencies

# A snapshot is a moving target, so only a release is held to this. The harness's own artifacts travel
# with the parent and are exempt; anything the project chose for itself is not.
install_graph_artifact chosen-snapshot 1.0.0-SNAPSHOT
snapshot_dep="$scratch/snapshot-dep"
mkdir -p "$snapshot_dep"
cat > "$snapshot_dep/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.4</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>snapshot-dep</artifactId>
  <version>1.0.0</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
  <dependencies>
    <dependency>
      <groupId>com.example.airnessit</groupId>
      <artifactId>chosen-snapshot</artifactId>
      <version>1.0.0-SNAPSHOT</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>
POM
run_case 'declarations: a released project rejects a snapshot dependency' 1 \
    'released project resolves a snapshot' \
    "$snapshot_dep" enforcer:enforce@airness-enforce-dependencies
perl -0pi -e 's{<version>1[.]0[.]0</version>}{<version>1.0.0-SNAPSHOT</version>}' "$snapshot_dep/pom.xml"
run_case 'declarations: a snapshot project may resolve a snapshot' 0 'BUILD SUCCESS' \
    "$snapshot_dep" enforcer:enforce@airness-enforce-dependencies

# Resolution policy is tested against locally installed fixtures so the result does not depend on the
# current transitive graph of an unrelated public library. The two bridges request different leaf
# versions, and one bridge deliberately shares a class path with its leaf.
install_graph_artifact leaf 1.0.0
install_graph_artifact leaf 2.0.0
install_graph_artifact bridge-one 1.0.0 leaf 1.0.0
install_graph_artifact bridge-two 1.0.0 leaf 2.0.0
convergence="$scratch/convergence"
mkdir -p "$convergence"
cat > "$convergence/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.4</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>convergence</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
  <dependencies>
    <dependency>
      <groupId>com.example.airnessit</groupId>
      <artifactId>bridge-one</artifactId>
      <version>1.0.0</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>com.example.airnessit</groupId>
      <artifactId>bridge-two</artifactId>
      <version>1.0.0</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>
POM
run_case 'resolution: disagreeing transitive versions are rejected' 1 'Dependency convergence error' \
    "$convergence" enforcer:enforce@airness-enforce-dependencies
perl -0pi -e \
    's{\n    <dependency>\n      <groupId>com[.]example[.]airnessit</groupId>\n      <artifactId>bridge-two</artifactId>.*?</dependency>}{}s' \
    "$convergence/pom.xml"
run_case 'resolution: duplicate classes are rejected even when one dependency is transitive' 1 \
    'Duplicate classes found|shared/Duplicate[.]class' \
    "$convergence" enforcer:enforce@airness-enforce-dependencies

resolution="$scratch/resolution-hygiene"
mkdir -p "$resolution"
cat > "$resolution/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.4</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>resolution-hygiene</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.apache.commons</groupId>
      <artifactId>commons-lang3</artifactId>
      <version>3.20.0</version>
    </dependency>
  </dependencies>
</project>
POM
run_case 'resolution: every dependency names its scope' 1 'does not have an explicit scope defined' \
    "$resolution" enforcer:enforce@airness-enforce-dependencies
perl -0pi -e \
    's{<version>3[.]20[.]0</version>}{<version>3.20.0</version>\n      <scope>compile</scope>}' \
    "$resolution/pom.xml"
perl -0pi -e \
    's{</project>}{  <repositories><repository><id>fixture</id><url>https://example.invalid/maven</url></repository></repositories>\n</project>}' \
    "$resolution/pom.xml"
run_case 'resolution: project repositories are rejected' 1 'poms have repositories defined' \
    "$resolution" enforcer:enforce@airness-enforce-dependencies

perl -0pi -e 's{<airness[.]package[.]root>com[.]example</airness[.]package[.]root>}{<airness.package.root>wrong.base</airness.package.root>}' \
    "$consumer/pom.xml"
run_case 'parameters: wrong package root cannot disable NullAway' 1 \
    'declares package com[.]example, which is outside wrong[.]base' \
    "$consumer" airness:check-parameters
perl -0pi -e 's{<airness[.]package[.]root>wrong[.]base</airness[.]package[.]root>}{<airness.package.root>com.example</airness.package.root>}' \
    "$consumer/pom.xml"
perl -0pi -e \
    's{<id>reactor-child</id>}{<id>reactor-child</id>\n      <properties><skipTests>true</skipTests></properties>}' \
    "$consumer/pom.xml"
run_case 'parameters: an inactive profile cannot retain a verdict bypass' 1 \
    'Remove child property skipTests' "$consumer" airness:check-parameters
perl -0pi -e \
    's{\n      <properties><skipTests>true</skipTests></properties>}{}' "$consumer/pom.xml"
# The harness runs no mutation analysis, so a consumer cannot bring its own. The declaration is refused
# where it is written, in a profile nobody activates, rather than left to produce a report nothing reads.
perl -0pi -e \
    's{<id>reactor-child</id>}{<id>reactor-child</id>\n      <build><plugins><plugin><groupId>org.pitest</groupId><artifactId>pitest-maven</artifactId></plugin></plugins></build>}' \
    "$consumer/pom.xml"
run_case 'parameters: a consumer cannot bring its own mutation analysis' 1 \
    'Remove org[.]pitest:pitest-maven' "$consumer" airness:check-parameters
perl -0pi -e \
    's{\n      <build><plugins><plugin><groupId>org[.]pitest</groupId><artifactId>pitest-maven</artifactId></plugin></plugins></build>}{}' \
    "$consumer/pom.xml"

if grep -Fq '    private static final float FRACTION = 0.75F;' \
    "$consumer/src/main/java/com/example/FormatFixture.java" \
    && grep -Fq '    public static float value() {' \
        "$consumer/src/main/java/com/example/FormatFixture.java"; then
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

# Checkstyle only asks that Javadoc exists. doclint is what reads it: a link the javadoc-links goal
# asked for is worth nothing if nothing afterwards confirms the link reaches anything.
cat > "$consumer/src/main/java/com/example/DanglingLink.java" <<'JAVA'
package com.example;

/**
 * Exercises the inherited doclint settings.
 *
 * @see Example
 */
final class DanglingLink {

    private DanglingLink() {
    }

    /**
     * Supplies a value, as {@link NoSuchTypeAnywhere} does not.
     *
     * @return the value
     */
    static int value() {
        return 1;
    }
}
JAVA
run_case 'doclint: a link that resolves to nothing fails compilation' 1 'reference not found' \
    "$consumer" clean compile
rm "$consumer/src/main/java/com/example/DanglingLink.java"

# Test integrity and determinism. Each rule reads src/test only, so every case below is paired with
# the production fixture at the end, which carries the same constructs and must pass.
cat > "$consumer/src/test/java/com/example/DisabledFixtureTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Exercises the rule against a switched-off test. */
class DisabledFixtureTest {

    @Disabled("intermittent on the build machine")
    @Test
    void neverRuns() {
        assertEquals(1, Example.value());
    }
}
JAVA
run_case 'tests: a disabled test is rejected' 1 'disabled test reports as absent' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/DisabledFixtureTest.java'
rm "$consumer/src/test/java/com/example/DisabledFixtureTest.java"

cat > "$consumer/src/test/java/com/example/AssumingFixtureTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

/** Exercises the rule against a test that assumes its way out. */
class AssumingFixtureTest {

    @Test
    void skipsItselfWhenInconvenient() {
        assumeTrue(Example.value() > 1);
        assertEquals(1, Example.value());
    }
}
JAVA
run_case 'tests: a runtime assumption is rejected' 1 'failed assumption aborts a test' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/AssumingFixtureTest.java'
rm "$consumer/src/test/java/com/example/AssumingFixtureTest.java"

cat > "$consumer/src/test/java/com/example/CommentedFixtureTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Exercises the rule against a commented-out test. */
class CommentedFixtureTest {

    @Test
    void stillRuns() {
        assertEquals(1, Example.value());
    }

    // @Test
    // void stoppedRunningWithoutAnyoneNoticing() {
    //     assertEquals(2, Example.value());
    // }
}
JAVA
run_case 'tests: a commented-out test is rejected' 1 'commented-out test stopped running' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/CommentedFixtureTest.java'
rm "$consumer/src/test/java/com/example/CommentedFixtureTest.java"

cat > "$consumer/src/test/java/com/example/SleepingFixtureTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Exercises the rule against a test that waits by guessing. */
class SleepingFixtureTest {

    @Test
    void waitsOnTheClockRatherThanTheCondition() throws InterruptedException {
        Thread.sleep(50);
        assertEquals(1, Example.value());
    }
}
JAVA
run_case 'tests: a sleep is rejected' 1 'speed of the machine' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/SleepingFixtureTest.java'
rm "$consumer/src/test/java/com/example/SleepingFixtureTest.java"

cat > "$consumer/src/test/java/com/example/RandomFixtureTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Exercises the rule against randomness nobody seeded. */
class RandomFixtureTest {

    @Test
    void drawsFromAnUnseededSource() {
        Random source = new Random();
        assertEquals(1, Example.value() + source.nextInt(1));
    }
}
JAVA
run_case 'tests: unseeded randomness is rejected' 1 'Unseeded randomness' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/RandomFixtureTest.java'
rm "$consumer/src/test/java/com/example/RandomFixtureTest.java"

# The same constructs in production code. Nothing above may reach them, or the rules would be
# banning a sleep from the code whose behavior a test is meant to observe.
cat > "$consumer/src/main/java/com/example/Waiting.java" <<'JAVA'
package com.example;

import java.util.Random;
import lombok.experimental.UtilityClass;

/**
 * Carries the constructs the test rules ban, in the place where they are not banned.
 */
@UtilityClass
public final class Waiting {

    /**
     * Waits and then draws a value.
     *
     * @param millis how long to wait
     * @return a drawn value
     * @throws InterruptedException when the wait is interrupted
     */
    public int draw(long millis) throws InterruptedException {
        Thread.sleep(millis);
        return new Random().nextInt();
    }
}
JAVA
run_case 'tests: production code keeps the constructs the test rules ban' 0 'BUILD SUCCESS' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/Waiting.java'
rm "$consumer/src/main/java/com/example/Waiting.java"

# A wait nothing bounds is a build that never returns a verdict. The ceiling is inherited, so a
# consumer that declares no timeout anywhere still gets one.
cat > "$consumer/src/test/java/com/example/UnboundedTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Exercises the inherited default test timeout. */
class UnboundedTest {

    @Test
    void outlastsTheInheritedCeiling() throws Exception {
        new CountDownLatch(1).await(3, TimeUnit.SECONDS);
        assertEquals(1, Example.value());
    }
}
JAVA
# Report-only, so the run reaches the assertion that matters. The test would otherwise pass, so the
# timeout message can only appear if the inherited ceiling is what ended it.
run_case 'tests: the inherited timeout bounds a wait no test declared' 0 'timed out after' \
    "$consumer" test -Dairness.enforce=false -Dairness.test.timeout=1s -Dtest=UnboundedTest
rm "$consumer/src/test/java/com/example/UnboundedTest.java"

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

# The agent files connect coding tools to the shared project instructions. Explicit synchronization
# must materialize the exact Claude entry and the detailed guide.
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
if sed -n '1p' "$consumer/AGENTS.md" | grep -Fq '<!-- BEGIN AIRNESS MANAGED INSTRUCTIONS -->' \
    && grep -Fq '# Consumer instructions' "$consumer/AGENTS.md"; then
    echo 'ok       instructions: sync prepends Airness and preserves project prose'
else
    echo 'FAILED   instructions: sync did not compose AGENTS.md safely' >&2
    failures=$((failures + 1))
fi
if grep -Fq '# Airness Agent Guide' "$consumer/.airness/agent-guide.md"; then
    echo 'ok       instructions: sync writes the detailed agent guide'
else
    echo 'FAILED   instructions: sync did not write the detailed agent guide' >&2
    failures=$((failures + 1))
fi
if grep -Fq '= Software Project Guideline' "$consumer/README-guideline-software-project.adoc"; then
    echo 'ok       instructions: sync writes the software project guideline'
else
    echo 'FAILED   instructions: sync did not write the software project guideline' >&2
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
perl -0pi -e 's/Follow the complete Airness contract/Ignore the complete Airness contract/' "$consumer/AGENTS.md"
run_case 'instructions: stale Airness block is rejected' 1 'stale Airness instructions' \
    "$consumer" airness:entry-files
(cd "$consumer" && mvn --quiet airness:assets-sync >/dev/null)
if grep -Fq 'Follow the complete Airness contract' "$consumer/AGENTS.md" \
    && grep -Fq '# Consumer instructions' "$consumer/AGENTS.md"; then
    echo 'ok       instructions: sync refreshes only the managed block'
else
    echo 'FAILED   instructions: sync did not preserve prose while refreshing the block' >&2
    failures=$((failures + 1))
fi
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
printf 'duplicate license declaration\n' > "$consumer/LiCeNsE.Md"
run_case 'assets: root license filename is rejected case-insensitively' 1 \
    'License files named LICENSE, LICENSE[.]TXT, or LICENSE[.]MD must not sit beside the root pom[.]xml|LiCeNsE[.]Md' \
    "$consumer" airness:assets-check
rm "$consumer/LiCeNsE.Md"
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
    <version>1.0.4</version>
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
cat > "$consumer/src/main/java/com/example/StaleJustification.java" <<'JAVA'
package com.example;

import eu.ciechanowiec.airness.Justification;

/** Exercises stale-justification detection. */
@Justification("A suppression used to be here")
final class StaleJustification {

    private StaleJustification() {
    }
}
JAVA
run_case 'default: a justification without its suppression is stale' 1 \
    'JustificationNeedsSuppression' "$consumer" pmd:check
rm "$consumer/src/main/java/com/example/StaleJustification.java"
cat > "$consumer/src/main/java/com/example/ProseJustification.java" <<'JAVA'
package com.example;

import eu.ciechanowiec.airness.Justification;

/** Exercises annotation prose validation. */
@Justification(value = "one clause" + "; another clause")
@SuppressWarnings("PMD.AtLeastOneConstructor")
final class ProseJustification {
}
JAVA
run_case 'comments: named concatenated justifications are read' 1 'another clause' \
    "$consumer" airness:comment-prose
rm "$consumer/src/main/java/com/example/ProseJustification.java"

# Build the archive through ordinary Maven goals, then inspect the bytes directly. Keeping this case
# outside package proves that the content goal catches material introduced by packaging rather than by
# a Java-source rule.
mkdir -p "$consumer/src/main/resources/.idea"
printf 'local workspace metadata\n' > "$consumer/src/main/resources/.idea/workspace.xml"
(cd "$consumer" && mvn --quiet resources:resources jar:jar -DskipTests)
run_case 'artifact: development metadata in the finished jar is rejected' 1 \
    'Source or development files packaged in the JAR|[.]idea/workspace[.]xml' \
    "$consumer" airness:artifact-content
rm "$consumer/src/main/resources/.idea/workspace.xml"
rmdir "$consumer/src/main/resources/.idea" "$consumer/src/main/resources"

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
    <version>1.0.4</version>
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
      <scope>compile</scope>
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
    <version>1.0.4</version>
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
      <scope>compile</scope>
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
    <version>1.0.4</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>relative-parent</artifactId>
  <version>987.654-SNAPSHOT</version>
  <packaging>pom</packaging>
  <properties>
    <airness.package.root>com.example</airness.package.root>
    <picocli.version>4.7.7</picocli.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>info.picocli</groupId>
      <artifactId>picocli</artifactId>
      <version>${picocli.version}</version>
      <scope>compile</scope>
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
  <properties>
    <picocli.version>2.0.0</picocli.version>
  </properties>
</project>
POM
run_case 'freshness: child override resolves an uninstalled parent declaration' 1 \
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

# A merge commit is prohibited outright. The goal reads the parents git recorded, so the linear fixture above
# passes it and no wording of a header can talk a second parent away.
run_case 'history: a linear consumer passes' 0 'Linear history read' \
    "$untested" airness:linear-history
merged="$(new_consumer merged)"
git -C "$merged" checkout --quiet -b side
git -C "$merged" commit --quiet --allow-empty --message 'feat(core): record a side commit to merge back'
git -C "$merged" checkout --quiet -
git -C "$merged" merge --quiet --no-ff side --message "Merge branch 'side'"
run_case 'history: a merge commit is rejected' 1 'Merge commits in the history' \
    "$merged" airness:linear-history

# The extended profile had never run against a consumer at all, only against Airness itself, so nothing said
# whether a consumer reaches its goals or in what order. The case below stops the build inside the
# governance execution, which runs its history goals ahead of the two goals that want Docker, so the
# wiring is proved without pulling an image.
#
# Findings are reported rather than enforced, for the same reason the fixture is built that way above: it
# exists to exercise the rewrite recipes and carries the untidy code they rewrite, which the compiler
# rejects under enforcement before any of this is reached. What this asserts survives that, because a
# commit the policy rejects stops the build whatever the switch says.
extended_profile="$(new_consumer extended-profile)"
git -C "$extended_profile" commit --quiet --allow-empty --message 'wip'
run_case 'extended: a consumer commit message answers to the policy' 1 \
    'Commit messages that break the policy' \
    "$extended_profile" clean package -Pextended -Dairness.enforce=false

# Published assets contain the pinned software guideline but no other documentation or Git-hook material.
assets="$HOME/.m2/repository/eu/ciechanowiec/airness-assets/1.0.4/airness-assets-1.0.4.jar"
listing="$scratch/assets.txt"
jar tf "$assets" > "$listing"
if ! grep -Fq 'airness/files/README-guideline-software-project.adoc.asset' "$listing"; then
    echo 'FAILED   assets: published jar omitted the software project guideline' >&2
    failures=$((failures + 1))
elif grep -Ev 'airness/files/README-guideline-software-project[.]adoc[.]asset$' "$listing" \
    | grep -Eq '(^|/)(\.vale|\.docs|docinfo|README|githooks|lint-docs)'; then
    echo 'FAILED   assets: documentation or hooks leaked into the published jar' >&2
    failures=$((failures + 1))
else
    echo 'ok       assets: published jar contains only the pinned guideline'
fi

if [ "$failures" -ne 0 ]; then
    printf '\n%s integration case(s) failed.\n' "$failures" >&2
    exit 1
fi
