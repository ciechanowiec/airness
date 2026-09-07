#!/usr/bin/env sh

run_maven_cases() {
    new_consumer maven-consumer
    maven_consumer="$consumer_directory"

    run_maven profile_extended maven "$maven_consumer" validate -Pextended -DskipTests
    expect_exit profile_extended 'profiles: the installed parent accepts its Extended profile' 0
    expect_match profile_extended 'profiles: the inherited profile reaches preflight' \
        'airness:[^ ]+:required-profiles \(airness-preflight\)'

    run_maven profile_extended_offline maven "$maven_consumer" validate -Pextended -o
    expect_exit profile_extended_offline 'profiles: Extended verification refuses an offline build' 1
    expect_match profile_extended_offline 'profiles: the offline refusal names the scan it protects' \
        'offline, so Maven skips the vulnerability scan'

    run_maven profile_deactivated maven "$maven_consumer" validate '-P!format' -DskipTests
    expect_exit profile_deactivated 'profiles: the declared format profile can be deactivated' 0

    run_maven profile_missing maven "$maven_consumer" \
        validate -Pmissing-profile -DskipTests -Dairness.enforce=false
    expect_exit profile_missing 'profiles: a missing activation fails in bypass modes' 1
    expect_match profile_missing 'profiles: the missing activation is named precisely' \
        'requested profiles \[missing-profile\].*do not exist'

    run_maven profile_missing_deactivation maven "$maven_consumer" \
        validate '-P!missing-profile' -DskipTests -Dairness.enforce=false
    expect_exit profile_missing_deactivation 'profiles: a missing deactivation fails in bypass modes' 1
    expect_match profile_missing_deactivation 'profiles: the missing deactivation is named precisely' \
        'requested profiles \[missing-profile\].*do not exist'

    run_maven lifecycle_clean maven "$maven_consumer" clean verify
    expect_exit lifecycle_clean 'lifecycle: a clean installed-parent consumer verifies' 0
    expect_match lifecycle_clean 'lifecycle: Checkstyle is selected from the installed parent' \
        'checkstyle:[^:]+:check'
    expect_match lifecycle_clean 'lifecycle: PMD and CPD are both bound' \
        'pmd:[^:]+:cpd-check'
    expect_match lifecycle_clean 'lifecycle: compiler metadata is checked after compilation' \
        'airness:[^ ]+:compiler-parameters \(airness-compiler-parameters\)'
    expect_match lifecycle_clean 'lifecycle: current-build coverage evidence is checked' \
        'airness:[^ ]+:coverage-evidence \(airness-coverage-evidence\)'
    expect_match lifecycle_clean 'lifecycle: the final archive is inspected' \
        'airness:[^ ]+:artifact-content \(airness-artifact\)'
    expect_match lifecycle_clean 'lifecycle: tree state is snapshotted before work' \
        'airness:[^ ]+:tree-snapshot \(airness-preflight\)'
    expect_match lifecycle_clean 'lifecycle: tree state is verified after work' \
        'airness:[^ ]+:tree-verify \(airness-governance\)'
    expect_match lifecycle_clean 'lifecycle: software refused by name is checked before the version check' \
        'airness:[^ ]+:blocklist \(airness-blocklist\)'

    run_blocklist_boundaries
    run_license_alias_case
    run_suppression_boundaries
}

# The licence check reads what a pom says about itself, and none of these five says anything: the
# MongoDB driver is Apache 2.0, and an image, a workflow distribution, and a Testcontainers literal have
# no pom at all. The goal is invoked directly, so the test source need not compile.
run_blocklist_boundaries() {
    new_consumer blocklist-consumer
    blocklist_consumer="$consumer_directory"
    blocklist_dependency="$(cat <<'XML'
  <dependencies>
    <dependency>
      <groupId>org.mongodb</groupId>
      <artifactId>mongodb-driver-sync</artifactId>
      <version>5.3.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
XML
)"
    BLOCKLIST_DEPENDENCY="$blocklist_dependency" perl -0pi -e \
        's{</project>}{$ENV{BLOCKLIST_DEPENDENCY}."\n</project>"}e' "$blocklist_consumer/pom.xml"
    printf 'FROM redis:7.4.1\n' > "$blocklist_consumer/Dockerfile"
    printf 'services:\n  store:\n    image: mongo:7.0.14\n' > "$blocklist_consumer/compose.yaml"
    mkdir -p "$blocklist_consumer/.github/workflows"
    cat > "$blocklist_consumer/.github/workflows/build.yml" <<'YAML'
jobs:
  build:
    steps:
      - uses: actions/setup-java@v4
        with:
          distribution: oracle
YAML
    cat > "$blocklist_consumer/src/test/java/com/example/ImageTest.java" <<'JAVA'
package com.example;

import org.testcontainers.utility.DockerImageName;

final class ImageTest {

    static final DockerImageName STORE = DockerImageName.parse("mongo:7");

    private static final String CACHE_IMAGE = "redis:7.4.1";

    static final DockerImageName CACHE = DockerImageName.parse(CACHE_IMAGE);
}
JAVA

    run_maven blocklist_report_only maven "$blocklist_consumer" airness:blocklist -Dairness.enforce=false
    expect_exit blocklist_report_only 'blocklist: refused software reports without failing' 0
    expect_match blocklist_report_only 'blocklist: the declared driver is named where it is written' \
        'pom[.]xml: org[.]mongodb:mongodb-driver-sync:5[.]3[.]0 - the MongoDB server is SSPL'
    expect_match blocklist_report_only 'blocklist: the resolved driver core is named in the resolved set' \
        'pom[.]xml [(]resolved set[)]: org[.]mongodb:mongodb-driver-core:5[.]3[.]0'
    expect_match blocklist_report_only 'blocklist: the Dockerfile image is named by line' \
        'Dockerfile:1: redis:7[.]4[.]1 - Redis 7[.]4 onward'
    expect_match blocklist_report_only 'blocklist: the compose image is named with its service' \
        'compose[.]yaml:3 [(]service store[)]: mongo:7[.]0[.]14'
    expect_match blocklist_report_only 'blocklist: the workflow distribution is named' \
        'build[.]yml:6: oracle - Oracle JDK'
    expect_match blocklist_report_only 'blocklist: the Testcontainers literal is named by line' \
        'ImageTest[.]java:7: mongo:7 - the MongoDB server is SSPL'
    expect_match blocklist_report_only 'blocklist: an image a constant of the same source holds is read too' \
        'ImageTest[.]java:11: redis:7[.]4[.]1 - Redis 7[.]4 onward'
    expect_match blocklist_report_only 'blocklist: every refusal names a replacement' \
        'use PostgreSQL through spring-boot-starter-data-jpa'

    run_maven blocklist_enforcement maven "$blocklist_consumer" airness:blocklist
    expect_exit blocklist_enforcement 'blocklist: refused software fails enforcement' 1

    new_consumer blocklist-unpinned
    unpinned_consumer="$consumer_directory"
    printf 'FROM postgres\n' > "$unpinned_consumer/Dockerfile"
    run_maven blocklist_unpinned maven "$unpinned_consumer" airness:blocklist -Dairness.enforce=false
    expect_exit blocklist_unpinned 'blocklist: an open image nothing pins reports without failing' 0
    expect_match blocklist_unpinned 'blocklist: the missing pin is named' \
        'Dockerfile:1: postgres - nothing pins what this pulls'
}

# Published POMs spell allowed licenses differently. JobRunr also offers a commercial alternative.
# The real goal must recognize the open license without a consumer merge or an artifact exemption.
run_license_alias_case() {
    new_consumer xmp-license-consumer
    license_consumer="$consumer_directory"
    license_dependency="$(cat <<'XML'
  <dependencies>
    <dependency>
      <groupId>com.adobe.xmp</groupId>
      <artifactId>xmpcore</artifactId>
      <version>6.1.11</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.jobrunr</groupId>
      <artifactId>jobrunr</artifactId>
      <version>8.8.2</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
XML
)"
    LICENSE_DEPENDENCY="$license_dependency" perl -0pi -e \
        's{</project>}{$ENV{LICENSE_DEPENDENCY}."\n</project>"}e' "$license_consumer/pom.xml"
    run_maven license_bsd3_alias maven "$license_consumer" license:add-third-party
    expect_exit license_bsd3_alias 'licenses: the published Adobe BSD3 alias is recognized' 0
    expect_match license_bsd3_alias 'licenses: the installed license goal actually ran' \
        'license:[^:]+:add-third-party'
    expect_match license_bsd3_alias 'licenses: JobRunr selects its allowed LGPL alternative' \
        "Commercial License.*org.jobrunr:jobrunr.*also licensed under 'LGPL-3.0'"
}

run_suppression_boundaries() {
    new_consumer suppression-policy
    suppression_consumer="$consumer_directory"
    suppression_document="$suppression_consumer/.airness/dependency-check-suppressions.xml"
    cat > "$suppression_document" <<'XML'
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
    <suppress>
        <notes>The affected parser is absent. Added 2026-09-07.</notes>
        <packageUrl regex="true">^pkg:maven/org.example/library@.*$</packageUrl>
        <cve>CVE-2020-27225</cve>
    </suppress>
</suppressions>
XML
    run_maven suppression_literal maven "$suppression_consumer" validate
    expect_exit suppression_literal 'suppressions: preflight accepts named advisories and dependency patterns' 0
    cat > "$suppression_document" <<'XML'
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
    <suppress>
        <notes>The affected parser is absent. Added 2026-09-07.</notes>
        <cve>CVE-2020-27225</cve>
        <vulnerabilityName regex="true">.*</vulnerabilityName>
    </suppress>
</suppressions>
XML
    run_maven suppression_blanket maven "$suppression_consumer" validate
    expect_exit suppression_blanket 'suppressions: a named CVE cannot hide a blanket exception' 1
    expect_match suppression_blanket 'suppressions: the rejection asks for literal advisory names' 'literal advisory'
    cat > "$suppression_document" <<'XML'
<dc:suppressions xmlns:dc="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.4.xsd">
    <dc:suppressionGroup name="examples">
        <dc:suppress>
            <dc:notes>The affected parser is absent. Added 2026-09-07.</dc:notes>
            <dc:cve>CVE-2020-27225</dc:cve>
            <dc:cvssBelow>7</dc:cvssBelow>
        </dc:suppress>
    </dc:suppressionGroup>
</dc:suppressions>
XML
    run_maven suppression_group maven "$suppression_consumer" validate
    expect_exit suppression_group 'suppressions: groups and prefixes cannot hide a score threshold' 1
    expect_match suppression_group 'suppressions: the rejection identifies the broad selector' 'replace cvssBelow'
}
