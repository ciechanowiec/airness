#!/usr/bin/env sh

run_model_policy_cases() {
    new_consumer model-policy
    model_consumer="${consumer_directory}"
    cp "${model_consumer}/pom.xml" "${scratch}/model-original.xml"
    run_maven model_cli_skip maven "${model_consumer}" airness:project-settings -DskipTests
    expect_exit model_cli_skip 'model: a command-line test skip remains available' 0
    # A property a bound goal reads in place of an unconfigured parameter is refused like the skip flags,
    # since Maven fills the parameter from it without the plugin ever being declared.
    for model_property in skipTests test maven.test.failure.ignore checkstyle.maxAllowedViolations; do
        cp "${scratch}/model-original.xml" "${model_consumer}/pom.xml"
        MODEL_PROPERTY="${model_property}" perl -0pi -e \
            's{<properties>}{"<properties><".$ENV{MODEL_PROPERTY}.">true</".$ENV{MODEL_PROPERTY}.">"}e' \
            "${model_consumer}/pom.xml"
        run_maven "model_${model_property}" maven "${model_consumer}" airness:project-settings -DskipTests
        expect_exit "model_${model_property}" "model: committed ${model_property} cannot bypass preflight" 1
        expect_match "model_${model_property}" "model: committed ${model_property} is identified" \
            "Remove child property ${model_property}"
    done
    # A project profile of the format id, active by default, would switch the tree comparisons off on every build
    # without anyone asking for formatting, so the id is the harness's alone.
    cp "${scratch}/model-original.xml" "${model_consumer}/pom.xml"
    model_profile='<profiles><profile><id>format</id><activation>'
    model_profile="${model_profile}<activeByDefault>true</activeByDefault></activation></profile></profiles>"
    MODEL_PROFILE="${model_profile}" perl -0pi -e \
        's{</project>}{$ENV{MODEL_PROFILE}."</project>"}e' "${model_consumer}/pom.xml"
    run_maven model_format_profile maven "${model_consumer}" airness:project-settings -DskipTests
    expect_exit model_format_profile 'model: a project profile cannot reuse the format id' 1
    expect_match model_format_profile 'model: the reused profile id is identified' 'Remove child profile format'
    cp "${scratch}/model-original.xml" "${model_consumer}/pom.xml"
    model_execution='<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>'
    model_execution="${model_execution}<artifactId>maven-surefire-plugin</artifactId><executions><execution>"
    model_execution="${model_execution}<id>default-test</id><phase>none</phase>"
    model_execution="${model_execution}</execution></executions></plugin></plugins></build>"
    MODEL_EXECUTION="${model_execution}" perl -0pi -e \
        's{</project>}{$ENV{MODEL_EXECUTION}."</project>"}e' "${model_consumer}/pom.xml"
    # The same run names the generic parent, which a module holding Java may not inherit on its own: both
    # model problems are reported together, so the refusal is proved without a run of its own.
    perl -0pi -e 's{<artifactId>airness-parent-java</artifactId>}{<artifactId>airness-parent</artifactId>}' \
        "${model_consumer}/pom.xml"
    run_maven model_test_phase maven "${model_consumer}" airness:project-settings
    expect_exit model_test_phase 'model: the default test execution cannot be unbound' 1
    expect_match model_test_phase 'model: the changed test phase is identified' 'default-test.*phase'
    expect_match model_test_phase 'model: Java under the generic parent is refused with the Java parent named' \
        'has Java sources but does not inherit eu[.]ciechanowiec:airness-parent-java'
    run_parent_model_cases
}

run_parent_model_cases() {
    cp "${scratch}/model-original.xml" "${model_consumer}/pom.xml"
    mkdir -p "${scratch}/company-parent"
    cp "${scratch}/model-original.xml" "${scratch}/company-parent/pom.xml"
    perl -0pi -e 's{<artifactId>consumer</artifactId>}{<artifactId>company-parent</artifactId>};
        s{<version>9.4.2</version>}{<version>9.4.2</version><packaging>pom</packaging>}' \
        "${scratch}/company-parent/pom.xml"
    perl -0pi -e 's{<parent>.*?</parent>}{<parent><groupId>com.example</groupId>
        <artifactId>company-parent</artifactId><version>9.4.2</version>
        <relativePath>../company-parent/pom.xml</relativePath></parent>}s' "${model_consumer}/pom.xml"
    run_maven model_valid_parent maven "${model_consumer}" airness:project-settings
    expect_exit model_valid_parent 'model: an ordinary parent outside the reactor remains valid' 0
    cp "${scratch}/company-parent/pom.xml" "${scratch}/parent-original.xml"
    for model_property in airness.enforce skipTests airness.version; do
        cp "${scratch}/parent-original.xml" "${scratch}/company-parent/pom.xml"
        MODEL_PROPERTY="${model_property}" MODEL_VERSION="${harness_version}" perl -0pi -e \
            's{<properties>}{"<properties><".$ENV{MODEL_PROPERTY}.">".
                ($ENV{MODEL_PROPERTY} eq "airness.version" ? $ENV{MODEL_VERSION} :
                    $ENV{MODEL_PROPERTY} eq "skipTests" ? "true" : "false").
                "</".$ENV{MODEL_PROPERTY}.">"}e' \
            "${scratch}/company-parent/pom.xml"
        run_maven "parent_${model_property}" maven "${model_consumer}" airness:project-settings -DskipTests
        expect_exit "parent_${model_property}" "model: inherited ${model_property} is refused outside the reactor" 1
        expect_match "parent_${model_property}" "model: the offending parent POM is identified" \
            'company-parent/pom[.]xml'
    done
    run_installed_parent_model_cases
}

run_installed_parent_model_cases() {
    cp "${scratch}/parent-original.xml" "${scratch}/company-parent/pom.xml"
    perl -0pi -e 's{<artifactId>company-parent</artifactId>}{<artifactId>airness-model-policy-parent</artifactId>}' \
        "${scratch}/company-parent/pom.xml"
    prepare_maven model_install_parent setup "${repository}" --non-recursive install:install-file \
        "-Dfile=${scratch}/company-parent/pom.xml" "-DpomFile=${scratch}/company-parent/pom.xml"
    perl -0pi -e 's{<artifactId>company-parent</artifactId>}{<artifactId>airness-model-policy-parent</artifactId>};
        s{<relativePath>.*?</relativePath>}{<relativePath/>}' "${model_consumer}/pom.xml"
    run_maven model_installed_parent maven "${model_consumer}" airness:project-settings
    expect_exit model_installed_parent 'model: an ordinary repository-resolved parent remains valid' 0
    perl -0pi -e 's{<properties>}{<properties><airness.enforce>false</airness.enforce>}' \
        "${scratch}/company-parent/pom.xml"
    prepare_maven model_install_bad_parent setup "${repository}" --non-recursive install:install-file \
        "-Dfile=${scratch}/company-parent/pom.xml" "-DpomFile=${scratch}/company-parent/pom.xml"
    run_maven model_installed_override maven "${model_consumer}" airness:project-settings -DskipTests
    expect_exit model_installed_override 'model: a repository-resolved parent cannot override the harness' 1
    expect_match model_installed_override 'model: the cached parent POM is named' 'airness-model-policy-parent.*[.]pom'
}
