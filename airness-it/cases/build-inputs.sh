#!/usr/bin/env sh

run_build_input_cases() {
    run_ignored_build_inputs
    run_filtered_build_inputs
    run_reactor_build_inputs
    run_source_folder_generation
    run_late_build_inputs generate-resources add-resource process-resources main
    run_late_build_inputs generate-test-resources add-test-resource process-test-resources test
    run_generated_build_inputs
}

run_ignored_build_inputs() {
    new_consumer ignored-build-inputs
    input_consumer="${consumer_directory}"
    input_java='src/main/java/com/example/build/Local.java'
    input_resource='src/main/resources/vendor/build/viewer.mjs'
    mkdir -p "${input_consumer}/src/main/java/com/example/build" "${input_consumer}/src/main/resources/vendor/build"
    printf 'package com.example.build; class Local {}\n' > "${input_consumer}/${input_java}"
    printf 'export const viewer = true;\n' > "${input_consumer}/${input_resource}"
    run_maven inputs_ignored repository "${input_consumer}" validate
    expect_exit inputs_ignored 'build inputs: ignored Java and runtime resources fail preflight' 1
    expect_match inputs_ignored 'build inputs: Java input is named' 'ignored build input .*Local[.]java'
    expect_match inputs_ignored 'build inputs: runtime resource is named' 'ignored build input .*viewer[.]mjs'
    run_maven inputs_report_only repository "${input_consumer}" validate -Dairness.enforce=false
    expect_exit inputs_report_only 'build inputs: report-only cannot excuse missing checkout inputs' 1
    git -C "${input_consumer}" add -f "${input_java}" "${input_resource}"
    printf 'New untracked resource\n' > "${input_consumer}/src/main/resources/new.txt"
    run_maven inputs_tracked repository "${input_consumer}" validate
    expect_exit inputs_tracked 'build inputs: tracked ignored-pattern files and new ordinary files pass' 0
    perl -0pi -e 's{<artifactId>airness-parent</artifactId>}{<artifactId>airness-parent-spring-boot</artifactId>}' \
        "${input_consumer}/pom.xml"
    printf 'Another local module\n' > "${input_consumer}/src/main/resources/vendor/build/missing.mjs"
    run_maven inputs_spring repository "${input_consumer}" validate
    expect_exit inputs_spring 'build inputs: Spring Boot inherits the same preflight' 1
    expect_match inputs_spring 'build inputs: Spring Boot names its missing resource' \
        'ignored build input .*missing[.]mjs'
}

run_filtered_build_inputs() {
    new_consumer filtered-build-inputs
    input_consumer="${consumer_directory}"
    mkdir -p "${input_consumer}/custom/build" "${input_consumer}/custom-tests/build"
    printf 'Excluded resource\n' > "${input_consumer}/custom/build/local.txt"
    printf 'Excluded test resource\n' > "${input_consumer}/custom-tests/build/local.txt"
    cat > "${input_consumer}/input-build.xml" <<'XML'
    <build>
        <resources><resource><directory>custom</directory>
            <includes><include>**/*.mjs</include></includes>
        </resource></resources>
        <testResources><testResource><directory>custom-tests</directory>
            <excludes><exclude>build/**</exclude></excludes>
        </testResource></testResources>
    </build>
XML
    append_input_build
    run_maven inputs_filtered repository "${input_consumer}" validate
    expect_exit inputs_filtered 'build inputs: effective resource filters exclude ignored non-inputs' 0
    printf 'Selected resource\n' > "${input_consumer}/custom/build/selected.mjs"
    run_maven inputs_custom repository "${input_consumer}" validate
    expect_exit inputs_custom 'build inputs: custom resource roots remain governed' 1
    expect_match inputs_custom 'build inputs: custom input path is reported' 'custom/build/selected[.]mjs'
}

run_late_build_inputs() {
    new_consumer "late-build-inputs-$4"
    input_consumer="${consumer_directory}"
    mkdir -p "${input_consumer}/late/build"
    printf 'Late registered resource\n' > "${input_consumer}/late/build/value.txt"
    cat > "${input_consumer}/input-build.xml" <<XML
    <build><plugins><plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>build-helper-maven-plugin</artifactId><version>3.6.1</version>
        <executions><execution><id>register-input</id><phase>$1</phase>
            <goals><goal>$2</goal></goals>
            <configuration><resources><resource><directory>late/build</directory></resource></resources></configuration>
        </execution></executions>
    </plugin></plugins></build>
XML
    append_input_build
    run_maven "inputs_late_before_$4" repository "${input_consumer}" validate
    expect_exit "inputs_late_before_$4" 'build inputs: an unrelated ignored directory is initially outside scope' 0
    run_maven "inputs_late_after_$4" repository "${input_consumer}" "$3"
    expect_exit "inputs_late_after_$4" 'build inputs: later resource registration is checked before tests' 1
    expect_match "inputs_late_after_$4" 'build inputs: newly registered resource is named' 'late/build/value[.]txt'
}

run_generated_build_inputs() {
    new_consumer generated-build-inputs
    input_consumer="${consumer_directory}"
    printf '\nbuild-output/\n' >> "${input_consumer}/.gitignore"
    cp "${repository}/airness-it/fixtures/build-inputs/"*.template "${input_consumer}/"
    perl -0pi -e 's/package com.example;/package com.example;\n\nimport com.example.generated.GeneratedValue;/;
        s/return 1;/return new GeneratedValue().value();/' \
        "${input_consumer}/src/main/java/com/example/Example.java"
    generated_input_build
    append_input_build
    run_maven inputs_generated repository "${input_consumer}" clean verify
    expect_exit inputs_generated \
        'build inputs: clean builds recreate generated Java and resources under custom output' 0
    if [ -f "${input_consumer}/build-output/classes/com/example/generated/GeneratedValue.class" ]; then
        pass 'build inputs: the generated Java input was compiled'
    else
        fail 'build inputs: the generated Java input was compiled' 'generated class missing'
    fi
    if jar --list --file "${input_consumer}/build-output/consumer-9.4.2.jar" | grep -q '^generated.txt$'; then
        pass 'build inputs: the generated resource is present in the finished archive'
    else
        fail 'build inputs: the generated resource is present in the finished archive' 'generated resource missing'
    fi
}

append_input_build() {
    input_build="$(cat "${input_consumer}/input-build.xml")"
    INPUT_BUILD="${input_build}" perl -0pi -e \
        's{</project>}{$ENV{INPUT_BUILD}."\n</project>"}e' "${input_consumer}/pom.xml"
}

generated_input_build() {
    cat > "${input_consumer}/input-build.xml" <<'XML'
    <build>
        <directory>build-output</directory>
        <resources><resource>
            <directory>${project.build.directory}/generated-resources</directory>
        </resource></resources>
        <plugins>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-antrun-plugin</artifactId>
                <version>3.2.0</version><executions><execution><id>produce-inputs</id><phase>generate-sources</phase>
                    <goals><goal>run</goal></goals><configuration><target>
                        <mkdir dir="${project.build.directory}/generated-sources/com/example/generated" />
                        <mkdir dir="${project.build.directory}/generated-resources" />
                        <copy file="${project.basedir}/GeneratedValue.java.template"
                            tofile="${project.build.directory}/generated-sources/com/example/generated/GeneratedValue.java"
                        />
                        <copy file="${project.basedir}/package-info.java.template"
                            tofile="${project.build.directory}/generated-sources/com/example/generated/package-info.java"
                        />
                        <echo file="${project.build.directory}/generated-resources/generated.txt"
                        >Generated resource&#10;</echo>
                    </target></configuration>
                </execution></executions>
            </plugin>
            <plugin><groupId>org.codehaus.mojo</groupId><artifactId>build-helper-maven-plugin</artifactId>
                <version>3.6.1</version><executions><execution>
                    <id>generated-source-root</id><phase>generate-sources</phase>
                    <goals><goal>add-source</goal></goals><configuration><sources>
                        <source>${project.build.directory}/generated-sources</source>
                    </sources></configuration>
                </execution></executions>
            </plugin>
        </plugins>
    </build>
XML
}

run_reactor_build_inputs() {
    new_consumer reactor-build-inputs
    input_consumer="${consumer_directory}"
    mkdir -p "${input_consumer}/child/src/main/resources/build"
    printf 'Child runtime input\n' > "${input_consumer}/child/src/main/resources/build/value.txt"
    perl -0pi -e 's{</project>}{<packaging>pom</packaging><modules><module>child</module></modules></project>}' \
        "${input_consumer}/pom.xml"
    cat > "${input_consumer}/child/pom.xml" <<'XML'
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent><groupId>com.example</groupId><artifactId>consumer</artifactId><version>9.4.2</version></parent>
    <artifactId>child</artifactId>
</project>
XML
    run_maven inputs_reactor repository "${input_consumer}" validate
    expect_exit inputs_reactor 'build inputs: child modules are checked independently of the aggregator' 1
    expect_match inputs_reactor 'build inputs: the child module and resource are identified' \
        'child main resource: ignored build input child/src/main/resources/build/value[.]txt'
}

run_source_folder_generation() {
    new_consumer source-folder-generation
    input_consumer="${consumer_directory}"
    cat > "${input_consumer}/input-build.xml" <<'XML'
    <build><plugins><plugin>
        <groupId>org.apache.maven.plugins</groupId><artifactId>maven-antrun-plugin</artifactId>
        <version>3.2.0</version><executions><execution>
            <id>generate-in-source-folder</id><phase>generate-resources</phase>
            <goals><goal>run</goal></goals><configuration><target>
                <mkdir dir="${project.basedir}/src/main/resources/build" />
                <echo file="${project.basedir}/src/main/resources/build/late.txt">Generated outside output\n</echo>
            </target></configuration>
        </execution></executions>
    </plugin></plugins></build>
XML
    append_input_build
    run_maven inputs_source_generated repository "${input_consumer}" process-resources
    expect_exit inputs_source_generated 'build inputs: generation into ignored source folders is refused' 1
    expect_match inputs_source_generated 'build inputs: the new ignored input is named' \
        'src/main/resources/build/late[.]txt'
}
