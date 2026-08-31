#!/usr/bin/env sh
# Verifies published inheritance from isolated consumer repositories. No Airness tracked file is edited.
set -eu

# Fixtures live under the home directory because Docker mounts them. A bind mount only carries
# content from a path the daemon shares, a macOS daemon shares the home directory and need not
# share the system temporary directory, and an unshared source mounts as an empty directory
# rather than as an error. The container then reads nothing, and the goals that drive one report
# on the machine instead of on the fixture, which is the one thing this suite must never do.
scratch="$(mktemp -d "$HOME/.airness-it-XXXXXX")"
# The Qodana container writes its report as root, and a file whose directory root owns cannot be
# unlinked by the user who started the run. Cleanup therefore passes over what it cannot remove
# without a word, so a scratch directory the runner discards anyway never decides the verdict.
trap 'rm -rf "$scratch" 2>/dev/null || true' EXIT INT TERM
failures=0
passed=0
failed_cases=''
started="$(date +%s)"
# Where each case records how long it took. It lands in the scratch directory the run discards,
# unless a caller profiling the suite points it somewhere that outlives the run.
timings="${AIRNESS_IT_TIMINGS:-$scratch/timings.tsv}"

# Every case label already begins with the domain it belongs to, so the domain is printed once when it
# changes rather than repeated on all two hundred lines. Results go to standard output and diagnostics
# to standard error, which is the split a reader redirecting one of the two expects. The exit status
# stays the authority on whether the suite passed.
#
# Colour is written only to a terminal. A captured log, which is how CI reads this, stays plain text,
# and NO_COLOR turns it off for anyone who asks. Everything printed is ASCII, as the rest of this file is.
if [ -t 1 ] && [ -z "${NO_COLOR-}" ]; then
    style_bold="$(printf '\033[1m')"
    style_dim="$(printf '\033[2m')"
    style_pass="$(printf '\033[32m')"
    style_fail="$(printf '\033[31m')"
    style_off="$(printf '\033[0m')"
else
    style_bold=''
    style_dim=''
    style_pass=''
    style_fail=''
    style_off=''
fi
domain=''
detail=''

# Splits one label into the domain it opens with and the rest, printing the domain when it is a new one.
enter() {
    case "$1" in
        *': '*)
            heading="${1%%: *}"
            detail="${1#*: }"
            ;;
        *)
            heading='suite'
            detail="$1"
            ;;
    esac
    if [ "$heading" != "$domain" ]; then
        domain="$heading"
        printf '\n  %s%s%s\n' "$style_bold" "$heading" "$style_off"
    fi
}

pass() {
    enter "$1"
    passed=$((passed + 1))
    printf '    %sPASS%s  %s\n' "$style_pass" "$style_off" "$detail"
}

# The optional second argument is why the case failed, which is worth a line of its own: the assertion
# that did not hold is what a reader needs before the log excerpt that follows it on standard error.
fail() {
    enter "$1"
    failures=$((failures + 1))
    failed_cases="$failed_cases$1
"
    printf '    %sFAIL%s  %s\n' "$style_fail" "$style_off" "$detail"
    if [ -n "${2-}" ]; then
        printf '          %s%s%s\n' "$style_dim" "$2" "$style_off"
    fi
}

# Whole hours and minutes are dropped rather than printed as zero, so a short run reads as seconds.
elapsed() {
    span="$1"
    if [ "$span" -ge 3600 ]; then
        printf '%dh %02dm %02ds' "$((span / 3600))" "$(((span % 3600) / 60))" "$((span % 60))"
    elif [ "$span" -ge 60 ]; then
        printf '%dm %02ds' "$((span / 60))" "$((span % 60))"
    else
        printf '%ds' "$span"
    fi
}

# The consumer fixtures below are written by quoted heredocs, and quoted is what they have to stay: the
# project files they carry hold Maven ${...} properties that an interpolating heredoc would hand to the
# shell instead. So the harness version is spelled out in each of them, and this is what keeps that
# spelling honest. A release that raises the version in the project file and forgets this one is told so
# here, in one sentence, rather than through an unresolvable parent somewhere in the middle of the suite.
harness_version='1.0.7-SNAPSHOT'
repository="$(cd "$(dirname "$0")/.." && pwd)"
declared="$(sed -n 's|^ *<version>\(.*\)</version> *$|\1|p' "$repository/pom.xml" | head -n 1)"
if [ "$declared" != "$harness_version" ]; then
    printf '\n  %s%s declares %s, and this suite is written for %s%s\n\n' \
        "$style_fail" "$repository/pom.xml" "$declared" "$harness_version" "$style_off" >&2
    exit 1
fi

printf '\n  %sAirness integration verification%s\n' "$style_bold" "$style_off"
printf '  %sharness %s, from isolated consumer repositories%s\n' \
    "$style_dim" "$harness_version" "$style_off"

# Where the reactor installed what this suite consumes. A settings.xml that moves the local repository
# is not read here, so a run using one names it through this variable rather than being quietly wrong.
local_repository="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"

# Every jar consumer starts from the same project file, the same sources, and the same three Maven
# invocations that seed the managed files, build the project once, and normalise its formatting. It only
# diverges once the case using it starts writing to it. Building that seven times was the largest single
# cost in this suite, at about forty seconds each, so it is built once and copied afterwards.
#
# Copying it is sound rather than merely quick because nothing in the built tree names its own location.
# The Git repository is copied with it, so a fixture keeps the single commit that the cases reading
# history expect to find.
consumer_template="$scratch/.consumer-template"

new_consumer() {
    consumer_name="$1"
    consumer_packaging="${2-jar}"
    consumer_directory="$scratch/$consumer_name"
    if [ "$consumer_packaging" = 'jar' ]; then
        if [ ! -d "$consumer_template" ]; then
            build_consumer "$consumer_template" jar
        fi
        clone_tree "$consumer_template" "$consumer_directory"
    else
        build_consumer "$consumer_directory" "$consumer_packaging"
    fi
    printf '%s\n' "$consumer_directory"
}

# APFS clones the blocks instead of reading and rewriting one and a half megabytes, which makes the copy
# almost free. A filesystem without that support, which is what the runners use, does an ordinary
# recursive copy and is still far cheaper than building the project again. A half-written clone from the
# first attempt is removed before the second, so the fallback never copies into a directory that exists.
clone_tree() {
    rm -rf "$2"
    if ! cp -c -R "$1" "$2" 2>/dev/null; then
        rm -rf "$2"
        cp -R "$1" "$2"
    fi
}

build_consumer() {
    directory="$1"
    packaging="$2"
    mkdir -p "$directory/src/main/java/com/example" "$directory/src/test/java/com/example"
    cat > "$directory/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.7-SNAPSHOT</version>
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
@NullMarked
package com.example;

import org.jspecify.annotations.NullMarked;
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
    cat > "$directory/src/main/java/com/example/RewriteMapIteration.java" <<'JAVA'
package com.example;

import java.util.Map;

/** Exercises static-analysis modernization inherited from Airness. */
final class RewriteMapIteration {

    private RewriteMapIteration() {
    }

    /**
     * Sums the values a map holds.
     *
     * @param map map whose values to sum
     * @return the sum of the values
     */
    static int sum(Map<String, Integer> map) {
        int sum = 0;
        for (String key : map.keySet()) {
            sum += map.get(key);
        }
        return sum;
    }
}
JAVA
    cat > "$directory/src/test/java/com/example/RewriteTestingTest.java" <<'JAVA'
package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Exercises test-framework cleanup inherited from Airness. */
class RewriteTestingTest {

    @Test
    void cleansAssertions() {
        assertThat("").hasSize(0);
    }

    @Test
    void keepsTheArgumentOrderItWasWrittenWith() {
        assertEquals(value(), "value");
    }

    @Test
    void simplifiesABooleanComparison() {
        assertEquals(false, value().isEmpty());
    }

    private String value() {
        return "value";
    }
}
JAVA
    git -C "$directory" init --quiet
    git -C "$directory" config user.name Fixture
    git -C "$directory" config user.email fixture@example.invalid
    prepare "$directory" --quiet airness:assets-sync
    prepare "$directory" --quiet clean package -Dairness.enforce=false
    prepare "$directory" --quiet process-resources -Pformat -Dairness.enforce=false
    git -C "$directory" add --all
    # A body, because the slow profile reads this history back and a non-trivial change needs one.
    git -C "$directory" commit --quiet \
        --message 'test(it): create an isolated consumer fixture' \
        --message 'The fixture carries one source per rule the harness enforces, so a consumer build has something to report on.'
}

# A setup step is not a case, so what it writes is captured rather than printed. Every direct start of a
# JVM emits a deprecation warning that a transitive dependency of Maven earns and this suite cannot fix,
# and sixty of them between two hundred result lines is what makes the result lines hard to read. A step
# that succeeds has nothing to say. One that fails prints what it wrote and stops, which is more than the
# bare abort that set -e would otherwise produce.
prepare_step=0
prepare() {
    prepare_directory="$1"
    shift
    prepare_step=$((prepare_step + 1))
    prepare_log="$scratch/prepare-$prepare_step-$(basename "$prepare_directory").log"
    if ! (cd "$prepare_directory" && mvn "$@") > "$prepare_log" 2>&1; then
        printf '\n  %ssetup failed in %s%s\n' "$style_fail" "$prepare_directory" "$style_off" >&2
        sed -n '1,220p' "$prepare_log" >&2
        exit 1
    fi
}

run_case() {
    label="$1"
    expected="$2"
    pattern="$3"
    directory="$4"
    shift 4
    log="$scratch/$(printf '%s' "$label" | tr ' /:' '____').log"
    case_started="$(date +%s)"
    set +e
    (cd "$directory" && mvn --batch-mode --no-transfer-progress "$@") >"$log" 2>&1
    status=$?
    set -e
    printf '%s\t%s\n' "$(($(date +%s) - case_started))" "$label" >> "$timings"
    matched=0
    if grep -Eq "$pattern" "$log"; then
        matched=1
    fi
    if [ "$status" -eq "$expected" ] && [ "$matched" -eq 1 ]; then
        pass "$label"
    else
        fail "$label" "exit $status, expected $expected; pattern /$pattern/ matched $matched"
        sed -n '1,220p' "$log" >&2
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
    prepare "$directory" --quiet install -DskipTests
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
    <version>1.0.7-SNAPSHOT</version>
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
    eu.ciechanowiec:airness-maven-plugin:1.0.7-SNAPSHOT:publication-metadata

managed="$scratch/managed-version"
mkdir -p "$managed"
cat > "$managed/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.7-SNAPSHOT</version>
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

# Settings live in the Airness namespace, and the namespace is refused by default. A project declares
# only the keys the user guide documents as its own, so a harness setting invented later is refused the
# day a project first writes it rather than the day somebody notices it was never protected.
settings="$scratch/settings"
mkdir -p "$settings"
write_settings() {
    cat > "$settings/pom.xml" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.7-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>settings</artifactId>
  <version>1.0.0</version>
  <properties>
$1
    <airness.package.root>com.example</airness.package.root>
$2
  </properties>
</project>
POM
}
# The two slots are before and after airness.package.root, because the inherited property-ordering rule
# wants an unreferenced property in alphabetical position and a fixture that ignored it would fail for a
# reason that has nothing to do with the rule under test.
write_settings '' '    <airness.suppression.rate>99</airness.suppression.rate>'
run_case 'settings: an Airness key the project does not own is rejected' 1 'Remove child property airness.suppression.rate' \
    "$settings" airness:check-parameters
write_settings '' '    <airness.typography.excludes>docs</airness.typography.excludes>'
run_case 'settings: a documented project key is accepted' 0 'BUILD SUCCESS' \
    "$settings" airness:check-parameters
# An exclusion names something the report measured, and naming a class outright is allowed. What is not
# allowed is naming nothing: the setting would be accepted, the report unchanged, and the coverage tool
# would have nothing to say because the pattern simply never matched. Driven goal by goal, because the
# coverage floor of this fixture is not met and the tool's own check would end the build first.
run_case 'coverage: an exclusion that reaches no class is rejected' 1 'excludes nothing' \
    "$consumer" test jacoco:report airness:coverage-evidence \
    -Dairness.coverage.excluded.classes='com.example.NoSuchClass'
run_case 'coverage: an exclusion naming one class is accepted' 0 'BUILD SUCCESS' \
    "$consumer" test jacoco:report airness:coverage-evidence \
    -Dairness.coverage.excluded.classes='com.example.FormatFixture'
write_settings '' '    <qodana.image>alpine</qodana.image>'
run_case 'images: a consumer cannot repoint the scanner image' 1 'Airness owns this value' \
    "$settings" airness:check-parameters
run_case 'images: an image named by tag alone is rejected' 1 'Pin the image by digest' \
    "$consumer" airness:scan-secrets -Dgitleaks.image=zricethezav/gitleaks:v8.30.0

# An advisory a project cannot reach is recorded rather than waved through, and the record says why, when
# and which. The scanner already refuses a rule that matches nothing; what it cannot ask is the reason.
suppressions="$scratch/suppressions.xml"
write_suppression() {
    cat > "$suppressions" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<suppressions>
    <suppress>
$1
    </suppress>
</suppressions>
XML
}
write_suppression '        <notes>The vulnerable path is a servlet this project never deploys. Added 2026-08-23.</notes>
        <cve>CVE-2020-27225</cve>'
run_case 'suppressions: a dated and explained entry is accepted' 0 'BUILD SUCCESS' \
    "$consumer" airness:check-parameters "-Dairness.dependency-check.suppression.file=$suppressions"
write_suppression '        <cve>CVE-2020-27225</cve>'
run_case 'suppressions: an entry that explains nothing is rejected' 1 'say why this project cannot reach' \
    "$consumer" airness:check-parameters "-Dairness.dependency-check.suppression.file=$suppressions"
write_suppression '        <notes>The vulnerable path is a servlet this project never deploys.</notes>
        <cve>CVE-2020-27225</cve>'
run_case 'suppressions: an entry with no date is rejected' 1 'record the date' \
    "$consumer" airness:check-parameters "-Dairness.dependency-check.suppression.file=$suppressions"
write_suppression '        <notes>Never reached here. Added 2026-08-23.</notes>
        <packageUrl regex="true">^pkg:maven/org\.example/.*$</packageUrl>'
run_case 'suppressions: an entry naming only a package is rejected' 1 'name the advisory it excuses' \
    "$consumer" airness:check-parameters "-Dairness.dependency-check.suppression.file=$suppressions"

# Networknt publishes a valid Apache-2.0 license under a non-SPDX spelling. The inherited merge
# normalizes it centrally, so a consumer neither configures nor redeclares the owned license plugin.
networknt_license="$scratch/networknt-license"
mkdir -p "$networknt_license"
cat > "$networknt_license/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent</artifactId>
    <version>1.0.7-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>networknt-license</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
  <dependencies>
    <dependency>
      <groupId>com.networknt</groupId>
      <artifactId>json-schema-validator</artifactId>
      <version>3.0.7</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>
POM
run_case 'licenses: Networknt Apache spelling is normalized centrally' 0 'BUILD SUCCESS' \
    "$networknt_license" license:add-third-party@airness-check-dependency-licenses
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
    <version>1.0.7-SNAPSHOT</version>
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
    <version>1.0.7-SNAPSHOT</version>
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
    <version>1.0.7-SNAPSHOT</version>
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
    <version>1.0.7-SNAPSHOT</version>
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

# Declaring a mocking library is the project stating that it means to mock. The graph is not searched,
# so an in-memory implementation of an ecosystem protocol may still carry one of its own.
perl -0pi -e \
    's{  </dependencies>}{    <dependency>\n      <groupId>org.mockito</groupId>\n      <artifactId>mockito-core</artifactId>\n      <version>5.23.0</version>\n      <scope>test</scope>\n    </dependency>\n  </dependencies>}' \
    "$consumer/pom.xml"
run_case 'declarations: a mocking library cannot be declared' 1 'A mocking library is declared here' \
    "$consumer" validate
perl -0pi -e \
    's{    <dependency>\n      <groupId>org[.]mockito</groupId>\n      <artifactId>mockito-core</artifactId>\n      <version>5[.]23[.]0</version>\n      <scope>test</scope>\n    </dependency>\n}{}' \
    "$consumer/pom.xml"

if grep -Fq '    private static final float FRACTION = 0.75F;' \
    "$consumer/src/main/java/com/example/FormatFixture.java" \
    && grep -Fq '    public static float value() {' \
        "$consumer/src/main/java/com/example/FormatFixture.java"; then
    pass 'format: inherited profile applies source formatting'
else
    fail 'format: inherited profile did not apply source formatting'
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
cat > "$consumer/src/main/java/com/example/WrittenTypes.java" <<'JAVA'
package com.example;

import java.util.List;

/**
 * Written-out types and an implicitly typed lambda, none of which the explicitness rule reaches.
 */
public final class WrittenTypes {

    private final List<String> values;

    /**
     * Creates the written-type fixture.
     */
    public WrittenTypes() {
        this.values = List.of("one", "");
    }

    /**
     * Counts the entries that carry text.
     *
     * @return how many entries are not empty
     */
    public int populated() {
        List<String> present = this.values.stream().filter(value -> !value.isEmpty()).toList();
        return present.size();
    }
}
JAVA
run_case 'checkstyle: written types pass the explicitness rule' 0 'BUILD SUCCESS' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/WrittenTypes.java'
rm "$consumer/src/main/java/com/example/WrittenTypes.java"
cat > "$consumer/src/main/java/com/example/InferredLocal.java" <<'JAVA'
package com.example;

import java.util.List;

/** Exercises the written-type rule against an inferred local. */
final class InferredLocal {

    private InferredLocal() {
        throw new IllegalStateException("no instances");
    }

    static int size() {
        var values = List.of("one");
        return values.size();
    }
}
JAVA
run_case 'checkstyle: an inferred local is rejected' 1 "Write the type" \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/InferredLocal.java'
rm "$consumer/src/main/java/com/example/InferredLocal.java"
cat > "$consumer/src/main/java/com/example/InferredLoop.java" <<'JAVA'
package com.example;

import java.util.List;

/** Exercises the written-type rule against an inferred enhanced-for variable. */
final class InferredLoop {

    private InferredLoop() {
        throw new IllegalStateException("no instances");
    }

    static int total(List<String> values) {
        int length = 0;
        for (var value : values) {
            length += value.length();
        }
        return length;
    }
}
JAVA
run_case 'checkstyle: an inferred loop variable is rejected' 1 "Write the type" \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/InferredLoop.java'
rm "$consumer/src/main/java/com/example/InferredLoop.java"
cat > "$consumer/src/main/java/com/example/InferredResource.java" <<'JAVA'
package com.example;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Exercises the written-type rule against an inferred try-with-resources binding. */
final class InferredResource {

    private InferredResource() {
        throw new IllegalStateException("no instances");
    }

    static int read() throws IOException {
        try (var stream = (InputStream) new ByteArrayInputStream(new byte[0])) {
            return stream.read();
        }
    }
}
JAVA
run_case 'checkstyle: an inferred resource is rejected' 1 "Write the type" \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/InferredResource.java'
rm "$consumer/src/main/java/com/example/InferredResource.java"
cat > "$consumer/src/main/java/com/example/InferredLambda.java" <<'JAVA'
package com.example;

import java.util.function.BinaryOperator;

/** Exercises the written-type rule against explicitly inferred lambda parameters. */
final class InferredLambda {

    private InferredLambda() {
        throw new IllegalStateException("no instances");
    }

    static BinaryOperator<String> joining() {
        return (var first, var second) -> first + second;
    }
}
JAVA
run_case 'checkstyle: inferred lambda parameters are rejected' 1 "Write the type" \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/InferredLambda.java'
rm "$consumer/src/main/java/com/example/InferredLambda.java"
cat > "$consumer/src/main/java/com/example/PathUtils.java" <<'JAVA'
package com.example;

/** Exercises the banned type-name suffix. */
final class PathUtils {

    private PathUtils() {
        throw new IllegalStateException("no instances");
    }
}
JAVA
run_case 'checkstyle: a Util suffix is rejected' 1 'Type name names an action rather than a thing' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/PathUtils.java'
rm "$consumer/src/main/java/com/example/PathUtils.java"
cat > "$consumer/src/main/java/com/example/Utilization.java" <<'JAVA'
package com.example;

/**
 * A subject whose name merely contains the banned suffix, which the anchor must leave alone.
 *
 * @param percentage how much of the capacity is in use
 */
public record Utilization(int percentage) {
}
JAVA
run_case 'checkstyle: a name that only contains Util passes' 0 'BUILD SUCCESS' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/Utilization.java'
rm "$consumer/src/main/java/com/example/Utilization.java"

# A method that overrides another does not choose its parameter list, so the cap asks it for an edit
# that cannot be made here. Both analyzers that measure it pass over an override, and both still
# measure the declaration the list came from, which is where the finding can be answered.
#
# Only Checkstyle is driven here, because it is the one of the two whose scope a command line can
# narrow: maven-pmd-plugin publishes no includes property, so a run of it would read the whole fixture
# and report on files these two cases are not about. The PMD half is held by this harness's own build
# instead, where TemplateFragmentCheck overrides a five-parameter method of the markup parser and
# carries no suppression: removing the exemption fails that build.
cat > "$consumer/src/main/java/com/example/Wide.java" <<'JAVA'
package com.example;

/**
 * Declares a list wider than the cap allows, which is this file's own decision to make.
 */
public interface Wide {

    /**
     * Takes more than a callable may.
     *
     * @param first  the first
     * @param second the second
     * @param third  the third
     * @param fourth the fourth
     * @param fifth  the fifth
     */
    void carry(String first, String second, String third, String fourth, String fifth);
}
JAVA
run_case 'caps: a declaration wider than the cap is rejected' 1 'More than 4 parameters' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/Wide.java'
cat > "$consumer/src/main/java/com/example/Carrier.java" <<'JAVA'
package com.example;

/**
 * Implements a list it did not choose, which no edit to this file can narrow.
 */
public final class Carrier implements Wide {

    @Override
    public void carry(String first, String second, String third, String fourth, String fifth) {
        throw new IllegalStateException(first + second + third + fourth + fifth);
    }
}
JAVA
run_case 'caps: an override of that declaration passes' 0 'BUILD SUCCESS' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/Carrier.java'
rm "$consumer/src/main/java/com/example/Carrier.java" "$consumer/src/main/java/com/example/Wide.java"

# A package that declares no nullness leaves every type in it undecided, which costs nothing until this
# code meets a framework that did decide. Spring marks its own packages, so an override of one of its
# methods then states a weaker contract than the method it overrides, and the promise it failed to
# repeat is checked by nothing. The annotation is on the compile classpath already, put there by the
# parent, so the rule asks for a line rather than for a dependency.
cat > "$consumer/src/main/java/com/example/package-info.java" <<'JAVA'
/**
 * A package that says nothing about whether the types in it can be absent.
 */
package com.example;
JAVA
run_case 'checkstyle: a package that declares no nullness is rejected' 1 'does not declare its nullness' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/package-info.java'
cat > "$consumer/src/main/java/com/example/package-info.java" <<'JAVA'
/**
 * Isolated consumer types used to exercise the inherited harness.
 */
@NullMarked
package com.example;

import org.jspecify.annotations.NullMarked;
JAVA
run_case 'checkstyle: a package that declares its nullness passes' 0 'BUILD SUCCESS' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/package-info.java'
# The other half of the same rule. An ordinary source carries no package annotation and must not be
# asked for one, and a filter that failed to scope would suppress the rule everywhere rather than
# here, which is the way this rule fails silently rather than loudly.
cat > "$consumer/src/main/java/com/example/Marked.java" <<'JAVA'
package com.example;

/**
 * An ordinary source, which declares no package and must not be asked to.
 *
 * @param seats how many people the room holds
 */
public record Marked(int seats) {
}
JAVA
run_case 'checkstyle: the nullness rule reaches no file but a package-info' 0 'BUILD SUCCESS' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/Marked.java'
rm "$consumer/src/main/java/com/example/Marked.java"
cat > "$consumer/src/main/java/com/example/Quantity.java" <<'JAVA'
package com.example;

/**
 * Exercises the narrowed structural-value allowlist.
 */
public final class Quantity {

    private final int value;

    /**
     * Creates the quantity fixture.
     */
    public Quantity() {
        this.value = 3;
    }

    /**
     * Scales the value by a quantity that is not structural.
     *
     * @return the scaled value
     */
    public int scaled() {
        return this.value * 100;
    }
}
JAVA
run_case 'checkstyle: a quantity outside the structural allowlist is rejected' 1 'is a magic number' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/Quantity.java'
rm "$consumer/src/main/java/com/example/Quantity.java"
cat > "$consumer/src/main/java/com/example/NamedQuantity.java" <<'JAVA'
package com.example;

/**
 * The same value, named, which is the form the rule asks for.
 */
public final class NamedQuantity {

    private static final int PERCENT = 100;

    private final int value;

    /**
     * Creates the named-quantity fixture.
     */
    public NamedQuantity() {
        this.value = 3;
    }

    /**
     * Scales the value by a named constant.
     *
     * @return the scaled value
     */
    public int scaled() {
        return this.value * PERCENT;
    }
}
JAVA
run_case 'checkstyle: the same quantity named passes' 0 'BUILD SUCCESS' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/NamedQuantity.java'
rm "$consumer/src/main/java/com/example/NamedQuantity.java"
cat > "$consumer/src/main/java/com/example/Mutable.java" <<'JAVA'
package com.example;

/**
 * Exercises the rule that a field is assigned once.
 */
public final class Mutable {

    private String name;

    /**
     * Creates the mutable fixture.
     */
    public Mutable() {
        this.name = "first";
    }

    /**
     * Supplies the name.
     *
     * @return the name this object currently holds
     */
    public String name() {
        return this.name;
    }
}
JAVA
run_case 'checkstyle: a field that can be assigned twice is rejected' 1 'assigned once' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/Mutable.java'
rm "$consumer/src/main/java/com/example/Mutable.java"
cat > "$consumer/src/main/java/com/example/Settled.java" <<'JAVA'
package com.example;

/**
 * The same object with its field settled at construction, which is the form the rule asks for.
 */
public final class Settled {

    private final String name;

    /**
     * Creates the settled fixture.
     */
    public Settled() {
        this.name = "first";
    }

    /**
     * Supplies the name.
     *
     * @return the name this object was created with
     */
    public String name() {
        return this.name;
    }
}
JAVA
run_case 'checkstyle: a field settled at construction passes' 0 'BUILD SUCCESS' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/Settled.java'
rm "$consumer/src/main/java/com/example/Settled.java"
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

# Two constructors that each reach super directly are both primary to the Qulice ConstructorsOrderCheck,
# which wanted the primary one declared last and so reported one of the pair whichever order they were
# written in. ConstructorsDeclarationGrouping fixes that same order by ascending parameter count, so an
# exception carrying (String) and (String, Throwable) had no arrangement that passed. The Qulice check is
# gone. The pair below is what a consumer could not write before, and the reversed pair after it is the
# rule that outlived the removal: dropping one of two contradicting checks must not drop both.
cat > "$consumer/src/main/java/com/example/BrokenRunException.java" <<'JAVA'
package com.example;

/**
 * Reports a run that could not finish.
 */
public final class BrokenRunException extends RuntimeException {

    /**
     * Reports the failure on its own.
     *
     * @param message what went wrong
     */
    public BrokenRunException(String message) {
        super(message);
    }

    /**
     * Reports the failure together with its cause.
     *
     * @param message what went wrong
     * @param cause   the failure underneath
     */
    public BrokenRunException(String message, Throwable cause) {
        super(message, cause);
    }
}
JAVA
run_case 'checkstyle: constructors in ascending parameter order pass' 0 'BUILD SUCCESS' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/BrokenRunException.java'
rm "$consumer/src/main/java/com/example/BrokenRunException.java"
cat > "$consumer/src/main/java/com/example/ReversedRunException.java" <<'JAVA'
package com.example;

/**
 * Reports a run that could not finish, with its constructors written the other way round.
 */
public final class ReversedRunException extends RuntimeException {

    /**
     * Reports the failure together with its cause.
     *
     * @param message what went wrong
     * @param cause   the failure underneath
     */
    public ReversedRunException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Reports the failure on its own.
     *
     * @param message what went wrong
     */
    public ReversedRunException(String message) {
        super(message);
    }
}
JAVA
run_case 'checkstyle: constructors out of parameter order are rejected' 1 \
    'Constructors should be ordered by increasing parameter count' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/ReversedRunException.java'
rm "$consumer/src/main/java/com/example/ReversedRunException.java"

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

# Substitutes. The criterion is what is banned rather than a name, so each case below reaches for a
# mocking library by one of the three routes an agent has: the import, the qualified call, and the
# annotation that installs one into a container.
cat > "$consumer/src/test/java/com/example/ImportingFixtureTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Exercises the rule against a test that imports a mocking library. */
class ImportingFixtureTest {

    @Test
    void readsWhateverItWasTold() {
        Runnable dependency = Mockito.mock(Runnable.class);
        dependency.run();
        assertEquals(1, Example.value());
    }
}
JAVA
run_case 'substitutes: an import of a mocking library is rejected' 1 'Illegal import - org[.]mockito' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/ImportingFixtureTest.java'
rm "$consumer/src/test/java/com/example/ImportingFixtureTest.java"

cat > "$consumer/src/test/java/com/example/QualifyingFixtureTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Exercises the rule against a substitute built without importing one. */
class QualifyingFixtureTest {

    @Test
    void readsWhateverItWasTold() {
        Runnable dependency = org.mockito.Mockito.mock(Runnable.class);
        dependency.run();
        assertEquals(1, Example.value());
    }
}
JAVA
run_case 'substitutes: a qualified mocking call is rejected' 1 'agrees with itself' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/QualifyingFixtureTest.java'
rm "$consumer/src/test/java/com/example/QualifyingFixtureTest.java"

cat > "$consumer/src/test/java/com/example/InjectingFixtureTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Exercises the rule against a substitute installed into a container. */
class InjectingFixtureTest {

    @MockBean
    private Runnable dependency;

    @Test
    void readsWhateverItWasTold() {
        assertEquals(1, Example.value());
    }
}
JAVA
run_case 'substitutes: a container substitute annotation is rejected' 1 'installs a substitute' \
    "$consumer" checkstyle:check '-Dcheckstyle.includes=**/InjectingFixtureTest.java'
rm "$consumer/src/test/java/com/example/InjectingFixtureTest.java"

# Assertions. Coverage counts a line that ran, so neither case below is visible to it: the first
# reaches no assertion at all, and the second reaches one that no change to the code can move.
cat > "$consumer/src/test/java/com/example/UnprovenFixtureTest.java" <<'JAVA'
package com.example;

import org.junit.jupiter.api.Test;

/** Exercises the rule against a test that judges nothing. */
class UnprovenFixtureTest {

    @Test
    void reachesTheValueAndStopsThere() {
        Example.value();
    }
}
JAVA
run_case 'assertions: a test that judges nothing is rejected' 1 'reaches no assertion' \
    "$consumer" airness:test-assertions
rm "$consumer/src/test/java/com/example/UnprovenFixtureTest.java"

cat > "$consumer/src/test/java/com/example/SettledFixtureTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Exercises the rule against an assertion settled before the code runs. */
class SettledFixtureTest {

    @Test
    void comparesOneConstantWithAnother() {
        assertEquals(1, 1);
    }
}
JAVA
run_case 'assertions: an assertion over literals alone is rejected' 1 'literals alone cannot fail' \
    "$consumer" airness:test-assertions
rm "$consumer/src/test/java/com/example/SettledFixtureTest.java"

run_case 'assertions: a suite that judges what it produced passes' 0 'BUILD SUCCESS' \
    "$consumer" airness:test-assertions

# Dependency structure. The two packages below depend on each other, which no file on the loop shows.
mkdir -p "$consumer/src/main/java/com/example/alpha" "$consumer/src/main/java/com/example/beta"
cat > "$consumer/src/main/java/com/example/alpha/Alpha.java" <<'JAVA'
package com.example.alpha;

import com.example.beta.Beta;

/** One half of a dependency loop. */
public final class Alpha {

    /**
     * Reaches the other half.
     *
     * @return the other half
     */
    public Beta beta() {
        return new Beta();
    }
}
JAVA
cat > "$consumer/src/main/java/com/example/beta/Beta.java" <<'JAVA'
package com.example.beta;

import com.example.alpha.Alpha;

/** The other half of a dependency loop. */
public final class Beta {

    /**
     * Reaches back.
     *
     * @return the first half
     */
    public Alpha alpha() {
        return new Alpha();
    }
}
JAVA
run_case 'design: a package cycle is rejected' 1 'com[.]example[.]alpha -> com[.]example[.]beta' \
    "$consumer" airness:package-cycles
rm -r "$consumer/src/main/java/com/example/alpha" "$consumer/src/main/java/com/example/beta"
run_case 'design: packages that depend in one direction pass' 0 'BUILD SUCCESS' \
    "$consumer" airness:package-cycles

# The suppression ceiling. One annotation naming two rules sets aside two rules, and the smallest
# ceiling underneath the rate is what keeps a small project from being left a budget of none.
cat > "$consumer/src/main/java/com/example/SuppressingFixture.java" <<'JAVA'
package com.example;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Exercises the ceiling on how many rules a project sets aside.
 */
final class SuppressingFixture {

    private SuppressingFixture() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "the caller owns the array it is given")
    static int value() {
        return 1;
    }
}
JAVA
# Three rules over two annotations of two kinds. The SpotBugs annotation counts like the other, or it
# would be the one analyzer a project could silence for free, and its reason counts as none.
run_case 'suppressions: both annotation kinds count, and a reason does not' 0 \
    'Suppressions in force: 3 of 5 allowed' \
    "$consumer" airness:suppression-budget
# Four more rules take the total past the smallest ceiling. The ceiling takes no project setting, so
# the only way to reach it is to hold more suppressions than it allows, which is the point of it.
cat > "$consumer/src/main/java/com/example/ExcessFixture.java" <<'JAVA'
package com.example;

/**
 * Exercises the ceiling once a project holds more than it allows.
 */
final class ExcessFixture {

    private ExcessFixture() {
    }

    @SuppressWarnings({"unchecked", "rawtypes", "deprecation", "serial"})
    static int value() {
        return 1;
    }
}
JAVA
run_case 'suppressions: passing the declared ceiling is rejected' 1 'passed the declared ceiling' \
    "$consumer" airness:suppression-budget
rm "$consumer/src/main/java/com/example/SuppressingFixture.java" \
    "$consumer/src/main/java/com/example/ExcessFixture.java"

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
    pass 'rewrite: SLF4J best practices reach consumers'
else
    fail 'rewrite: SLF4J best practices did not reach consumers'
fi

if grep -Fq 'StringUtils.isBlank' "$consumer/src/main/java/com/example/RewriteApache.java"; then
    fail 'rewrite: Apache Commons cleanup did not reach consumers'
else
    pass 'rewrite: Apache Commons cleanup reaches consumers'
fi

# The only static-analysis recipe named on its own, because it is absent from the composite that
# carries the rest. A release that folds it into that composite, or that renames it, would otherwise
# take it away without a word.
if grep -Fq 'map.entrySet()' "$consumer/src/main/java/com/example/RewriteMapIteration.java" \
    && ! grep -Fq 'map.keySet()' "$consumer/src/main/java/com/example/RewriteMapIteration.java"; then
    pass 'rewrite: the map iteration cleanup reaches consumers'
else
    fail 'rewrite: the map iteration cleanup did not reach consumers'
fi

if grep -Fq 'assertThat("").isEmpty();' "$consumer/src/test/java/com/example/RewriteTestingTest.java"; then
    pass 'rewrite: AssertJ cleanup reaches consumers'
else
    fail 'rewrite: test-framework cleanup did not reach consumers'
fi

# The recipe left out of the assertion cleanup set. It would put the constant first, which reads as a
# convention and is not one: assertEquals calls equals on its first argument, so the swap moves the
# comparison onto the constant's type and a test written to prove this project's equals rejects a
# foreign value proves the JDK's instead. The assertion goes on passing either way, so this line is what
# would notice the recipe coming back.
# The assertion set the recipe above was taken out of is restated by the harness, so the whole JUnit
# chain now runs through three copies of upstream composites. A name that stops resolving takes the set
# away silently: nothing fails, the recipes simply stop running, and the order assertion below would
# then pass for the wrong reason. This is the positive half that rules that out, because it can only
# hold if the innermost copy is still reached.
#
# The comparison it reads is written over a call rather than over a constant. Written over a constant
# the set folds it to a literal and then deletes the assertion outright, which leaves the test body
# empty and the assertion rule reporting it, so the control has to survive its own recipe set.
if grep -Fq 'assertFalse(value().isEmpty());' "$consumer/src/test/java/com/example/RewriteTestingTest.java"; then
    pass 'rewrite: the restated JUnit assertion cleanup still reaches consumers'
else
    fail 'rewrite: the restated JUnit assertion cleanup stopped running'
fi

if grep -Fq 'assertEquals(value(), "value");' "$consumer/src/test/java/com/example/RewriteTestingTest.java"; then
    pass 'rewrite: an assertion keeps the argument order it was written with'
else
    fail 'rewrite: the rewrite reordered the arguments of an assertion'
fi

# The agent files connect coding tools to the shared project instructions. Explicit synchronization
# must materialize the exact Claude entry and the detailed guide.
expected_claude="$scratch/expected-claude"
printf '@AGENTS.md\n' > "$expected_claude"
expected_java_version="$scratch/expected-java-version"
printf '25\n' > "$expected_java_version"
if cmp -s "$expected_claude" "$consumer/CLAUDE.md"; then
    pass 'instructions: sync writes the exact Claude entry'
else
    fail 'instructions: sync wrote the wrong Claude entry'
fi
if sed -n '1p' "$consumer/AGENTS.md" | grep -Fq '<!-- BEGIN AIRNESS MANAGED INSTRUCTIONS -->' \
    && grep -Fq '# Consumer instructions' "$consumer/AGENTS.md"; then
    pass 'instructions: sync prepends Airness and preserves project prose'
else
    fail 'instructions: sync did not compose AGENTS.md safely'
fi
if grep -Fq '# Airness Agent Guide' "$consumer/.airness/agent-guide.md"; then
    pass 'instructions: sync writes the detailed agent guide'
else
    fail 'instructions: sync did not write the detailed agent guide'
fi
if grep -Fq '= Software Project Guideline' "$consumer/README-guideline-software-project.adoc"; then
    pass 'instructions: sync writes the software project guideline'
else
    fail 'instructions: sync did not write the software project guideline'
fi
if cmp -s "$expected_java_version" "$consumer/.java-version"; then
    pass 'assets: sync writes the pinned Java version'
else
    fail 'assets: sync wrote the wrong Java version'
fi
if cmp -s "$repository/airness-assets/src/main/resources/airness/files/.dockerignore.asset" \
    "$consumer/.dockerignore"; then
    pass 'assets: sync writes the Docker ignore seed'
else
    fail 'assets: sync did not write the Docker ignore seed'
fi
mv "$consumer/.dockerignore" "$scratch/consumer-dockerignore"
run_case 'assets: Docker ignore seed is mandatory' 1 \
    'Seeded files that must exist but are missing|[.]dockerignore' \
    "$consumer" airness:assets-check
mv "$scratch/consumer-dockerignore" "$consumer/.dockerignore"
printf '# The consumer owns these patterns.\n' > "$consumer/.dockerignore"
run_case 'assets: Docker ignore seed content is project-owned' 0 'BUILD SUCCESS' \
    "$consumer" airness:assets-check
cp "$repository/airness-assets/src/main/resources/airness/files/.dockerignore.asset" \
    "$consumer/.dockerignore"
mv "$consumer/AGENTS.md" "$scratch/consumer-AGENTS.md"
run_case 'instructions: AGENTS is mandatory' 1 'mandatory AGENTS.md file is missing' \
    "$consumer" airness:entry-files -Dairness.instruction.file=NONE -Dairness.entry.files=NONE
mv "$scratch/consumer-AGENTS.md" "$consumer/AGENTS.md"
perl -0pi -e 's/Follow the complete Airness contract/Ignore the complete Airness contract/' "$consumer/AGENTS.md"
run_case 'instructions: stale Airness block is rejected' 1 'stale Airness instructions' \
    "$consumer" airness:entry-files
prepare "$consumer" --quiet airness:assets-sync
if grep -Fq 'Follow the complete Airness contract' "$consumer/AGENTS.md" \
    && grep -Fq '# Consumer instructions' "$consumer/AGENTS.md"; then
    pass 'instructions: sync refreshes only the managed block'
else
    fail 'instructions: sync did not preserve prose while refreshing the block'
fi
printf '@AGENTS.md\nRun Maven first.\n' > "$consumer/CLAUDE.md"
run_case 'instructions: CLAUDE has exact content' 1 'must contain exactly @AGENTS.md' \
    "$consumer" airness:entry-files -Dairness.instruction.file=NONE -Dairness.entry.files=NONE
printf '24\n' > "$consumer/.java-version"
run_case 'assets: package rejects drifted pinned content' 1 \
    'Files the harness owns that this project changed or is missing|\.java-version' \
    "$consumer" package
if grep -Fqx '24' "$consumer/.java-version"; then
    pass 'assets: failed package leaves drifted content untouched'
else
    fail 'assets: failed package rewrote drifted pinned content'
fi
prepare "$consumer" --quiet airness:assets-sync
if cmp -s "$expected_java_version" "$consumer/.java-version"; then
    pass 'assets: explicit sync restores drifted pinned content'
else
    fail 'assets: explicit sync did not restore drifted pinned content'
fi
printf 'duplicate license declaration\n' > "$consumer/LiCeNsE.Md"
run_case 'assets: root license filename is rejected case-insensitively' 1 \
    'License files named LICENSE, LICENSE[.]TXT, or LICENSE[.]MD must not sit beside the root pom[.]xml|LiCeNsE[.]Md' \
    "$consumer" airness:assets-check
rm "$consumer/LiCeNsE.Md"

# The secret scan configuration is seeded and then owned by the project, which is right for the one
# exception a project needs and wrong for everything else in the file. Its shape is checked at validate
# rather than beside the scan, so a configuration that switches the scan off fails the fast command.
write_gitleaks() {
    cat > "$consumer/.gitleaks.toml" <<TOML
title = "Secret scan configuration"

[extend]
useDefault = $1

[[allowlists]]
$2
TOML
}
scoped='description = "Known invalid AWS value"
targetRules = ["generic-api-key"]
regexes = ["AKIA1234567890123456"]'
write_gitleaks true "$scoped"
run_case 'secrets: a rule-scoped exact exception is accepted' 0 'BUILD SUCCESS' \
    "$consumer" airness:assets-check
write_gitleaks false "$scoped"
run_case 'secrets: dropping the shared rule set is rejected' 1 'useDefault must be declared true' \
    "$consumer" airness:assets-check
write_gitleaks true 'description = "a fixture"
regexes = ["AKIA1234567890123456"]'
run_case 'secrets: an exception that names no rule is rejected' 1 'needs both targetRules' \
    "$consumer" airness:assets-check
write_gitleaks true 'description = "a fixture"
targetRules = ["generic-api-key"]
regexes = [".*"]'
run_case 'secrets: the pattern form of an exception is rejected' 1 'is a pattern rather than an exact value' \
    "$consumer" airness:assets-check
write_gitleaks true 'description = "a fixture"
targetRules = ["generic-api-key"]
paths = ["src/test/resources/fixture.txt"]'
run_case 'secrets: an exception that excuses a whole file is rejected' 1 'excuses a whole file' \
    "$consumer" airness:assets-check
write_gitleaks true "$scoped"
run_case 'assets: later pinned change fails tree verification' 1 \
    'Build plugins changed committable files|working tree content differs' \
    "$consumer" -Pdrift-pinned-asset airness:assets-sync airness:tree-snapshot \
    antrun:run@drift-pinned-asset airness:tree-verify
# The case above leaves the drift it made, which is the point of it. Restoring the file here is what
# keeps that drift the subject of one case rather than the reason every later case that reaches a
# lifecycle phase fails its assets check instead of the rule it was written for. The multimodule
# twin of this case restores in the same way.
git -C "$consumer" restore .gitattributes

# A typography exemption is a role rather than a convenience, so the goal reports a prefix that
# excluded nothing as its own rule. Such a prefix names a path that moved or went, it protects
# nothing, and it hides the next thing it would have excluded from whoever reads the list next.
run_case 'typography: an exclusion prefix that excluded nothing is rejected' 1 \
    'exclusion prefix excluded nothing' \
    "$consumer" airness:typography -Dairness.typography.excludes=vendor/
run_case 'report-only: a dead exclusion prefix is visible' 0 \
    'exclusion prefix excluded nothing' \
    "$consumer" airness:typography -Dairness.typography.excludes=vendor/ -Dairness.enforce=false
# The counterpart, so the failure above is read as the dead prefix rather than as any prefix at all,
# and so the cost a live exemption puts on the record stays part of the contract.
run_case 'typography: a prefix that excluded files passes and records its cost' 0 \
    'Typography exemption src left [1-9][0-9]* file' \
    "$consumer" airness:typography -Dairness.typography.excludes=src

# A template is a program whose output is HTML, so nothing about it is known until something reads it,
# and a fragment no page calls yet is read by nothing at all. The parser is the engines' own, so what a
# templating dialect adds is not a defect and only markup no engine could read is reported.
mkdir -p "$consumer/src/main/resources/templates"
cat > "$consumer/src/main/resources/templates/broken.html" <<'HTML'
<html>
<body>
<div class="never closed></div>
</body>
</html>
HTML
run_case 'templates: markup no engine could read is rejected' 1 'no template engine could read' \
    "$consumer" airness:template-parse
rm "$consumer/src/main/resources/templates/broken.html"
cat > "$consumer/src/main/resources/templates/dialect.html" <<'HTML'
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body>
<th:block th:if="${present}">
    <span th:text="${name}" x-data="{ open: true }" @click="open = false">Name</span>
</th:block>
</body>
</html>
HTML
run_case 'templates: the attributes and elements of a dialect pass' 0 'Template parse read [1-9]' \
    "$consumer" airness:template-parse
rm "$consumer/src/main/resources/templates/dialect.html"
rmdir "$consumer/src/main/resources/templates"

# A fragment is invoked by name with a positional argument list, which is what a callable is, so the cap
# on a callable's parameters is the cap on its arguments. Every measured cap in the build stops at the
# last Java file, so without this one a fragment grows to seven positional strings and nothing objects.
mkdir -p "$consumer/src/main/resources/templates"
cat > "$consumer/src/main/resources/templates/wide.html" <<'HTML'
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body>
<div th:fragment="field(label, name, type, purpose, value, error)"></div>
</body>
</html>
HTML
run_case 'templates: a fragment past the argument cap is rejected' 1 'takes 6 arguments' \
    "$consumer" airness:template-fragments
rm "$consumer/src/main/resources/templates/wide.html"
cat > "$consumer/src/main/resources/templates/narrow.html" <<'HTML'
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body>
<div th:fragment="field(label, name, control, error)"></div>
<div th:replace="~{templates/narrow :: field('one, two', b, c, d)}"></div>
</body>
</html>
HTML
run_case 'templates: a fragment at the argument cap passes' 0 'Template fragments read [1-9]' \
    "$consumer" airness:template-fragments
rm "$consumer/src/main/resources/templates/narrow.html"
rmdir "$consumer/src/main/resources/templates"

# A replacement discards the element it is written on, so a condition written beside it decides nothing
# and the fragment is drawn for everybody. The markup parses and the page renders, which is why no other
# check reaches it: the document is valid and the page is wrong.
mkdir -p "$consumer/src/main/resources/templates"
cat > "$consumer/src/main/resources/templates/discarded.html" <<'HTML'
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body>
<span th:if="${primary}" th:replace="~{templates/discarded :: pill}"></span>
</body>
</html>
HTML
run_case 'templates: a replacement that discards a condition is rejected' 1 'discards th:if' \
    "$consumer" airness:template-replacements
rm "$consumer/src/main/resources/templates/discarded.html"
cat > "$consumer/src/main/resources/templates/wrapped.html" <<'HTML'
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body>
<th:block th:if="${primary}">
    <span th:replace="~{templates/wrapped :: pill}"></span>
</th:block>
<div th:if="${shown}" th:insert="~{templates/wrapped :: pill}"></div>
</body>
</html>
HTML
run_case 'templates: a condition on the block around a replacement passes' 0 'Template replacements read [1-9]' \
    "$consumer" airness:template-replacements
rm "$consumer/src/main/resources/templates/wrapped.html"
rmdir "$consumer/src/main/resources/templates"

# The formatter over the half of the tree the Java one never reads. Checking is bound to an ordinary
# build and writing is reachable only through the format profile, so the same file answers both ways.
mkdir -p "$consumer/src/main/resources/static"
printf '.pill {\ncolor : red ;\n     background:blue}\n' > "$consumer/src/main/resources/static/style.css"
run_case 'prettier: an unformatted stylesheet is rejected' 1 'Incorrectly formatted file' \
    "$consumer" process-resources
run_case 'prettier: the format profile rewrites it' 0 'BUILD SUCCESS' \
    "$consumer" -Pformat process-resources
run_case 'prettier: the rewritten stylesheet then passes' 0 'BUILD SUCCESS' \
    "$consumer" process-resources
rm "$consumer/src/main/resources/static/style.css"
rmdir "$consumer/src/main/resources/static" "$consumer/src/main/resources"

# A file git is configured never to carry is build output or a tool's scratch rather than content this
# style governs. The linter has no notion of git, so the list is written for it at validate. Without it
# a stray file in an ignored directory fails a build in a directory nobody wrote, and no project setting
# reaches the exclusions to say otherwise.
# The pair below reads one ignored file and one committable file, and differs in nothing but whether
# the committable one is well formed. Scoping the run to those two is what keeps the subject the
# exclusion rather than the rest of a fixture that was never written to satisfy .editorconfig, and
# including a clean file is what makes the pass mean the linter read something and was content with
# it rather than that it read nothing at all.
mkdir -p "$consumer/coverage"
printf 'A report with no final newline, written by a tool' > "$consumer/coverage/report.txt"
printf 'A note that ends as it should.\n' > "$consumer/notes.txt"
run_case 'editorconfig: a file git ignores is not read' 0 'BUILD SUCCESS' \
    "$consumer" validate editorconfig:check '-Deditorconfig.includes=coverage/**,notes.txt'
printf 'A note with no final newline' > "$consumer/notes.txt"
run_case 'editorconfig: a file git would carry is still read' 1 'insert_final_newline' \
    "$consumer" validate editorconfig:check '-Deditorconfig.includes=coverage/**,notes.txt'
rm -r "$consumer/coverage"
rm "$consumer/notes.txt"

# A picture, a font or a video the linter would otherwise read as text. The suffixes it passes over of
# its own accord stop short of the newer formats, so the parent names them, and the carriage return
# written below is the byte one of them would be reported for. The clean file beside it is what makes
# the pass mean the linter read something and was content with it rather than that it read nothing.
printf 'a\r\nb\n' > "$consumer/mark.woff2"
printf 'A note that ends as it should.\n' > "$consumer/notes.txt"
run_case 'editorconfig: a binary suffix the parent excludes is not read' 0 'BUILD SUCCESS' \
    "$consumer" validate editorconfig:check '-Deditorconfig.includes=mark.woff2,notes.txt'
rm "$consumer/mark.woff2"
rm "$consumer/notes.txt"

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
    <version>1.0.7-SNAPSHOT</version>
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
prepare "$multimodule" --quiet validate editorconfig:format -Preactor-child
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
    fail 'skip: harness findings were shown'
else
    pass 'skip: no harness findings are shown'
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
    pass 'report-only: later harness checks also run'
else
    fail 'report-only: later harness checks did not run'
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

# Copy-paste detection. The consumer is clone-free before the pair below is written, so the rejection
# that follows is the pair being reported rather than the fixture carrying duplication of its own.
run_case 'default: a consumer without duplication passes' 0 'BUILD SUCCESS' "$consumer" pmd:cpd-check
cat > "$consumer/src/main/java/com/example/FirstScorer.java" <<'JAVA'
package com.example;

import java.util.List;
import java.util.Locale;

/** Carries a block long enough to cross the duplication bound. */
final class FirstScorer {

    private FirstScorer() {
    }

    static int score(List<String> values) {
        int total = 0;
        for (int index = 0; index < values.size(); index++) {
            String entry = values.get(index);
            if (entry.isEmpty()) {
                continue;
            }
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("a") || trimmed.startsWith("b")) {
                total = total + trimmed.length() * 2;
            } else if (trimmed.endsWith("z")) {
                total = total - trimmed.length();
            } else {
                total = total + 1;
            }
            if (total > 1000) {
                total = 1000;
            }
        }
        return total;
    }
}
JAVA
sed 's/FirstScorer/SecondScorer/g' "$consumer/src/main/java/com/example/FirstScorer.java" \
    > "$consumer/src/main/java/com/example/SecondScorer.java"
run_case 'default: a duplicated block is rejected' 1 'has found [0-9]+ duplication' \
    "$consumer" pmd:cpd-check
run_case 'report-only: duplication is visible' 0 'has found [0-9]+ duplication' \
    "$consumer" pmd:cpd-check -Dairness.enforce=false
rm "$consumer/src/main/java/com/example/FirstScorer.java" \
   "$consumer/src/main/java/com/example/SecondScorer.java"
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
prepare "$consumer" --quiet resources:resources jar:jar -DskipTests
run_case 'artifact: development metadata in the finished jar is rejected' 1 \
    'Source or development files packaged in the JAR|[.]idea/workspace[.]xml' \
    "$consumer" airness:artifact-content
rm "$consumer/src/main/resources/.idea/workspace.xml"
rmdir "$consumer/src/main/resources/.idea" "$consumer/src/main/resources"

# The same rule aimed at a project that repackages. Package writes two archives there, the thin one the
# jar plugin produces and the one that ships, written after it by the repackaging plugin, and Maven
# merges an inherited plugin ahead of one the project declares. Every goal the parent binds to package
# therefore runs before the repackaging, so a content goal bound to package reads the thin archive and
# never the archive that ships. The development metadata below reaches the shaded archive alone, through
# the shade transformer that includes a file the project keeps outside its resource directories, so the
# thin archive carries nothing to report and a check reading it reports nothing at all.
#
# Findings are reported rather than enforced, because this fixture carries the same untidy sources every
# consumer fixture here carries, and enforcement stops the build at the coverage floor long before any
# archive exists.
repackaged="$(new_consumer repackaged)"
mkdir -p "$repackaged/packaging"
printf 'local workspace metadata\n' > "$repackaged/packaging/workspace.xml"
shade_block="$(cat <<'XML'
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <executions>
          <execution>
            <id>repackage</id>
            <phase>package</phase>
            <goals>
              <goal>shade</goal>
            </goals>
            <configuration>
              <createDependencyReducedPom>false</createDependencyReducedPom>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.IncludeResourceTransformer">
                  <resource>.idea/workspace.xml</resource>
                  <file>packaging/workspace.xml</file>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
XML
)"
SHADE_BLOCK="$shade_block" perl -0pi -e 's{(  <profiles>)}{$ENV{SHADE_BLOCK}."\n".$1}e' "$repackaged/pom.xml"
run_case 'artifact: a repackaging project has the archive it ships inspected' 0 \
    'Source or development files packaged in the JAR' \
    "$repackaged" clean verify -Dairness.enforce=false

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
    <version>1.0.7-SNAPSHOT</version>
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
    <version>1.0.7-SNAPSHOT</version>
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
prepare "$stale_grandparent" --quiet --non-recursive install -DskipTests

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
prepare "$middle_parent" --quiet --non-recursive install -DskipTests

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
    <version>1.0.7-SNAPSHOT</version>
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
prepare "$untested" --quiet clean compile -DskipTests
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
# The header git writes for a merge is a header like any other, and no shape is exempt from the message
# policy. Two findings on one commit is the stronger signal about a commit that should not exist.
run_case 'history: a merge header is read like every other header' 1 'Commit messages that break the policy' \
    "$merged" airness:commit-history

# The extended profile had never run against a consumer at all, only against Airness itself, so nothing
# said whether a consumer reaches its goals or in what order. The order is read from the log rather than
# from an exit code, because the profile ends in two goals that drive Docker, and what a container does
# on one machine it need not do on another. A verdict taken from the exit code would describe the
# machine. Every goal this asserts has already reported by the time either container starts.
#
# Findings are reported rather than enforced, because the fixture carries the untidy code the rewrite
# recipes exist to fix, and enforcement stops the build at the coverage floor long before the profile
# reaches any of these goals.
extended_profile="$(new_consumer extended-profile)"
git -C "$extended_profile" commit --quiet --allow-empty --message 'wip'
extended_log="$scratch/extended-profile.log"
(cd "$extended_profile" && mvn --batch-mode --no-transfer-progress clean package -Pextended \
    -Dairness.enforce=false) > "$extended_log" 2>&1 || true
reached="$(grep -cE '^\[INFO\] --- airness:[^ ]+:(commit-history|commit-typography|linear-history|scan-secrets) \(airness-governance-extended\)' "$extended_log" || true)"
if [ "$reached" -eq 4 ] && grep -Fq 'Commit messages that break the policy' "$extended_log"; then
    pass 'extended: a consumer reaches the profile governance goals'
else
    fail "extended: a consumer reached $reached of 4 profile governance goals"
    sed -n '1,220p' "$extended_log" >&2
fi

# Two inspections of the Qodana profile are off, and the fixture below carries what a consumer could not
# write while they were on. InnerClassOnInterface exempts a nested enum but not a nested record, so it
# reported a sealed interface once per variant, nine times here. ClassWithTooManyDependencies held a class
# to 10 where ClassCoupling declares 15, offered no option but that limit, and never counted the JDK, so
# nothing about it could be narrowed and nothing in the profile said which of the two caps was in force.
#
# Three controls sit beside the regression, because dropping a rule and correcting one look alike from the
# outside. CommandRouter names sixteen collaborators of its own project and must still be reported, by
# ClassCoupling now. CommandRunner names twelve, which is over the cap that went and under the cap that
# stayed, so it is the band the change actually moved. JdkHeavy names fourteen java.util and java.time
# types beside one collaborator and must be reported by neither, which is the reading that showed the
# dropped inspection was a second cap rather than a rule that counted the JDK.
#
# Tool answers a second change to the same profile. UtilityClassWithoutPrivateConstructor now names an
# annotation it lets through, and an exemption keyed on an annotation is only as narrow as the projects
# that cannot carry it. Tool declares nothing but a main and carries no annotation, so the inspection has
# to report it here, in a consumer with no Spring on its classpath at all. The reading it is the control
# for is in the Spring Boot fixture below, which is the only one that resolves the annotation.
#
# The verdict is read from the report rather than from the exit code, because a fixture that is meant to
# carry one finding cannot say through an exit code which finding it carried. The case drives Docker, so
# where no daemon answers it reports that it did not run instead of deciding the suite on a machine that
# cannot start a container.
if ! docker info >/dev/null 2>&1; then
    echo 'skipped  qodana: no Docker daemon answered, so the profile cases did not run'
else
    qodana_consumer="$(new_consumer qodana-profile)"
    cat > "$qodana_consumer/src/main/java/com/example/CommandRequest.java" <<'JAVA'
package com.example;

/**
 * A closed set of requests, laid out with its permitted records inside the sealed parent.
 */
public sealed interface CommandRequest {

    /**
     * Describes the request.
     *
     * @return the description
     */
    String description();

    /**
     * Starts a project.
     *
     * @param root what the request names
     */
    record Init(String root) implements CommandRequest {

        @Override
        public String description() {
            return "init";
        }
    }

    /**
     * Reads a source without changing it.
     *
     * @param source what the request names
     */
    record Lint(String source) implements CommandRequest {

        @Override
        public String description() {
            return "lint";
        }
    }

    /**
     * Produces the artifact.
     *
     * @param target what the request names
     */
    record Build(String target) implements CommandRequest {

        @Override
        public String description() {
            return "build";
        }
    }

    /**
     * Discards produced output.
     *
     * @param scope what the request names
     */
    record Clean(String scope) implements CommandRequest {

        @Override
        public String description() {
            return "clean";
        }
    }

    /**
     * Rewrites a source into its canonical shape.
     *
     * @param style what the request names
     */
    record Format(String style) implements CommandRequest {

        @Override
        public String description() {
            return "format";
        }
    }

    /**
     * Sends the artifact to a registry.
     *
     * @param registry what the request names
     */
    record Publish(String registry) implements CommandRequest {

        @Override
        public String description() {
            return "publish";
        }
    }

    /**
     * Writes the findings of a run.
     *
     * @param findings what the request names
     */
    record Report(String findings) implements CommandRequest {

        @Override
        public String description() {
            return "report";
        }
    }

    /**
     * Decides whether the project holds.
     *
     * @param rules what the request names
     */
    record Verify(String rules) implements CommandRequest {

        @Override
        public String description() {
            return "verify";
        }
    }

    /**
     * Repeats a run whenever a source changes.
     *
     * @param trigger what the request names
     */
    record Watch(String trigger) implements CommandRequest {

        @Override
        public String description() {
            return "watch";
        }
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/OutputFormat.java" <<'JAVA'
package com.example;

/**
 * The shapes a run can report in.
 */
public enum OutputFormat {

    /**
     * Plain text.
     */
    TEXT,

    /**
     * Structured JSON.
     */
    JSON;

    /**
     * Reads a format from its written name.
     *
     * @param name the written name
     * @return the format the name stands for
     */
    public static OutputFormat of(String name) {
        return "json".equals(name) ? JSON : TEXT;
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/ParseResult.java" <<'JAVA'
package com.example;

/**
 * The outcome of reading one request.
 *
 * @param name the name the request parsed to
 */
public record ParseResult(String name) {
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/SafePath.java" <<'JAVA'
package com.example;

import java.nio.file.Path;

/**
 * A path the run is allowed to reach.
 *
 * @param value the permitted path
 */
public record SafePath(Path value) {
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/BrokenRunException.java" <<'JAVA'
package com.example;

/**
 * Reports a run that could not finish.
 */
public final class BrokenRunException extends RuntimeException {

    /**
     * Reports the failure on its own.
     *
     * @param message what went wrong
     */
    public BrokenRunException(String message) {
        super(message);
    }

    /**
     * Reports the failure together with its cause.
     *
     * @param message what went wrong
     * @param cause   the failure underneath
     */
    public BrokenRunException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Sums the failure up in one line.
     *
     * @return the summary of the failure
     */
    public String summary() {
        return "broken run: " + this.getMessage();
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/CommandGrammar.java" <<'JAVA'
package com.example;

/**
 * Reads a request into the name the run works with.
 */
public final class CommandGrammar {

    /**
     * Reads one request.
     *
     * @param request the request to read
     * @return what the request parsed to
     */
    public ParseResult parse(CommandRequest request) {
        return new ParseResult(request.description());
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/ProjectRepository.java" <<'JAVA'
package com.example;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The project a run reads.
 */
public final class ProjectRepository {

    private final Path root;

    /**
     * Creates the repository.
     *
     * @param root where the project sits
     */
    public ProjectRepository(Path root) {
        this.root = root;
    }

    /**
     * Locates what a parsed request names.
     *
     * @param result what the request parsed to
     * @return the located path, empty when the name reaches nothing
     */
    public Optional<SafePath> locate(ParseResult result) {
        return result.name().isEmpty()
            ? Optional.empty()
            : Optional.of(new SafePath(this.root.resolve(result.name())));
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/CommandRouter.java" <<'JAVA'
package com.example;

/**
 * Reaches every request shape a project offers.
 *
 * <p>The class names sixteen collaborators of its own project and none of the JDK, so it is what a
 * genuinely entangled class looks like once the JDK stops counting.
 */
public final class CommandRouter {

    /**
     * Routes the request shapes a project starts with.
     *
     * @param init  the start request
     * @param lint  the read-only request
     * @param build the producing request
     * @param clean the discarding request
     * @return what the four requests name
     */
    public String opening(
        CommandRequest.Init init, CommandRequest.Lint lint,
        CommandRequest.Build build, CommandRequest.Clean clean
    ) {
        return init.root() + lint.source() + build.target() + clean.scope();
    }

    /**
     * Routes the request shapes a project finishes with.
     *
     * @param format  the rewriting request
     * @param publish the sending request
     * @param report  the writing request
     * @param verify  the deciding request
     * @return what the four requests name
     */
    public String closing(
        CommandRequest.Format format, CommandRequest.Publish publish,
        CommandRequest.Report report, CommandRequest.Verify verify
    ) {
        return format.style() + publish.registry() + report.findings() + verify.rules();
    }

    /**
     * Routes a repeated run against what it locates.
     *
     * @param watch      the repeating request
     * @param repository the project the run reads
     * @param fallback   where the run reads when the name reaches nothing
     * @param result     what the request parsed to
     * @return what the repeated run reaches
     */
    public String repeated(
        CommandRequest.Watch watch, ProjectRepository repository,
        SafePath fallback, ParseResult result
    ) {
        return watch.trigger()
            + repository.locate(result).orElse(fallback).value()
            + result.name();
    }

    /**
     * Routes a request that has not been read yet.
     *
     * @param grammar the grammar the request is read with
     * @param output  the shape the run reports in
     * @param failure the failure a broken run carries
     * @param request the request to read
     * @return what the unread request reaches
     */
    public String unread(
        CommandGrammar grammar, OutputFormat output,
        BrokenRunException failure, CommandRequest request
    ) {
        return grammar.parse(request).name() + output.name() + failure.summary();
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/CommandRunner.java" <<'JAVA'
package com.example;

/**
 * Reads twelve request shapes of its own project.
 *
 * <p>Twelve is over the cap the dropped inspection held a class to and under the cap ClassCoupling
 * declares, so the class is exactly what the change let a consumer write.
 */
public final class CommandRunner {

    /**
     * Reads the request shapes a project starts with.
     *
     * @return what those requests describe
     */
    public String opening() {
        return new CommandRequest.Init("root").description()
            + new CommandRequest.Lint("source").description()
            + new CommandRequest.Build("target").description()
            + new CommandRequest.Clean("scope").description();
    }

    /**
     * Reads the request shapes a project finishes with.
     *
     * @return what those requests describe
     */
    public String closing() {
        return new CommandRequest.Format("style").description()
            + new CommandRequest.Publish("registry").description()
            + new CommandRequest.Report("findings").description()
            + new CommandRequest.Verify("rules").description();
    }

    /**
     * Reads a repeated run.
     *
     * @return what the repeated run reports in
     */
    public String repeated() {
        CommandRequest request = new CommandRequest.Watch("changes");
        ParseResult result = new ParseResult(request.description());
        OutputFormat output = OutputFormat.of(result.name());
        return output.name();
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/JdkHeavy.java" <<'JAVA'
package com.example;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Names fourteen JDK types beside a single collaborator of its own project.
 *
 * <p>No coupling rule counts what this class reaches into java.util and java.time, so the class is
 * held to a budget of one.
 */
public final class JdkHeavy {

    /**
     * Reads every JDK type this fixture is built from.
     *
     * @param format the one collaborator the class names
     * @return a reading of the types above
     */
    public String reading(OutputFormat format) {
        Deque<String> queue = new ArrayDeque<>(List.of(format.name()));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(queue.peek(), 1);
        NavigableSet<String> sorted = new TreeSet<>(counts.keySet());
        Set<OutputFormat> formats = EnumSet.of(format);
        Optional<String> first = Optional.of(sorted.getFirst());
        Duration span = Duration.between(Instant.EPOCH, Instant.EPOCH);
        LocalDate day = LocalDate.EPOCH;
        String read = sorted.stream().collect(Collectors.joining(", ", "[", "]"));
        return first.orElse("none")
            + " / " + formats.size()
            + " / " + span.toMillis()
            + " / " + day.getDayOfYear()
            + " / " + read;
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/Tool.java" <<'JAVA'
package com.example;

/**
 * A static-only class that declares a main and carries no annotation.
 */
public final class Tool {

    /**
     * Runs the tool.
     *
     * @param args the command line arguments
     */
    static void main(String[] args) {
        IO.println(args.length);
    }
}
JAVA
    git -C "$qodana_consumer" add --all
    git -C "$qodana_consumer" commit --quiet \
        --message 'test(it): carry the shapes the dropped inspections reported' \
        --message 'The fixture holds a sealed hierarchy, an over-coupled class, a class inside the band that moved and a JDK-heavy class, so the profile has something to be read against.'
    qodana_log="$scratch/qodana-profile.log"
    (cd "$qodana_consumer" && mvn --batch-mode --no-transfer-progress airness:qodana) \
        > "$qodana_log" 2>&1 || true
    qodana_sarif="$qodana_consumer/target/qodana/qodana.sarif.json"
    if [ ! -f "$qodana_sarif" ]; then
        fail 'qodana: the run left no report to read'
        sed -n '1,220p' "$qodana_log" >&2
    else
        for dropped in InnerClassOnInterface ClassWithTooManyDependencies; do
            reported="$(grep -c "\"ruleId\": \"$dropped\"" "$qodana_sarif" || true)"
            if [ "$reported" -eq 0 ]; then
                pass "qodana: $dropped reports nothing on the fixture"
            else
                fail "qodana: $dropped reported $reported finding(s)"
            fi
        done
        if grep -q "'CommandRouter' is overly coupled" "$qodana_sarif"; then
            pass 'qodana: ClassCoupling still reports an over-coupled class'
        else
            fail 'qodana: ClassCoupling stopped reporting the over-coupled class'
            sed -n '1,220p' "$qodana_log" >&2
        fi
        for spared in CommandRunner JdkHeavy; do
            if grep -q "'$spared' is overly coupled" "$qodana_sarif"; then
                fail "qodana: ClassCoupling reported $spared"
            else
                pass "qodana: ClassCoupling leaves $spared alone"
            fi
        done
        if grep -q "Class 'Tool' has only 'static' members" "$qodana_sarif"; then
            pass 'qodana: a static-only class with a main is still reported'
        else
            fail 'qodana: the utility-class exemption reached a project without the annotation'
        fi
    fi
fi

# Spring Boot consumers name a second parent. It relaxes what Spring genuinely makes impossible, adds the
# rules only a Spring Boot project can break, and leaves every other project answering to what it did
# before. The source cases below declare no Spring dependency on purpose: the rules read the source, so
# the fixture needs the annotations and not the platform behind them.
spring_source="$scratch/spring-source"
mkdir -p "$spring_source/src/main/java/com/example/deep"
cat > "$spring_source/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent-spring-boot</artifactId>
    <version>1.0.7-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>spring-source</artifactId>
  <version>1.0.0</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
</project>
POM
cat > "$spring_source/src/main/java/com/example/Offender.java" <<'JAVA'
package com.example;

class Offender {

    @Autowired
    private Neighbour neighbour;

    @Transactional
    void hidden() {
    }

    @Async
    void fired() {
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Wiring.java" <<'JAVA'
package com.example;

@Configuration
class Wiring {

    @Bean
    Neighbour neighbour() {
        return new Neighbour();
    }

    @Bean
    Holder holder() {
        return new Holder(neighbour());
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/deep/Application.java" <<'JAVA'
package com.example.deep;

@SpringBootApplication(proxyBeanMethods = false)
final class Application {
}
JAVA

# One source per rule group, each construct chosen so the rules of that group report on it. The sources
# declare no Spring dependency for the reason the block header gives: Checkstyle reads the annotation
# text, so a fixture needs the spelling and not the platform behind it.
mkdir -p "$spring_source/src/test/java/com/example"
cat > "$spring_source/src/main/java/com/example/Proxied.java" <<'JAVA'
package com.example;

class Proxied {

    @PreAuthorize("hasRole('X')")
    void guarded() {
    }

    @Cacheable("names")
    String cached() {
        return "";
    }

    @Async
    void offloaded() {
    }

    @Validated
    void checked() {
    }

    @Retryable
    void attempted() {
    }

    @Transactional(readOnly = true, timeout = 5)
    public final void frozen() {
    }

    @Transactional(readOnly = true, timeout = 5)
    public static void detached() {
    }

    @Cacheable("a")
    @CachePut("a")
    public String contradictory() {
        return "";
    }

    @Bean
    public void publishes() {
    }

    @Bean
    public BeanPostProcessor processor() {
        return null;
    }
}

@Service
final class Frozen {

    @Transactional(readOnly = true, timeout = 5)
    public void act() {
    }
}

interface CachedReader {

    @Cacheable("x")
    String read();
}
JAVA
cat > "$spring_source/src/main/java/com/example/Injected.java" <<'JAVA'
package com.example;

@Service
class Injected {

    @Autowired
    private static Neighbour shared;

    private Neighbour mutable;

    @Autowired
    Injected(@Lazy ApplicationContext context) {
    }

    @Autowired
    public void setNeighbour(Neighbour neighbour) {
        this.mutable = neighbour;
    }
}

class Aware implements ApplicationContextAware {
}
JAVA
cat > "$spring_source/src/main/java/com/example/Transacted.java" <<'JAVA'
package com.example;

@Service
class Transacted {

    @Transactional
    public void writes() throws java.io.IOException {
    }

    @Modifying
    public void bulk() {
    }

    public void manual() {
        this.entityManager.getTransaction();
    }
}

interface Ledger {

    @Transactional(readOnly = true, timeout = 5)
    void post();
}
JAVA
cat > "$spring_source/src/main/java/com/example/Persisted.java" <<'JAVA'
package com.example;

@Entity
@Data
class Persisted {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(cascade = CascadeType.ALL)
    private Persisted parent;

    @OneToMany(cascade = CascadeType.ALL)
    private List<Persisted> children;

    @ManyToMany
    private List<Persisted> peers;

    Persisted(Long identifier) {
        this.id = identifier;
    }
}

interface PersistedRepository {

    Page<Persisted> byName(String name);

    List<Persisted> findByParent(Persisted parent);

    @Query("select p from Persisted p where p.name = " + NAME)
    Set<Persisted> byQuery();

    @Query(value = "select * from persisted", nativeQuery = true)
    Set<Persisted> byNativeQuery();
}
JAVA
# The two ways a repository query claims a bound, read as a pair from one log. The parameter is the only
# one the harness accepts: a bound named in the method carries the ordering with it, which is how a
# derived name outgrows the length a method name is held to.
cat > "$spring_source/src/main/java/com/example/Named.java" <<'JAVA'
package com.example;

interface NamedRepository {

    List<Persisted> findTop10ByName(String name);
}
JAVA
cat > "$spring_source/src/main/java/com/example/Paged.java" <<'JAVA'
package com.example;

interface PagedRepository {

    List<Persisted> byPage(String name, Pageable pageable);
}
JAVA
cat > "$spring_source/src/main/java/com/example/Hardened.java" <<'JAVA'
package com.example;

@Entity
class Hardened {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public final Long identifier() {
        return this.id;
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Frozen.java" <<'JAVA'
package com.example;

@Entity
final class Frozen {

    private static final long SERIAL = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id = null;
}
JAVA
cat > "$spring_source/src/main/java/com/example/Enclosing.java" <<'JAVA'
package com.example;

class Enclosing {

    @Entity
    class Nested {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Recorded.java" <<'JAVA'
package com.example;

@Entity
record Recorded(Long id, String label) {
}
JAVA
cat > "$spring_source/src/main/java/com/example/Swept.java" <<'JAVA'
package com.example;

class Swept {

    @Scheduled(cron = "${app.swept.cron}")
    private void sweep() {
    }

    @EventListener
    private void observe(Object event) {
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Routed.java" <<'JAVA'
package com.example;

import com.fasterxml.jackson.annotation.JsonInclude;

@RestController
class Routed {

    @GetMapping("/routed")
    private String read() {
        return "routed";
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Bound.java" <<'JAVA'
package com.example;

@Component
@ConfigurationProperties("app")
class Bound {

    private final String name;

    Bound(String name) {
        this.name = name;
    }
}
JAVA
# A correct entity. It is the counterpart of the fixtures above: nothing may report it, and in
# particular neither the final-class nor the final-field rule, which held it before the persistence
# exemptions were written.
cat > "$spring_source/src/main/java/com/example/Retained.java" <<'JAVA'
package com.example;

@Entity
class Retained {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    private Retained parent;

    protected Retained() {
    }

    public String label() {
        return this.label;
    }
}
JAVA
# The entity shape this harness prescribes: the no-arg constructor JPA needs arrives as the Lombok
# annotation the entity rule's own message asks for, so the only constructor PMD sees is the one that
# assigns. ImmutableField then asks for a final field, which AirnessSpringJpaEntityFieldIsNotFinal refuses
# and the provider could not write. Assembled is the same class without the annotation, and it is what
# keeps the exemption assertion from passing on a rule that had stopped firing.
cat > "$spring_source/src/main/java/com/example/Constructed.java" <<'JAVA'
package com.example;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Constructed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    Constructed(Long identifier) {
        this.id = identifier;
    }

    public Long identifier() {
        return this.id;
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Assembled.java" <<'JAVA'
package com.example;

class Assembled {

    private Long id;

    Assembled(Long identifier) {
        this.id = identifier;
    }

    public Long identifier() {
        return this.id;
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Served.java" <<'JAVA'
package com.example;

@RestController
class Served {

    @Transactional(readOnly = true, timeout = 5)
    public void act() {
    }

    @RequestMapping("/x")
    public String handle(
        @RequestBody Payload body, @Size(max = 10) @RequestParam String query, HttpServletRequest request
    ) {
        return "";
    }

    @CrossOrigin
    public String open() {
        RestTemplate client = new RestTemplate();
        return "";
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Secured.java" <<'JAVA'
package com.example;

class Secured {

    public void chain(HttpSecurity http, WebSecurity web) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        web.ignoring();
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean mixed = http.isSecure() || http.isOpen() && web.isOpen();
        http.headers(headers -> headers.frameOptions().disable());
        Jwts.parser().setSigningKey("literal-signing-key");
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/**").permitAll());
        UserDetailsManager users = new InMemoryUserDetailsManager();
    }
}
JAVA
# The repair the actuator rule's own message prescribes. Naming the probes individually put the second
# literal directly before the permitAll, which the pattern used to match, so the rule refused the one
# shape it asked for.
cat > "$spring_source/src/main/java/com/example/Probed.java" <<'JAVA'
package com.example;

class Probed {

    public void chain(HttpSecurity http) {
        http.authorizeHttpRequests(
            requests -> requests
                .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .anyRequest().authenticated()
        );
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Timed.java" <<'JAVA'
package com.example;

@Component
class Timed {

    @Scheduled(fixedRate = 5000)
    public String tick(String argument) {
        return "";
    }

    @EventListener
    @Transactional(readOnly = true, timeout = 5)
    public void onEvent(Object event) {
    }

    @EventListener
    public void onTwo(Object first, Object second) {
    }

    @InitBinder
    public String bind() {
        return "";
    }

    public void spawn() {
        new Thread(() -> {
        });
    }
}

@ComponentScan(basePackages = "com.example")
class Scanned {
}
JAVA
cat > "$spring_source/src/test/java/com/example/SuiteTest.java" <<'JAVA'
package com.example;

@SpringBootTest(properties = "a=b")
@ActiveProfiles("integration")
@Transactional
@DirtiesContext
class SuiteTest {

    @BeforeTransaction
    String hook(String argument) {
        return "";
    }

    void uses() {
        MockMvc mvc = null;
    }
}
JAVA
# The sources above carry the constructs the analyzer configuration reads. The ones below carry the
# constructs it cannot: each needs two facts about one file correlated, which is the whole reason the
# governance goal exists beside Checkstyle rather than inside it. They are written as separate files
# because three of these rules are satisfied by anything anywhere in the file that answers them, so a
# fixture that broke one of them beside a source that answered another would report neither.
cat > "$spring_source/src/main/java/com/example/Advised.java" <<'JAVA'
package com.example;

@Service
class Advised {

    Advised() {
        this.stored();
    }

    @Transactional(readOnly = true, timeout = 5)
    public void stored() {
    }

    public void entry() {
        stored();
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Registry.java" <<'JAVA'
package com.example;

@Component
class Registry {

    private static Registry current;

    Registry() {
        current = this;
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Identified.java" <<'JAVA'
package com.example;

@Entity
class Identified {

    @Id
    @GeneratedValue
    private Long id;

    @Override
    public boolean equals(Object other) {
        return other instanceof Identified twin && this.id.equals(twin.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Handled.java" <<'JAVA'
package com.example;

@RestControllerAdvice
class Handled {

    @ExceptionHandler(RuntimeException.class)
    public String onFailure(RuntimeException failure) {
        return failure.getMessage();
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Caller.java" <<'JAVA'
package com.example;

@Component
class Caller {

    private final RestTemplate template;

    Caller(RestTemplateBuilder builder) {
        this.template = builder.build();
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Chained.java" <<'JAVA'
package com.example;

class Chained {

    public void chain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/public/**").permitAll());
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Origins.java" <<'JAVA'
package com.example;

class Origins {

    public void configure(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins("*").allowCredentials(true);
    }
}
JAVA
cat > "$spring_source/src/test/java/com/example/ReplacedTest.java" <<'JAVA'
package com.example;

@DataJpaTest
class ReplacedTest {

    @Test
    void reads() {
    }
}
JAVA
# The two halves of a cross-file defect. Neither source is wrong on its own: an entity is an ordinary
# entity, a controller is an ordinary controller, and a second application class is what every module
# holding one looks like. Only the pair is the defect, which is what the module and reactor goals read.
cat > "$spring_source/src/main/java/com/example/Exposed.java" <<'JAVA'
package com.example;

@RestController
class Exposed {

    @GetMapping("/persisted")
    public List<Persisted> all() {
        return List.of();
    }

    @PostMapping("/persisted")
    public void accept(@RequestBody Persisted persisted) {
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Built.java" <<'JAVA'
package com.example;

class Built {

    Registry own() {
        return new Registry();
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Scoped.java" <<'JAVA'
package com.example;

@Component
@Scope("prototype")
class Scoped {
}
JAVA
cat > "$spring_source/src/main/java/com/example/Sharing.java" <<'JAVA'
package com.example;

@Service
class Sharing {

    Sharing(Scoped scoped) {
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Stored.java" <<'JAVA'
package com.example;

interface Stored extends JpaRepository<Persisted, Long> {
}
JAVA
cat > "$spring_source/src/main/java/com/example/Reaching.java" <<'JAVA'
package com.example;

@RestController
class Reaching {

    Reaching(Stored stored) {
    }
}
JAVA
cat > "$spring_source/src/main/java/com/example/Twin.java" <<'JAVA'
package com.example;

@SpringBootApplication(proxyBeanMethods = false)
class Twin {
}
JAVA
# The runtime settings the same consumer ships. Every rule the configuration check states is broken here
# once, and several are broken by one line on purpose: openInView is read as open-in-view, so the line
# both fails the setting and fails the spelling, which is exactly the confusion the spelling rule is for.
mkdir -p "$spring_source/src/main/resources"
cat > "$spring_source/src/main/resources/application.yml" <<'YML'
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
server:
  port: 8080
spring:
  web:
    error:
      include-stacktrace: always
      include-message: always
  profiles:
    active: production
  main:
    allow-circular-references: true
    allow-bean-definition-overriding: true
  h2:
    console:
      enabled: true
  sql:
    init:
      mode: always
  jpa:
    show-sql: true
    openInView: true
    hibernate:
      ddl-auto: update
  datasource:
    url: jdbc:postgresql://localhost/example
    credential: written-into-the-file
YML
git -C "$spring_source" init --quiet
git -C "$spring_source" config user.name Fixture
git -C "$spring_source" config user.email fixture@example.invalid

run_case 'spring: the added source rules all report' 0 'AirnessSpringTransactionalIsPublic' \
    "$spring_source" checkstyle:check -Dairness.enforce=false
spring_log="$scratch/spring__the_added_source_rules_all_report.log"
for rule in AirnessNoMixedBooleanOperators AirnessSpringSecurityActuatorIsNotPublic AirnessSpringSecurityCsrfIsNotDisabled \
    AirnessSpringSecurityFilterChainIsNotBypassed AirnessSpringSecurityHasNoInMemoryUsers AirnessSpringSecurityHeadersStay \
    AirnessSpringSecurityKeyIsNotALiteral AirnessSpringSecurityPasswordEncoderIsStrong AirnessSpringSecurityPermitAllIsScoped \
    AirnessSpringAsyncIsPublic AirnessSpringAsyncReturnsAFuture \
    AirnessSpringAutowiredOnSoleConstructorIsRedundant AirnessSpringBeanMethodReturnsAValue \
    AirnessSpringCacheAnnotationIsOnAConcreteType AirnessSpringCacheableIsNotCombinedWithCachePut \
    AirnessSpringCacheableIsPublic AirnessSpringConfigurationIsLite \
    AirnessSpringConfigurationPropertiesIsNotAComponent \
    AirnessSpringConfigurationPropertiesIsValidated AirnessSpringJacksonIsTheConfiguredOne \
    AirnessSpringJpaToOneDoesNotCascadeRemove \
    AirnessSpringDataPageMethodTakesAPageable AirnessSpringDataQueryIsBounded \
    AirnessSpringDataQueryIsNotConcatenated AirnessSpringEventListenerIsPublic \
    AirnessSpringEventListenerIsTransactionAware \
    AirnessSpringEventListenerTakesOneArgument AirnessSpringInitBinderReturnsVoid \
    AirnessSpringInjectionIsNotStatic AirnessSpringJpaEntityHasANoArgConstructor \
    AirnessSpringJpaEntityFieldIsNotFinal AirnessSpringJpaEntityIsNotALombokValue \
    AirnessSpringJpaEntityIsNotAnInnerClass AirnessSpringJpaEntityIsNotFinal \
    AirnessSpringJpaEntityIsNotARecord AirnessSpringJpaEntityMethodIsNotFinal \
    AirnessSpringJpaIdStrategyIsExplicit \
    AirnessSpringJpaManyToManyUsesASet AirnessSpringJpaToManyDoesNotCascadeRemove \
    AirnessSpringJpaToOneIsLazy AirnessSpringMethodSecurityIsPublic \
    AirnessSpringModifyingClearsThePersistenceContext AirnessSpringNoLazyCycleBreak \
    AirnessSpringNoManualTransactionControl AirnessSpringNoServiceLocator AirnessSpringNoSetterInjection \
    AirnessSpringNoUnmanagedThreads AirnessSpringPostProcessorBeanIsStatic \
    AirnessSpringProxiedClassIsNotFinal AirnessSpringProxiedMethodIsNotFinal \
    AirnessSpringProxiedMethodIsNotStatic AirnessSpringRetryableIsPublic AirnessSpringScanIsNotRedeclared \
    AirnessSpringScheduleIsConfigurable AirnessSpringScheduledIsPublic \
    AirnessSpringScheduledReturnsVoid \
    AirnessSpringScheduledTakesNoArguments AirnessSpringSingletonHasNoMutableState \
    AirnessSpringTestDoesNotDirtyTheContext AirnessSpringTestIsNotTransactional \
    AirnessSpringTestSharesTheContext AirnessSpringTestTransactionHooksRespectTheContract \
    AirnessSpringTestUsesARealPort AirnessSpringTransactionalDeclaresReadOnly \
    AirnessSpringTransactionalDeclaresTimeout AirnessSpringTransactionalIsNotOnAnInterface \
    AirnessSpringTransactionalIsNotOnTheWebLayer AirnessSpringTransactionalIsPublic \
    AirnessSpringTransactionalRollsBackCheckedExceptions AirnessSpringValidatedIsPublic \
    AirnessSpringWebClientIsABean AirnessSpringWebCrossOriginIsNotWildcard \
    AirnessSpringWebHandlerIsPublic \
    AirnessSpringWebMappingNamesItsMethod AirnessSpringWebParameterConstraintIsEvaluated \
    AirnessSpringWebParameterIsNamed \
    AirnessSpringWebRequestBodyIsValidated AirnessSpringWebSignatureIsNotServletTyped; do
    if grep -q "$rule" "$spring_log"; then
        pass "spring: $rule reports on the fixture"
    else
        fail "spring: $rule reported nothing"
    fi
done

# The bound is claimed two ways and only one of them counts. The pair is read from the same log, because
# an exemption asserted against a rule that has stopped firing asserts nothing.
if grep -E 'Named\.java.*unbounded collection' "$spring_log" >/dev/null; then
    pass 'spring: a bound named in the method no longer stands in for a Pageable'
else
    fail 'spring: a query bounded only by its name was accepted'
fi
if grep -E 'Paged\.java.*unbounded collection' "$spring_log" >/dev/null; then
    fail 'spring: a query taking a Pageable was called unbounded'
else
    pass 'spring: a query taking a Pageable is accepted'
fi
if grep -q 'Use constructor injection instead of field injection' "$spring_log"; then
    pass 'spring: field injection is refused'
else
    fail 'spring: field injection went unreported'
fi

if grep -E 'Retained\.java.*(concrete class must be final|assigned once)' "$spring_log" >/dev/null; then
    fail 'spring: a correct entity was held to the final-class or final-field rule'
else
    pass 'spring: a correct entity is exempt from the final-class and final-field rules'
fi

if grep -E 'Probed\.java.*Permitting the actuator prefix' "$spring_log" >/dev/null; then
    fail 'spring: probes named individually were read as a published actuator'
else
    pass 'spring: probes named individually are accepted'
fi

if grep -E 'Swept\.java.*differ between environments' "$spring_log" >/dev/null; then
    fail 'spring: a schedule read from a placeholder was called a literal'
else
    pass 'spring: a schedule read from a placeholder is accepted'
fi

run_case 'spring: a native query without a reason is refused' 0 'NativeQueryNeedsJustification' \
    "$spring_source" pmd:check -Dairness.enforce=false
# The PMD half of the persistence exemption, which the Checkstyle half had without it. The pair is read
# from one log: the persistent type must draw nothing, and the type beside it that is not persistent must
# draw the rule, because an exemption asserted against a rule that has stopped firing asserts nothing.
spring_pmd_log="$scratch/spring__a_native_query_without_a_reason_is_refused.log"
if grep -E 'Constructed.*ImmutableField' "$spring_pmd_log" >/dev/null; then
    fail 'spring: a persistent field was asked to be final'
else
    pass 'spring: a persistent field is exempt from the immutable-field rule'
fi
if grep -E 'Assembled.*ImmutableField' "$spring_pmd_log" >/dev/null; then
    pass 'spring: the immutable-field rule still reaches a type that is not persistent'
else
    fail 'spring: the immutable-field rule reported nothing, so the exemption proves nothing'
fi

run_case 'spring: a bean method calling another is refused' 1 'builds a second instance' \
    "$spring_source" airness:spring-source
run_case 'spring: an application class below the root is refused' 1 'component scanning starts there' \
    "$spring_source" airness:spring-source

# The two goals report every rule they state, not just the first. So each is run once with enforcement
# withheld, and the single log is then read for one rule at a time. Asserting on the count rather than on
# the text would pass a rule that reported another rule's offence, which is the failure this shape rules
# out: the eleven titles below are distinct sentences, and each names the file that has to have tripped it.
run_case 'spring: the source goal reports every rule it states' 0 'Spring source read' \
    "$spring_source" airness:spring-source -Dairness.enforce=false
spring_source_log="$scratch/spring__the_source_goal_reports_every_rule_it_states.log"
while IFS= read -r rule; do
    if grep -qF "$rule" "$spring_source_log"; then
        pass "spring: the source goal reports $rule"
    else
        fail "spring: the source goal reported nothing for $rule"
    fi
done <<'RULES'
Spring application classes outside the declared package root
Bean methods calling another bean method of the same class
Calls reaching a proxied method from inside the bean that declares it
Calls reaching a proxied method while the bean is still being built
Beans assigning a static field of their own
Entities deciding equality by a generated identifier
Exception handlers copying the exception into the response
HTTP clients built with no connect or read timeout
Filter chains naming no terminal request matcher
Credentialed requests accepted from a wildcard origin
Persistence tests run against a database the application never uses
RULES

run_case 'spring: the configuration goal reports every rule it states' 0 'Spring configuration read' \
    "$spring_source" airness:spring-configuration -Dairness.enforce=false
spring_configuration_log="$scratch/spring__the_configuration_goal_reports_every_rule_it_states.log"
while IFS= read -r rule; do
    if grep -qF "$rule" "$spring_configuration_log"; then
        pass "spring: the configuration goal reports $rule"
    else
        fail "spring: the configuration goal reported nothing for $rule"
    fi
done <<'RULES'
management.endpoints.web.exposure.include: the actuator is exposed at a wildcard
management.endpoint.health.show-details: the health payload names every component
spring.web.error.include-stacktrace: an unhandled error returns its stack trace
spring.web.error.include-message: an unhandled error returns its internal message
spring.main.allow-circular-references: circular references are refused by default
spring.main.allow-bean-definition-overriding: two definitions of one name
spring.h2.console.enabled: this publishes a database console
spring.jpa.show-sql: every statement is written to standard output
spring.profiles.active: the artifact chooses its own environment
spring.sql.init.mode: schema.sql and data.sql then run on every startup
spring.jpa.hibernate.ddl-auto is not set as it has to be
spring.jpa.open-in-view is not set as it has to be
spring.datasource.hikari.max-lifetime is not set as it has to be
spring.datasource.hikari.leak-detection-threshold is not set as it has to be
spring.datasource.hikari.maximum-pool-size is not set as it has to be
spring.datasource.hikari.connection-timeout is not set as it has to be
server.shutdown is not set as it has to be
openInView is not written in kebab-case
credential carries a literal secret
no dependency on the compile classpath published a spring-configuration-metadata.json
RULES

# The rules above judge a value. These judge the key itself, against the metadata the dependencies
# publish about what they bind, so this fixture is the one Spring fixture that has to resolve a starter.
# Every rule is broken once: include-message was withdrawn outright in Boot 4, show-banner is deprecated
# and still bound, shutdwon is a typo under a namespace the classpath declares, and shutdown is written
# twice in one document where only the last of them is read.
spring_settings="$scratch/spring-settings"
mkdir -p "$spring_settings/src/main/resources"
cat > "$spring_settings/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent-spring-boot</artifactId>
    <version>1.0.7-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>spring-settings</artifactId>
  <version>1.0.0</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>
POM
cat > "$spring_settings/src/main/resources/application.yml" <<'YML'
server:
  error:
    include-message: never
  shutdown: graceful
  shutdown: graceful
  shutdwon: graceful
spring:
  main:
    show-banner: false
acme:
  retry:
    attempts: 3
YML
git -C "$spring_settings" init --quiet
git -C "$spring_settings" config user.name Fixture
git -C "$spring_settings" config user.email fixture@example.invalid

run_case 'spring: a setting the container no longer binds is refused' 1 'stopped binding' \
    "$spring_settings" airness:spring-configuration
run_case 'spring: the metadata rules all report' 0 'Spring configuration read' \
    "$spring_settings" airness:spring-configuration -Dairness.enforce=false
spring_settings_log="$scratch/spring__the_metadata_rules_all_report.log"
while IFS= read -r rule; do
    if grep -qF "$rule" "$spring_settings_log"; then
        pass "spring: the configuration goal reports $rule"
    else
        fail "spring: the configuration goal reported nothing for $rule"
    fi
done <<'RULES'
include-message names a setting the container has stopped binding
write spring.web.error.include-message instead
show-banner names a setting its supplier has deprecated
write spring.main.banner-mode instead
shutdwon is not a setting anything on the classpath declares
shutdown is declared more than once in this document
RULES

# A key beneath a namespace no dependency declares is the project's own, and saying otherwise would
# leave a project unable to bind anything it did not inherit.
if grep -qF 'acme.retry.attempts' "$spring_settings_log"; then
    fail 'spring: a setting of the project own namespace was judged against somebody else metadata'
else
    pass 'spring: a setting under an undeclared namespace is left alone'
fi

run_case 'spring: an entity carried by a web signature is refused' 1 'republishes the schema' \
    "$spring_source" airness:spring-module
run_case 'spring: an entity bound from a request body is refused' 1 'every column' \
    "$spring_source" airness:spring-module
run_case 'spring: a second application class is refused' 1 'whichever the search finds first' \
    "$spring_source" airness:spring-reactor

# The module goal states nine rules and the two cases above prove one of them, so it is run once more
# with enforcement withheld and the single log read one headline at a time. Each headline below names a
# defect whose two halves are in different files of the fixture: a component annotated in one and built
# in another, a scope declared in one and ignored in another, a profile activated in a test and answered
# by no file at all. Asserting on the count instead would pass a rule that reported another rule's work.
run_case 'spring: the module goal reports every rule it states' 0 'Spring module read' \
    "$spring_source" airness:spring-module -Dairness.enforce=false
spring_module_log="$scratch/spring__the_module_goal_reports_every_rule_it_states.log"
while IFS= read -r rule; do
    if grep -qF "$rule" "$spring_module_log"; then
        pass "spring: the module goal reports $rule"
    else
        fail "spring: the module goal reported nothing for $rule"
    fi
done <<'RULES'
Persistence entities carried by a web request or response
Controllers holding the repository layer directly
Components built with new rather than taken from the container
Prototype beans injected into a singleton that is built once
Method security annotations that nothing in the module enables
Asynchronous methods left to an executor that pools nothing
Test profiles activated with nothing to activate
RULES

# The ninth rule is the one this fixture cannot break, and that is the assertion: the module declares a
# @RestControllerAdvice, and one advice answers for every controller of the module it is declared in.
if grep -qF "left to the framework's own error page" "$spring_module_log"; then
    fail 'spring: the error handler rule fired at a module that declares an advice'
else
    pass 'spring: an advice anywhere in the module answers for every controller in it'
fi

# So the rule gets a module of its own: one controller and nothing else, which is what a project looks
# like on the day its first endpoint is written and its error contract has not been decided yet.
spring_unadvised="$scratch/spring-unadvised"
mkdir -p "$spring_unadvised/src/main/java/com/example"
sed -e 's|<artifactId>spring-source</artifactId>|<artifactId>spring-unadvised</artifactId>|' \
    "$spring_source/pom.xml" > "$spring_unadvised/pom.xml"
cat > "$spring_unadvised/src/main/java/com/example/Only.java" <<'JAVA'
package com.example;

@RestController
class Only {

    @GetMapping("/only")
    public String read() {
        return "";
    }
}
JAVA
git -C "$spring_unadvised" init --quiet
git -C "$spring_unadvised" config user.name Fixture
git -C "$spring_unadvised" config user.email fixture@example.invalid

run_case 'spring: controllers with no error handler are refused' 1 'default error page' \
    "$spring_unadvised" airness:spring-module

# The model goal answers from the dependency list and the plugin bindings, so the fixture that breaks it
# is a pom rather than a tree of sources. Every rule is broken once: the tooling is declared without
# optional, the schema is mapped with no migration tool, the actuator is absent, nothing authenticates a
# caller, both web stacks are declared together, and the application registers auto-configuration with
# itself. Four of the six ask first whether the module is deployed, which the repackage binding below is
# what says.
spring_model="$scratch/spring-model"
mkdir -p "$spring_model/src/main/resources/META-INF/spring"
cat > "$spring_model/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>eu.ciechanowiec</groupId>
    <artifactId>airness-parent-spring-boot</artifactId>
    <version>1.0.7-SNAPSHOT</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>spring-model</artifactId>
  <version>1.0.0</version>
  <properties>
    <airness.package.root>com.example</airness.package.root>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-devtools</artifactId>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webflux</artifactId>
      <scope>compile</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <executions>
          <execution>
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
printf 'com.example.Own\n' \
    > "$spring_model/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
git -C "$spring_model" init --quiet
git -C "$spring_model" config user.name Fixture
git -C "$spring_model" config user.email fixture@example.invalid

run_case 'spring: the model goal reports every rule it states' 0 'Spring model read' \
    "$spring_model" airness:spring-model -Dairness.enforce=false
spring_model_log="$scratch/spring__the_model_goal_reports_every_rule_it_states.log"
while IFS= read -r rule; do
    if grep -qF "$rule" "$spring_model_log"; then
        pass "spring: the model goal reports $rule"
    else
        fail "spring: the model goal reported nothing for $rule"
    fi
done <<'RULES'
Development tooling declared in a way that lets it ship
A mapped schema with nothing declared that would create it
A deployable application publishing nothing an orchestrator can read
A deployable application serving HTTP with nothing that authenticates a caller
Both web stacks declared where only one of them can start
Auto-configuration declared inside the application it configures
RULES

# The same rules against a module that is not the deployed one. Four of the six have nothing to say
# there, which is what keeps a library of a Spring Boot project from being asked to publish probes.
run_case 'spring: a module that is not repackaged answers fewer of the model rules' 0 \
    'not repackaged' "$spring_source" airness:spring-model -Dairness.enforce=false
if grep -qE 'mapped schema|orchestrator can read|Auto-configuration declared' \
    "$scratch/spring__a_module_that_is_not_repackaged_answers_fewer_of_the_model_rules.log"; then
    fail 'spring: a rule scoped to the deployed module reached a library'
else
    pass 'spring: the rules that ask about a deployment ask only where there is one'
fi


# The same sources under airness-parent. The Spring rules are suppressed there, and the generic rule that
# grew a Spring annotation is not, which is what says the relaxations reach only a Spring Boot project.
plain_spring="$scratch/plain-spring"
mkdir -p "$plain_spring"
cp -R "$spring_source/src" "$plain_spring/src"
sed -e 's|<artifactId>airness-parent-spring-boot</artifactId>|<artifactId>airness-parent</artifactId>|' \
    -e 's|<artifactId>spring-source</artifactId>|<artifactId>plain-spring</artifactId>|' \
    "$spring_source/pom.xml" > "$plain_spring/pom.xml"
git -C "$plain_spring" init --quiet
git -C "$plain_spring" config user.name Fixture
git -C "$plain_spring" config user.email fixture@example.invalid

run_case 'spring: a plain consumer still refuses field injection' 0 \
    'Use constructor injection instead of field injection' \
    "$plain_spring" checkstyle:check -Dairness.enforce=false
plain_log="$scratch/spring__a_plain_consumer_still_refuses_field_injection.log"
if grep -q 'AirnessSpring' "$plain_log"; then
    fail 'spring: a Spring rule reached a project that is not a Spring Boot one'
else
    pass 'spring: the Spring rules stay suppressed outside a Spring Boot project'
fi
if grep -q 'A concrete class must be final' "$plain_log"; then
    pass 'spring: the final-class rule keeps its Spring exemption to the Spring parent'
else
    fail 'spring: the final-class relaxation leaked outside a Spring Boot project'
fi

# A Spring Boot application built and run the way one ships. This is the only case that resolves the
# platform, and the only one that starts the archive rather than reading it: the two defects it was
# written after, a split test framework and an annotation left out of the repackaged jar, both passed
# every source and artifact check and appeared for the first time in a running process.
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
prepare "$spring_app" --quiet airness:assets-sync
# No format step, deliberately, and the fixture verifies without one. That is the guard on the Java 25
# recipe set: spring-boot-starter-test carries Mockito transitively, and while the upstream migration
# wired Mockito's agent into surefire, every Spring Boot project failed its first build until it had
# accepted that wiring into its own project file. A format step here would absorb the same thing
# silently if it ever came back.
git -C "$spring_app" add --all
git -C "$spring_app" commit --quiet \
    --message 'test(it): create a Spring Boot consumer fixture' \
    --message 'The fixture carries one bean and one entry point, so a consumer build has something to report on.'

run_case 'spring: a conforming Spring Boot application verifies' 0 'BUILD SUCCESS' \
    "$spring_app" clean verify
# The model goal is bound rather than invoked, so this is where that binding is proven. Every other case
# runs a goal from the command line, which says nothing about the phase a consumer would meet it at, and
# this is the one consumer here that runs a whole lifecycle. It passing is the other half of the claim:
# the fixture declares the actuator a repackaged module has to, so the rule is satisfiable as well as real.
if grep -q 'airness-spring-model' \
    "$scratch/spring__a_conforming_Spring_Boot_application_verifies.log"; then
    pass 'spring: the model goal runs from its validate binding rather than from a command line'
else
    fail 'spring: the model goal never ran in a full consumer build'
fi

spring_jar="$spring_app/target/spring-app-1.0.0.jar"
if unzip -l "$spring_jar" 2>/dev/null | grep -q 'jspecify'; then
    pass 'spring: the repackaged archive carries the annotations the container reads'
else
    fail 'spring: the repackaged archive omits the annotations the container reads'
fi
spring_run="$scratch/spring-app-run.log"
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
if grep -q 'Started Application' "$spring_run"; then
    pass 'spring: the repackaged archive starts from a package-private main'
else
    fail 'spring: the repackaged archive did not start'
    sed -n '1,220p' "$spring_run" >&2
fi

# The entry point of the same application, read by the inspection engine. It holds nothing but its main,
# and UtilityClassWithoutPrivateConstructor reported it until the profile named the annotation: every
# repair the finding offers is closed to a Spring Boot project, because the container instantiates the
# class, so a private constructor stops the application from starting and Lombok @UtilityClass makes the
# type uninstantiable outright. That left a suppression as the only project-side answer, spent on a
# construct every project of this kind carries. The exemption is keyed on the annotation, which is what
# checkstyle.xml already keys its own on, and this is the only fixture that resolves it, so this is the
# only place the exemption can be read at all. It costs a second container run, and the run is what says
# the option reached the engine: the list is read by a JDOM externalizer of its own, so a shape borrowed
# from a neighbouring option parses as XML, is ignored in silence, and leaves the finding standing.
#
# This fixture is meant to carry no finding at all, so unlike the profile cases above it can answer
# through an exit code, and that is the reading that says the scan ran rather than that it found nothing
# because it never started. The report is read after it for the one finding this case exists for, so a
# failure names the regression instead of only the build that carried it.
if ! docker info >/dev/null 2>&1; then
    echo 'skipped  spring: no Docker daemon answered, so the entry-point case did not run'
else
    run_case 'spring: a conforming Spring Boot application passes the inspection engine' 0 'BUILD SUCCESS' \
        "$spring_app" airness:qodana
    spring_sarif="$spring_app/target/qodana/qodana.sarif.json"
    if [ -f "$spring_sarif" ] && ! grep -q '"ruleId": "UtilityClassWithoutPrivateConstructor"' "$spring_sarif"; then
        pass 'spring: the entry point is not read as a utility class'
    else
        fail 'spring: the entry point is still read as a utility class'
    fi
fi

# Published assets contain the pinned software guideline but no other documentation or Git-hook material.
assets="$local_repository/eu/ciechanowiec/airness-assets/$harness_version/airness-assets-$harness_version.jar"
listing="$scratch/assets.txt"
jar tf "$assets" > "$listing"
if ! grep -Fq 'airness/files/README-guideline-software-project.adoc.asset' "$listing"; then
    fail 'assets: published jar omitted the software project guideline'
elif grep -Ev 'airness/files/README-guideline-software-project[.]adoc[.]asset$' "$listing" \
    | grep -Eq '(^|/)(\.vale|\.docs|docinfo|README|githooks|lint-docs)'; then
    fail 'assets: documentation or hooks leaked into the published jar'
else
    pass 'assets: published jar contains only the pinned guideline'
fi

# The failed cases are named once more here. After two hundred lines of scroll the reader needs the list
# collected rather than found again, and the log excerpt for each is already above under its own domain.
total=$((passed + failures))
took="$(elapsed "$(($(date +%s) - started))")"

printf '\n  %s%s%s\n' "$style_dim" \
    '------------------------------------------------------------' "$style_off"
if [ "$failures" -ne 0 ]; then
    printf '  %sFailed%s\n' "$style_bold" "$style_off"
    printf '%s' "$failed_cases" | while IFS= read -r case_label; do
        if [ -n "$case_label" ]; then
            printf '    %s\n' "$case_label"
        fi
    done
    printf '\n  %s%s of %s cases failed%s, %s\n' \
        "$style_fail" "$failures" "$total" "$style_off" "$took"
    exit 1
fi
printf '  %sall %s cases passed%s, %s\n' "$style_pass" "$total" "$style_off" "$took"
