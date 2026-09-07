#!/usr/bin/env sh

# These fixtures use the installed goal and Spring Boot's actual repackager. Preparation deliberately
# invokes packaging goals alone; only the subsequent enforced artifact check supplies a verdict.
run_packaged_output_boundary() {
    prepare_packaged_consumer
    run_maven boot_native_missing repository "$packaged_consumer" airness:artifact-content
    expect_exit boot_native_missing 'artifact: Boot application native access is enforced' 1
    expect_match boot_native_missing 'artifact: Boot refusal identifies the native-access rule' \
        'Restricted native access the manifest does not declare'
    expect_match boot_native_missing 'artifact: missing native access names the packaged application class' \
        'BOOT-INF/classes/com/example/NativeApplication[.]class'

    cp "$packaged_archive" "$packaged_consumer/missing-native.jar"
    printf 'Enable-Native-Access: ALL-UNNAMED\n' > "$packaged_consumer/native.mf"
    jar --update --file "$packaged_archive" --manifest "$packaged_consumer/native.mf"
    run_maven boot_native_valid repository "$packaged_consumer" airness:artifact-content
    expect_exit boot_native_valid 'artifact: the correctly declared Boot archive is accepted' 0
    run_artifact_java boot_native_launch "$packaged_archive"
    expect_exit boot_native_launch 'artifact: the valid native declaration permits the actual call' 0
    expect_match boot_native_launch 'artifact: the application reached its native operation' 'native-access-ready'

    mkdir -p "$packaged_consumer/injected/BOOT-INF/classes/com/example"
    cp "$packaged_consumer/target/test-classes/com/example/ExampleTest.class" \
        "$packaged_consumer/injected/BOOT-INF/classes/com/example/ExampleTest.class"
    jar --update --file "$packaged_archive" -C "$packaged_consumer/injected" \
        BOOT-INF/classes/com/example/ExampleTest.class
    run_maven boot_test_output repository "$packaged_consumer" airness:artifact-content
    expect_exit boot_test_output 'artifact: Boot cannot ship test-only output' 1
    expect_match boot_test_output 'artifact: Boot refusal identifies the test-output rule' \
        'Test-only output packaged in the JAR'
    expect_match boot_test_output 'artifact: test-only output names its packaged path' \
        'BOOT-INF/classes/com/example/ExampleTest[.]class'
    expect_no_match boot_test_output 'artifact: the test-only refusal is independent of native access' \
        'Restricted native access the manifest does not declare|Invalid native-access manifest declaration'

    cp "$packaged_consumer/missing-native.jar" "$packaged_archive"
    printf 'Enable-Native-Access: false\n' > "$packaged_consumer/native.mf"
    jar --update --file "$packaged_archive" --manifest "$packaged_consumer/native.mf"
    run_maven boot_native_invalid repository "$packaged_consumer" airness:artifact-content
    expect_exit boot_native_invalid 'artifact: an invalid native declaration is refused' 1
    expect_match boot_native_invalid 'artifact: the invalid declaration names its repair' \
        'META-INF/MANIFEST[.]MF: Enable-Native-Access must be exactly ALL-UNNAMED'
    expect_no_match boot_native_invalid 'artifact: an invalid declaration is not also reported as missing' \
        'Restricted native access the manifest does not declare'
    run_artifact_java boot_invalid_launch "$packaged_archive"
    expect_exit boot_invalid_launch 'artifact: the JVM also rejects the invalid declaration' 1
    expect_match boot_invalid_launch 'artifact: JVM refusal names the manifest value' \
        'illegal value "false" for Enable-Native-Access'
}

prepare_packaged_consumer() {
    new_consumer boot-artifact-content
    packaged_consumer="$consumer_directory"
    packaged_archive="$packaged_consumer/target/consumer-9.4.2.jar"
    cat > "$packaged_consumer/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent-spring-boot</artifactId>
        <version>1.0.7-SNAPSHOT</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>consumer</artifactId>
    <version>9.4.2</version>
    <properties><airness.package.root>com.example</airness.package.root></properties>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration><mainClass>com.example.NativeApplication</mainClass></configuration>
            </plugin>
        </plugins>
    </build>
</project>
POM
    cat > "$packaged_consumer/src/main/java/com/example/NativeApplication.java" <<'JAVA'
package com.example;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;

public final class NativeApplication {
    public static void main(String[] arguments) {
        Linker linker = Linker.nativeLinker();
        System.out.println(linker.downcallHandle(
            linker.defaultLookup().find("strlen").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)));
        System.out.println("native-access-ready");
    }
}
JAVA
    prepare_maven boot_artifact_build repository "$packaged_consumer" --quiet \
        resources:resources compiler:compile compiler:testCompile jar:jar spring-boot:repackage
}

run_artifact_java() {
    artifact_execution="$1"
    artifact_archive="$2"
    artifact_started="$(date +%s)"
    set +e
    java --illegal-native-access=deny -jar "$artifact_archive" > "$(execution_log "$artifact_execution")" 2>&1
    artifact_status=$?
    set -e
    printf '%s\n' "$artifact_status" > "$execution_logs/$artifact_execution.status"
    physical_executions=$((physical_executions + 1))
    record_timing "$(($(date +%s) - artifact_started))" repository "$artifact_execution" \
        "$artifact_status" java --illegal-native-access=deny -jar "$artifact_archive"
}
