#!/usr/bin/env sh

run_managed_plugin_cases() {
    prepare_managed_plugin_consumer
    run_maven optional_plugins maven "${plugins_consumer}" clean verify -DskipITs=true
    expect_exit optional_plugins 'plugins: inherited optional plugins verify with native IT skipping refused' 0
    expect_match optional_plugins 'plugins: the integration verification goal is inherited' \
        'failsafe:[^ ]+:verify [(]airness-integration-verify[)]'
    expect_before optional_plugins 'plugins: integration tests contribute before the coverage report' \
        'failsafe:[^ ]+:integration-test ' 'jacoco:[^ ]+:report '
    expect_before optional_plugins 'plugins: integration tests run before the final tree check' \
        'failsafe:[^ ]+:integration-test ' 'airness:[^ ]+:tree-verify '
    if python3 "${repository}/airness-it/managed-plugins-check.py" "${plugins_consumer}"; then
        pass 'plugins: both test phases cover the branch and the assembled JAR holds the additional resource'
    else
        fail 'plugins: both test phases cover the branch and the assembled JAR holds the additional resource'
    fi
    run_integration_failure_cases
    run_spring_integration_case
}

prepare_managed_plugin_consumer() {
    new_consumer managed-plugins
    plugins_consumer="${consumer_directory}"
    plugins_fixture="${repository}/airness-it/fixtures/managed-plugins"
    cp "${plugins_fixture}/Decision.java" "${plugins_consumer}/src/main/java/com/example/Decision.java"
    cp "${plugins_fixture}/DecisionTest.java" "${plugins_consumer}/src/test/java/com/example/DecisionTest.java"
    cp "${plugins_fixture}/DecisionIT.java" "${plugins_consumer}/src/test/java/com/example/DecisionIT.java"
    mkdir -p "${plugins_consumer}/src/assembly" "${plugins_consumer}/src/additional-resources"
    cp "${plugins_fixture}/distribution.xml" "${plugins_consumer}/src/assembly/distribution.xml"
    printf 'Managed Build Helper resource.\n' > "${plugins_consumer}/src/additional-resources/plugin-resource.txt"
    plugins_xml="$(cat "${plugins_fixture}/plugins.xml")"
    PLUGINS_XML="${plugins_xml}" perl -0pi -e \
        's{</project>}{$ENV{PLUGINS_XML}."\n</project>"}e' "${plugins_consumer}/pom.xml"
    prepare_maven setup_plugins setup "${plugins_consumer}" --quiet process-resources -Pformat editorconfig:format
}

run_integration_failure_cases() {
    perl -0pi -e 's/assertEquals\(0,/assertEquals(1,/' \
        "${plugins_consumer}/src/test/java/com/example/DecisionIT.java"
    run_maven integration_failure maven "${plugins_consumer}" compiler:testCompile \
        failsafe:integration-test failsafe:verify
    expect_exit integration_failure 'plugins: a failed integration assertion fails verification' 1
    expect_match integration_failure 'plugins: the failure reaches Failsafe verification' 'There are test failures'
    run_maven integration_report_only maven "${plugins_consumer}" -Dairness.enforce=false \
        failsafe:integration-test failsafe:verify
    expect_exit integration_report_only 'plugins: report-only mode retains integration findings' 0
    expect_match integration_report_only 'plugins: report-only keeps the failed assertion visible' 'Failures: 1'
    rm "${plugins_consumer}/src/test/java/com/example/DecisionIT.java"
    rm "${plugins_consumer}/target/test-classes/com/example/DecisionIT.class"
    run_maven integration_empty maven "${plugins_consumer}" failsafe:integration-test failsafe:verify
    expect_exit integration_empty 'plugins: an opted-in module without integration tests fails' 1
    expect_match integration_empty 'plugins: missing integration tests are explained' 'No tests were executed'
}

run_spring_integration_case() {
    integration_spring="${scratch}/failsafe-spring"
    build_spring_application "${integration_spring}" setup_failsafe_spring
    build_spring_context_test "${integration_spring}"
    mv "${integration_spring}/src/test/java/com/example/ContextTest.java" \
        "${integration_spring}/src/test/java/com/example/ContextIT.java"
    perl -0pi -e 's/ContextTest/ContextIT/g' "${integration_spring}/src/test/java/com/example/ContextIT.java"
    integration_plugin="$(cat <<'XML'
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
</plugin>
XML
)"
    INTEGRATION_PLUGIN="${integration_plugin}" perl -0pi -e \
        's{<plugins>}{"<plugins>".$ENV{INTEGRATION_PLUGIN}}e' \
        "${integration_spring}/pom.xml"
    prepare_maven setup_failsafe_spring_format setup "${integration_spring}" --quiet validate editorconfig:format
    run_maven integration_spring maven "${integration_spring}" clean verify
    expect_exit integration_spring 'plugins: Spring startup evidence can come solely from an integration test' 0
    expect_before integration_spring 'plugins: Spring runtime evidence is checked after integration tests' \
        'failsafe:[^ ]+:integration-test ' 'airness:[^ ]+:spring-context '
}
