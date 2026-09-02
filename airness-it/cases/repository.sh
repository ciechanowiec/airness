#!/usr/bin/env sh

run_repository_cases() {
    run_formatting_boundaries
    run_tree_boundary
    run_report_only_boundaries
    run_artifact_boundaries
    run_coverage_boundary
    run_git_boundaries
    check_published_assets
}

run_formatting_boundaries() {
    new_consumer formatting-state
    formatting_consumer="$consumer_directory"
    mkdir -p "$formatting_consumer/src/main/resources/static"
    cat > "$formatting_consumer/src/main/java/com/example/Formatting.java" <<'JAVA'
package com.example;
final class Formatting { static String value(){return "value";} }
JAVA
    cat > "$formatting_consumer/src/main/resources/static/style.css" <<'CSS'
.pill {
color : red ;
     background:blue}
CSS
    run_maven formatting_before repository "$formatting_consumer" process-resources
    expect_exit formatting_before 'formatting: unformatted source or stylesheet is rejected' 1
    expect_match formatting_before 'formatting: the check names formatting rather than compilation' \
        'Incorrectly formatted file|Java sources that do not match|rewrite'

    run_maven formatting_write repository "$formatting_consumer" \
        process-resources -Pformat -Dairness.enforce=false
    expect_exit formatting_write 'formatting: the installed format profile rewrites both kinds' 0

    run_maven formatting_after repository "$formatting_consumer" process-resources
    expect_exit formatting_after 'formatting: the rewritten consumer then passes the same phase' 0
    expect_no_match formatting_after 'formatting: no stale formatter finding survives the write' \
        'Incorrectly formatted file|Java sources that do not match'
}

run_tree_boundary() {
    new_consumer tree-state
    tree_consumer="$consumer_directory"
    tree_profile="$(cat <<'XML'
  <profiles>
    <profile>
      <id>tree-drift</id>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-antrun-plugin</artifactId>
            <version>3.2.0</version>
            <executions>
              <execution>
                <id>mutate-after-snapshot</id>
                <phase>process-resources</phase>
                <goals><goal>run</goal></goals>
                <configuration>
                  <target>
                    <echo file="${project.basedir}/AGENTS.md" append="true">lifecycle drift</echo>
                  </target>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
XML
)"
    TREE_PROFILE="$tree_profile" perl -0pi -e \
        's{</project>}{$ENV{TREE_PROFILE}."\n</project>"}e' "$tree_consumer/pom.xml"
    prepare_maven tree_fixture_format repository "$tree_consumer" validate editorconfig:format
    run_maven tree_mutation repository "$tree_consumer" clean package -Ptree-drift
    expect_exit tree_mutation 'tree: a mutation after the same lifecycle snapshot fails verification' 1
    expect_match tree_mutation 'tree: the changed tracked file is named' \
        'Committable files changed during the build|working tree content differs|AGENTS[.]md'
}

run_report_only_boundaries() {
    new_consumer report-only-compilation
    compilation_consumer="$consumer_directory"
    cat > "$compilation_consumer/src/main/java/com/example/Broken.java" <<'JAVA'
package com.example;

final class Broken {
    this is not Java
}
JAVA
    run_maven compilation_report_only repository "$compilation_consumer" \
        clean package -Dairness.enforce=false
    expect_exit compilation_report_only 'report-only: compilation failure remains fatal' 1
    expect_match compilation_report_only 'report-only: the fatal compiler failure is visible' \
        'COMPILATION ERROR|BUILD FAILURE'

    new_consumer report-only-test
    test_consumer="$consumer_directory"
    cat > "$test_consumer/src/test/java/com/example/ExampleTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExampleTest {

    @Test
    void reportsFailure() {
        assertEquals(2, new Example().value());
    }
}
JAVA
    run_maven test_report_only repository "$test_consumer" clean package -Dairness.enforce=false
    expect_exit test_report_only 'report-only: a test finding remains reportable without stopping later checks' 0
    expect_match test_report_only 'report-only: the failed test remains visible' 'Failures: 1'
    expect_match test_report_only 'report-only: later current-build evidence still executes' \
        'airness:[^ ]+:coverage-evidence \(airness-coverage-evidence\)'
}

run_artifact_boundaries() {
    new_consumer artifact-content
    artifact_consumer="$consumer_directory"
    mkdir -p "$artifact_consumer/src/main/resources/.idea"
    printf 'local workspace metadata\n' \
        > "$artifact_consumer/src/main/resources/.idea/workspace.xml"
    prepare_maven artifact_build repository "$artifact_consumer" --quiet resources:resources jar:jar -DskipTests
    run_maven artifact_reject repository "$artifact_consumer" airness:artifact-content
    expect_exit artifact_reject 'artifact: development metadata in the finished JAR is rejected' 1
    expect_match artifact_reject 'artifact: the packaged path is named precisely' \
        'Source or development files packaged in the JAR|[.]idea/workspace[.]xml'

    new_consumer repackaged-content
    repackaged_consumer="$consumer_directory"
    mkdir -p "$repackaged_consumer/packaging"
    printf 'local workspace metadata\n' > "$repackaged_consumer/packaging/workspace.xml"
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
            <goals><goal>shade</goal></goals>
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
    SHADE_BLOCK="$shade_block" perl -0pi -e \
        's{</project>}{$ENV{SHADE_BLOCK}."\n</project>"}e' "$repackaged_consumer/pom.xml"
    run_maven artifact_repackaged repository "$repackaged_consumer" \
        clean verify -Dairness.enforce=false
    expect_exit artifact_repackaged 'artifact: report-only permits inspection of the repackaged archive' 0
    expect_match artifact_repackaged 'artifact: the final repackaged archive is the one inspected' \
        'Source or development files packaged in the JAR'
}

run_coverage_boundary() {
    new_consumer coverage-current-build
    coverage_consumer="$consumer_directory"
    rm "$coverage_consumer/src/test/java/com/example/ExampleTest.java"
    run_maven coverage_missing repository "$coverage_consumer" clean package
    expect_exit coverage_missing 'coverage: a production module with no current test evidence fails' 1
    expect_match coverage_missing 'coverage: the current-build evidence failure is explicit' \
        'Missing current-build JaCoCo evidence'

    run_maven coverage_report_only repository "$coverage_consumer" \
        clean package -Dairness.enforce=false
    expect_exit coverage_report_only 'coverage: missing current evidence remains visible in report-only mode' 0
    expect_match coverage_report_only 'coverage: report-only does not hide the current-build finding' \
        'Missing current-build JaCoCo evidence'
}

run_git_boundaries() {
    new_consumer git-linear
    git_linear="$consumer_directory"
    run_maven git_linear repository "$git_linear" airness:linear-history
    expect_exit git_linear 'history: a real linear consumer repository passes' 0
    expect_match git_linear 'history: the full history was actually read' 'Linear history read [0-9]+ commit'

    git_shallow="$scratch/git-shallow"
    git clone --quiet --depth 1 "file://$git_linear" "$git_shallow"
    run_maven git_shallow repository "$git_shallow" airness:require-full-history
    expect_exit git_shallow 'history: a real shallow clone is rejected' 1
    expect_match git_shallow 'history: the shallow topology is named' 'This is a shallow clone'

    new_consumer git-merged
    git_merged="$consumer_directory"
    git -C "$git_merged" checkout --quiet -b side
    git -C "$git_merged" commit --quiet --allow-empty \
        --message 'feat(core): record a side commit to merge back'
    git -C "$git_merged" checkout --quiet -
    git -C "$git_merged" merge --quiet --no-ff side --message "Merge branch 'side'"
    run_maven git_merge repository "$git_merged" \
        airness:linear-history airness:commit-history -Dairness.enforce=false
    expect_exit git_merge 'history: merge topology and its header report without stopping the batch' 0
    expect_match git_merge 'history: the real merge topology is rejected' 'Merge commits in the history'
    expect_match git_merge 'history: the merge header is read like every other header' \
        'Commit messages that break the policy'
    expect_count git_merge 'history: both requested installed Git goals executed' \
        'airness:[^ ]+:(linear-history|commit-history) \(default-cli\)' 2

    new_consumer git-complete-history
    git_complete="$consumer_directory"
    git -C "$git_complete" commit --quiet --allow-empty --message 'wip'
    git -C "$git_complete" commit --quiet --allow-empty \
        --message 'test(core): record a later compliant history entry'
    run_maven git_complete repository "$git_complete" airness:commit-history
    expect_exit git_complete 'history: a later good commit cannot hide an earlier bad one' 1
    expect_match git_complete 'history: complete-history evidence names the bad header' \
        'Commit messages that break the policy|wip'
}

check_published_assets() {
    assets="$local_repository/eu/ciechanowiec/airness-assets/$harness_version/airness-assets-$harness_version.jar"
    listing="$scratch/assets.txt"
    jar tf "$assets" > "$listing"
    if grep -Fq 'airness/files/README-guideline-software-project.adoc.asset' "$listing" \
        && ! grep -Ev 'airness/files/README-guideline-software-project[.]adoc[.]asset$' "$listing" \
            | grep -Eq '(^|/)([.]vale|[.]docs|docinfo|README|githooks|lint-docs)'; then
        pass 'assets: the published JAR contains only the pinned guideline documentation'
    else
        fail 'assets: the published JAR content is not the pinned guideline-only shape'
    fi
}
