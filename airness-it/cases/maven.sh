#!/usr/bin/env sh

run_maven_cases() {
    new_consumer maven-consumer
    maven_consumer="$consumer_directory"

    run_maven profile_extended maven "$maven_consumer" validate -Pextended -DskipTests
    expect_exit profile_extended 'profiles: the installed parent accepts its Extended profile' 0
    expect_match profile_extended 'profiles: the inherited profile reaches preflight' \
        'airness:[^ ]+:required-profiles \(airness-preflight\)'

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
}
