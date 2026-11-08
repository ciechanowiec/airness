#!/usr/bin/env sh

run_repository_cases() {
    run_build_input_cases
    run_formatting_boundaries
    run_binary_formatting_boundary
    run_indentation_boundary
    run_ignored_output_boundary
    run_tree_boundary
    run_report_only_boundaries
    run_artifact_boundaries
    run_artifact_manifest_boundary
    run_packaged_output_boundary
    run_coverage_boundary
    run_git_boundaries
    run_managed_file_cases
    check_published_assets
}

run_formatting_boundaries() {
    new_consumer formatting-state
    formatting_consumer="${consumer_directory}"
    mkdir -p "${formatting_consumer}/src/main/resources/static"
    cat > "${formatting_consumer}/src/main/java/com/example/Formatting.java" <<'JAVA'
package com.example;
final class Formatting { static String value(){return "value";} }
JAVA
    cat > "${formatting_consumer}/src/main/resources/static/style.css" <<'CSS'
.pill {
color : red ;
     background:blue}
CSS
    # A stylesheet a Java module serves belongs to a frontend tooling project; the module itself is one here.
    write_node_consumer "${formatting_consumer}" '.' ''
    printf '{\n  "name": "formatting-state",\n  "private": true,\n  "airness": { "sourceRoots": ["src/main/resources/static"] }\n}\n' \
        > "${formatting_consumer}/package.json"
    prepare_maven formatting_assets repository "${formatting_consumer}" --quiet airness:assets-sync
    run_maven formatting_before repository "${formatting_consumer}" process-resources
    expect_exit formatting_before 'formatting: unformatted source or stylesheet is rejected' 1
    expect_match formatting_before 'formatting: the check names formatting rather than compilation' \
        'Incorrectly formatted file|Java sources that do not match'

    run_maven formatting_write repository "${formatting_consumer}" \
        process-resources -Pformat -Dairness.enforce=false
    expect_exit formatting_write 'formatting: the installed format profile reformats both kinds' 0

    run_maven formatting_after repository "${formatting_consumer}" process-resources
    expect_exit formatting_after 'formatting: the rewritten consumer then passes the same phase' 0
    expect_no_match formatting_after 'formatting: no stale formatter finding survives the write' \
        'Incorrectly formatted file|Java sources that do not match'
}

run_binary_formatting_boundary() {
    new_consumer audio-formatting
    audio_consumer="${consumer_directory}"
    audio_fixture="${repository}/airness-it/fixtures/silence.wav"
    document_fixtures="${repository}/airness-it/fixtures/document-binaries"
    media_fixtures="${repository}/airness-it/fixtures/media-binaries"
    mkdir -p "${audio_consumer}/src/main/resources"
    cp "${audio_fixture}" "${audio_consumer}/src/main/resources/silence.wav"
    cp "${document_fixtures}/module.wasm" "${audio_consumer}/src/main/resources/module.wasm"
    cp "${document_fixtures}/font.pfb" "${audio_consumer}/src/main/resources/font.pfb"
    cp "${document_fixtures}/map.bcmap" "${audio_consumer}/src/main/resources/map.bcmap"
    cp "${media_fixtures}/tone.mp3" "${audio_consumer}/src/main/resources/tone.mp3"
    cp "${media_fixtures}/clip.mov" "${audio_consumer}/src/main/resources/clip.mov"
    run_maven audio_binary repository "${audio_consumer}" validate editorconfig:check
    expect_exit audio_binary 'formatting: audio, video, WebAssembly, fonts and character maps are binary' 0
    if cmp -s "${audio_fixture}" "${audio_consumer}/src/main/resources/silence.wav"; then
        pass 'formatting: checking audio preserves its exact bytes'
    else
        fail 'formatting: checking audio preserves its exact bytes' 'the WAV fixture changed'
    fi
    for binary_source in "${document_fixtures}/module.wasm" "${document_fixtures}/font.pfb" \
        "${document_fixtures}/map.bcmap" "${media_fixtures}/tone.mp3" "${media_fixtures}/clip.mov"; do
        binary_name="${binary_source##*/}"
        if cmp -s "${binary_source}" "${audio_consumer}/src/main/resources/${binary_name}"; then
            pass "formatting: checking preserves ${binary_name} bytes"
        else
            fail "formatting: checking preserves ${binary_name} bytes" 'the binary fixture changed'
        fi
    done
    printf 'Readable notes' > "${audio_consumer}/src/main/resources/notes.txt"
    # A file a generator owns is written as the generator writes it, without the final newline the style asks for.
    printf 'Generated notes' > "${audio_consumer}/src/main/resources/generated.txt"
    run_maven audio_text repository "${audio_consumer}" validate editorconfig:check \
        -Dairness.generated.files=src/main/resources/generated.txt
    expect_exit audio_text 'formatting: ordinary text still needs its final newline' 1
    expect_match audio_text 'formatting: the text file is the offending file' \
        'notes[.]txt.*insert_final_newline'
    expect_no_match audio_text 'formatting: a generated file is left to its generator' 'generated[.]txt'
    expect_no_match audio_text 'formatting: binaries stay outside the text finding' \
        'silence[.]wav@|module[.]wasm@|font[.]pfb@|map[.]bcmap@|tone[.]mp3@|clip[.]mov@'
}

# Indentation is held to the managed .editorconfig in every tracked text file: the whitespace indent_style names,
# and in YAML each nesting step at indent_size. A format whose syntax is a tab, such as a make recipe, indents with
# one, and tab-separated data is exempt by role, as the file linter's configuration exempts it.
run_indentation_boundary() {
    new_consumer indentation
    indentation_consumer="${consumer_directory}"
    printf 'build:\n\tmvn verify\n' > "${indentation_consumer}/Makefile"
    printf 'name\tvalue\n\tsecond column only\n' > "${indentation_consumer}/table.tsv"
    run_maven indentation_conforming repository "${indentation_consumer}" airness:indentation
    expect_exit indentation_conforming 'indentation: a tree indented as its .editorconfig declares passes' 0
    expect_match indentation_conforming 'indentation: the check read the tracked tree' \
        'Indentation read [1-9][0-9]* tracked file'

    mkdir -p "${indentation_consumer}/src/main/resources"
    printf '# Notes\n\n\tIndented with a tab.\n' > "${indentation_consumer}/notes.md"
    printf 'spring:\n    datasource:\n        url: jdbc:postgresql://localhost/app\n' \
        > "${indentation_consumer}/src/main/resources/application.yml"
    run_maven indentation_departing repository "${indentation_consumer}" airness:indentation
    expect_exit indentation_departing 'indentation: a departing tree fails the check' 1
    expect_match indentation_departing 'indentation: a tab-indented file names indent_style and its line' \
        'notes[.]md: indent_style = space, but line 3 indents with a tab'
    expect_match indentation_departing 'indentation: a four-space YAML file names indent_size and its step' \
        'src/main/resources/application[.]yml: indent_size = 2, but line 2 nests 4 columns deeper than line 1'
    expect_no_match indentation_departing 'indentation: the make recipe and the tab-separated data stay clean' \
        'Makefile: |table[.]tsv: '
}

# Output a build writes after validate to a path git ignores outside the build directory, as a compiled package
# writes its output. The linter's exclusions are first listed at validate, when that output does not exist yet, so
# only their second listing, which the check follows, keeps it out of the first build from a clean tree.
run_ignored_output_boundary() {
    new_consumer ignored-output
    ignored_consumer="${consumer_directory}"
    ignored_block="$(cat <<'XML'
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-antrun-plugin</artifactId>
                <version>3.2.0</version>
                <executions>
                    <execution>
                        <id>write-ignored-output</id>
                        <phase>compile</phase>
                        <goals><goal>run</goal></goals>
                        <configuration>
                            <target>
                                <echo file="dist/notes.txt" message="Written by the build.   ${line.separator}"/>
                            </target>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
XML
)"
    IGNORED_BLOCK="${ignored_block}" perl -0pi -e \
        's{</project>}{$ENV{IGNORED_BLOCK}."\n</project>"}e' "${ignored_consumer}/pom.xml"
    run_maven ignored_output repository "${ignored_consumer}" clean verify
    expect_file_match "${ignored_consumer}/dist/notes.txt" \
        'formatting: the build wrote an ignored file with trailing whitespace' ' $'
    expect_exit ignored_output 'formatting: ignored output the build writes is not read on the first build' 0
    expect_before ignored_output 'formatting: the exclusions are listed again before the check' \
        '--- airness:[^ ]+:editorconfig-excludes [(]airness-editorconfig-excludes[)]' \
        '--- editorconfig:[^ ]+:check [(]airness-editorconfig-check[)]'
    expect_file_match "${ignored_consumer}/target/airness/editorconfig-excludes.txt" \
        'formatting: the exclusions the check read name the ignored output' '^dist/[*][*]$'
    expect_no_match ignored_output 'formatting: the ignored output is named nowhere' 'dist/notes[.]txt'

    printf 'Committed notes.   \n' > "${ignored_consumer}/notes.txt"
    git -C "${ignored_consumer}" add notes.txt
    run_maven ignored_tracked repository "${ignored_consumer}" clean package
    expect_exit ignored_tracked 'formatting: a tracked file with trailing whitespace still fails' 1
    expect_match ignored_tracked 'formatting: the tracked file is the offending file' \
        'notes[.]txt.*trim_trailing_whitespace'
    expect_no_match ignored_tracked 'formatting: the ignored output stays unread beside it' 'dist/notes[.]txt'
}

run_tree_boundary() {
    new_consumer tree-state
    tree_consumer="${consumer_directory}"
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
    TREE_PROFILE="${tree_profile}" perl -0pi -e \
        's{</project>}{$ENV{TREE_PROFILE}."\n</project>"}e' "${tree_consumer}/pom.xml"
    prepare_maven tree_fixture_format repository "${tree_consumer}" validate editorconfig:format
    run_maven tree_mutation repository "${tree_consumer}" clean verify -Ptree-drift
    expect_exit tree_mutation 'tree: a mutation after the same lifecycle snapshot fails verification' 1
    expect_match tree_mutation 'tree: the changed tracked file is named' \
        'Committable files changed during the build|working tree content differs|AGENTS[.]md'
}

run_report_only_boundaries() {
    new_consumer report-only-compilation
    compilation_consumer="${consumer_directory}"
    cat > "${compilation_consumer}/src/main/java/com/example/Broken.java" <<'JAVA'
package com.example;

final class Broken {
    this is not Java
}
JAVA
    run_maven compilation_report_only repository "${compilation_consumer}" \
        clean verify -Dairness.enforce=false
    expect_exit compilation_report_only 'report-only: compilation failure remains fatal' 1
    expect_match compilation_report_only 'report-only: the fatal compiler failure is visible' \
        'COMPILATION ERROR|BUILD FAILURE'

    new_consumer report-only-test
    test_consumer="${consumer_directory}"
    cat > "${test_consumer}/src/test/java/com/example/ExampleTest.java" <<'JAVA'
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
    run_maven test_report_only repository "${test_consumer}" clean verify -Dairness.enforce=false
    expect_exit test_report_only 'report-only: a test finding remains reportable without stopping later checks' 0
    expect_match test_report_only 'report-only: the failed test remains visible' 'Failures: 1'
    expect_match test_report_only 'report-only: later current-build evidence still executes' \
        'airness:[^ ]+:coverage-evidence \(airness-coverage-evidence\)'
}

run_artifact_boundaries() {
    new_consumer artifact-content
    artifact_consumer="${consumer_directory}"
    mkdir -p "${artifact_consumer}/src/main/resources/.idea"
    printf 'local workspace metadata\n' \
        > "${artifact_consumer}/src/main/resources/.idea/workspace.xml"
    prepare_maven artifact_build repository "${artifact_consumer}" --quiet resources:resources jar:jar -DskipTests
    run_maven artifact_reject repository "${artifact_consumer}" airness:artifact-content
    expect_exit artifact_reject 'artifact: development metadata in the finished JAR is rejected' 1
    expect_match artifact_reject 'artifact: the packaged path is named precisely' \
        'Source or development files packaged in the JAR|[.]idea/workspace[.]xml'

    new_consumer repackaged-content
    repackaged_consumer="${consumer_directory}"
    mkdir -p "${repackaged_consumer}/packaging"
    printf 'local workspace metadata\n' > "${repackaged_consumer}/packaging/workspace.xml"
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
    SHADE_BLOCK="${shade_block}" perl -0pi -e \
        's{</project>}{$ENV{SHADE_BLOCK}."\n</project>"}e' "${repackaged_consumer}/pom.xml"
    run_maven artifact_repackaged repository "${repackaged_consumer}" \
        clean verify -Dairness.enforce=false
    expect_exit artifact_repackaged 'artifact: report-only permits inspection of the repackaged archive' 0
    expect_match artifact_repackaged 'artifact: the final repackaged archive is the one inspected' \
        'Source or development files packaged in the JAR'
    run_vendored_artifact_boundary
}

# A dependency's own published bytes, which a shade copies into the archive unchanged, are that dependency's
# to answer for. The goal is invoked on its own, as in the neighbouring cases, so the archives it exempts
# reach it only through the dependencies the goal itself requires resolved.
run_vendored_artifact_boundary() {
    new_consumer vendored-content
    vendored_consumer="${consumer_directory}"
    mkdir -p "${vendored_consumer}/vendor/.idea"
    printf 'vendor workspace metadata\n' > "${vendored_consumer}/vendor/.idea/workspace.xml"
    jar --create --file "${vendored_consumer}/vendor.jar" -C "${vendored_consumer}/vendor" .idea/workspace.xml
    prepare_maven vendored_install setup "${repository}" --non-recursive install:install-file \
        "-Dfile=${vendored_consumer}/vendor.jar" -DgroupId=com.example \
        -DartifactId=airness-it-vendored-library -Dversion=1.0.0 -Dpackaging=jar
    vendored_block="$(cat <<'XML'
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>airness-it-vendored-library</artifactId>
      <version>1.0.0</version>
    </dependency>
  </dependencies>
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
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
XML
)"
    VENDORED_BLOCK="${vendored_block}" perl -0pi -e \
        's{</project>}{$ENV{VENDORED_BLOCK}."\n</project>"}e' "${vendored_consumer}/pom.xml"
    prepare_maven vendored_build repository "${vendored_consumer}" --quiet jar:jar shade:shade@repackage -DskipTests
    if jar --list --file "${vendored_consumer}/target/consumer-9.4.2.jar" | grep -q '^[.]idea/workspace[.]xml$'; then
        pass 'artifact: the shade copied the dependency entry into the finished JAR'
    else
        fail 'artifact: the shade copied the dependency entry into the finished JAR' 'vendored entry missing'
    fi
    run_maven vendored_accept repository "${vendored_consumer}" airness:artifact-content
    expect_exit vendored_accept 'artifact: a dependency entry the shade copied unchanged is not rejected' 0
    expect_no_match vendored_accept 'artifact: the vendored entry is not reported as development material' \
        'Source or development files packaged in the JAR|[.]idea/workspace[.]xml'
}

# A versioned class that reaches the archive after its manifest was written. The plugin that packages
# an ordinary archive declares a versioned class it can see, so the defect belongs to repackaging: a
# shade writes a manifest of its own and says nothing about the versioned classes it copied in, and a
# runtime then reads none of them.
run_artifact_manifest_boundary() {
    new_consumer artifact-manifest
    manifest_consumer="${consumer_directory}"
    mkdir -p "${manifest_consumer}/packaging"
    printf 'bytecode\n' > "${manifest_consumer}/packaging/Versioned.class"
    manifest_block="$(cat <<'XML'
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
                  <resource>META-INF/versions/17/com/example/Versioned.class</resource>
                  <file>packaging/Versioned.class</file>
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
    MANIFEST_BLOCK="${manifest_block}" perl -0pi -e \
        's{</project>}{$ENV{MANIFEST_BLOCK}."\n</project>"}e' "${manifest_consumer}/pom.xml"
    prepare_maven manifest_build repository "${manifest_consumer}" --quiet clean package \
        -DskipTests -Dairness.enforce=false
    run_maven manifest_reject repository "${manifest_consumer}" airness:artifact-content
    expect_exit manifest_reject 'artifact: an undeclared versioned class is rejected' 1
    expect_match manifest_reject 'artifact: the undeclared versioned class is named precisely' \
        'Versioned classes the manifest does not declare|META-INF/versions/17/com/example/Versioned[.]class'
}

run_coverage_boundary() {
    new_consumer coverage-current-build
    coverage_consumer="${consumer_directory}"
    rm "${coverage_consumer}/src/test/java/com/example/ExampleTest.java"
    run_maven coverage_missing repository "${coverage_consumer}" clean verify
    expect_exit coverage_missing 'coverage: a production module with no current test evidence fails' 1
    expect_match coverage_missing 'coverage: the current-build evidence failure is explicit' \
        'Missing current-build JaCoCo evidence'

    run_maven coverage_report_only repository "${coverage_consumer}" \
        clean verify -Dairness.enforce=false
    expect_exit coverage_report_only 'coverage: missing current evidence remains visible in report-only mode' 0
    expect_match coverage_report_only 'coverage: report-only does not hide the current-build finding' \
        'Missing current-build JaCoCo evidence'
}

run_git_boundaries() {
    new_consumer git-linear
    git_linear="${consumer_directory}"
    run_maven git_linear repository "${git_linear}" airness:linear-history
    expect_exit git_linear 'history: a real linear consumer repository passes' 0
    expect_match git_linear 'history: the full history was actually read' 'Linear history read [0-9]+ commit'

    git_shallow="${scratch}/git-shallow"
    git clone --quiet --depth 1 "file://${git_linear}" "${git_shallow}"
    run_maven git_shallow repository "${git_shallow}" airness:require-full-history
    expect_exit git_shallow 'history: a real shallow clone is rejected' 1
    expect_match git_shallow 'history: the shallow topology is named' 'This is a shallow clone'

    new_consumer git-merged
    git_merged="${consumer_directory}"
    git -C "${git_merged}" checkout --quiet -b side
    git -C "${git_merged}" commit --quiet --allow-empty \
        --message 'feat(core): record a side commit to merge back'
    git -C "${git_merged}" checkout --quiet -
    git -C "${git_merged}" merge --quiet --no-ff side --message "Merge branch 'side'"
    run_maven git_merge repository "${git_merged}" \
        airness:linear-history airness:commit-history -Dairness.enforce=false
    expect_exit git_merge 'history: merge topology and its header report without stopping the batch' 0
    expect_match git_merge 'history: the real merge topology is rejected' 'Merge commits in the history'
    expect_match git_merge 'history: the merge header is read like every other header' \
        'Commit messages that break the policy'
    expect_count git_merge 'history: both requested installed Git goals executed' \
        'airness:[^ ]+:(linear-history|commit-history) \(default-cli\)' 2

    new_consumer git-complete-history
    git_complete="${consumer_directory}"
    git -C "${git_complete}" commit --quiet --allow-empty --message 'wip'
    git -C "${git_complete}" commit --quiet --allow-empty \
        --message 'test(core): record a later compliant history entry'
    run_maven git_complete repository "${git_complete}" airness:commit-history
    expect_exit git_complete 'history: a later good commit cannot hide an earlier bad one' 1
    expect_match git_complete 'history: complete-history evidence names the bad header' \
        'Commit messages that break the policy|wip'
}

check_published_assets() {
    assets="${local_repository}/eu/ciechanowiec/airness-assets/${harness_version}/airness-assets-${harness_version}.jar"
    listing="${scratch}/assets.txt"
    jar tf "${assets}" > "${listing}"
    if grep -Fq 'airness/files/generic/README-guideline-software-project.adoc.asset' "${listing}" \
        && ! grep -Ev 'airness/files/generic/README-guideline-software-project[.]adoc[.]asset$' "${listing}" \
            | grep -Eq '(^|/)([.]vale|[.]docs|docinfo|README|githooks|lint-docs)'; then
        pass 'assets: the published JAR contains only the pinned guideline documentation'
    else
        fail 'assets: the published JAR content is not the pinned guideline-only shape'
    fi
}
