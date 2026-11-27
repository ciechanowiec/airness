# The starters domain: an application airness:new makes from a starter passes Extended verification as it is
# made, and the goal refuses a request it cannot make. It needs Docker, because the application's tests and its
# browser sweep start their own services, the image gate boots the application's image against empty ones, and
# Extended runs its scanners in containers.

starter_goal="eu.ciechanowiec:airness-maven-plugin:${harness_version}:new"
starter_organization='-Dairness.organization=Sample Organization'

# Makes an application from a starter in a directory of its own under the starters home and holds it to what
# every starter promises: the goal names what it made, the first lockfile resolves the installed harness,
# assets-init wrote the managed files, the result is one commit with a body and nothing left over, and the
# application passes Extended verification with the node gates, Dependency-Check, the browser sweep and the
# image gate. Each label names the starter. The arguments after the starter are handed to the goal.
#   verify_starter <starter> <artifactId> [goal argument...]
# It sets starter_application and leaves the verification log under the id starter_verify_<starter>.
verify_starter() {
    verified_starter="$1"
    verified_artifact="$2"
    shift 2
    verified_id="$(printf '%s' "${verified_starter}" | tr -c 'a-z0-9\n' '_')"
    starter_home="${scratch}/starters"
    mkdir -p "${starter_home}"
    # The goal commits the application it makes, and a CI runner configures no Git identity.
    GIT_AUTHOR_NAME=Fixture
    GIT_AUTHOR_EMAIL=fixture@example.invalid
    GIT_COMMITTER_NAME=Fixture
    GIT_COMMITTER_EMAIL=fixture@example.invalid
    export GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL

    run_maven "starter_new_${verified_id}" starters "${starter_home}" "${starter_goal}" \
        -Dairness.starter="${verified_starter}" -Dairness.artifactId="${verified_artifact}" \
        "${starter_organization}" -Dairness.url="https://git.example.invalid/${verified_artifact}" "$@"
    expect_exit "starter_new_${verified_id}" \
        "starters: airness:new makes an application from the ${verified_starter} starter" 0
    expect_match "starter_new_${verified_id}" \
        "starters: airness:new names the ${verified_starter} application and where it made it" \
        "Made .* in .*/${verified_artifact}[.]"
    starter_application="${starter_home}/${verified_artifact}"
    expect_file_match "${starter_application}/pnpm-lock.yaml" \
        "starters: the first lockfile of the ${verified_starter} application resolves the installed harness package" \
        "'@airness/node@${harness_version}'"
    expect_file_match "${starter_application}/.airness/agent-guide.md" \
        "starters: assets-init writes the managed files of the harness into the ${verified_starter} application" \
        'Airness'
    starter_commits="$(git -C "${starter_application}" rev-list --count HEAD 2>/dev/null || printf '0')"
    starter_changes="$(git -C "${starter_application}" status --porcelain 2>/dev/null || printf 'unreadable')"
    if [ "${starter_commits}" = 1 ] && [ -z "${starter_changes}" ]; then
        pass "starters: the ${verified_starter} application is one commit with nothing left uncommitted"
    else
        fail "starters: the ${verified_starter} application is one commit with nothing left uncommitted" \
            "${starter_commits} commit(s), and uncommitted: ${starter_changes}"
    fi

    starter_body="$(git -C "${starter_application}" log -1 --format=%b 2>/dev/null || printf '')"
    if [ -n "${starter_body}" ]; then
        pass "starters: the first commit of the ${verified_starter} application says why it was made"
    else
        fail "starters: the first commit of the ${verified_starter} application says why it was made" \
            'the body is empty'
    fi

    # Extended rather than Default, because Default is a subset of it: a starter is held to the strictest
    # verification of the harness it was made by, so the application a user starts from passes both.
    run_maven "starter_verify_${verified_id}" starters "${starter_application}" clean verify -Pextended
    expect_exit "starter_verify_${verified_id}" \
        "starters: the application made from the ${verified_starter} starter passes mvn clean verify -Pextended" 0
    expect_match "starter_verify_${verified_id}" \
        "starters: the node gates run on the ${verified_starter} application" 'Node gate knip in [.] passed'
    expect_match "starter_verify_${verified_id}" \
        "starters: Dependency-Check reads the dependencies of the ${verified_starter} application" \
        'dependency-check:[^ ]+:check [(]airness-known-vulnerabilities[)]'
    expect_match "starter_verify_${verified_id}" \
        "starters: the browser sweep and the end-to-end suite pass on the ${verified_starter} application" \
        'Node gate e2e in [.] passed'
    # The image copies what the build made rather than installing packages, so it is built and booted under a
    # SNAPSHOT harness as well, whose packages no registry a Docker build reaches holds.
    expect_match "starter_verify_${verified_id}" \
        "starters: the image of the ${verified_starter} application boots against empty services" \
        'Node gate image in [.] passed'
}

run_starter_cases() {
    verify_starter spring-boot task-tracker -Dairness.groupId=org.sample
    expect_match starter_new_spring_boot 'starters: airness:new names the application from its artifact' \
        'Made Task Tracker in .*/task-tracker[.]'
    expect_file_match "${starter_application}/src/main/java/org/sample/tasktracker/Application.java" \
        'starters: the code moves to the package of the application' '^package org[.]sample[.]tasktracker;'
    expect_match starter_verify_spring_boot 'starters: the coverage floor holds for the application' \
        'All coverage checks have been met'
    expect_match starter_verify_spring_boot 'starters: Qodana finds no problem in the application' \
        'Analysis results: 0 problem detected'
    expect_file_match "${starter_application}/pom.xml" 'starters: the application begins in the year it is made' \
        "<inceptionYear>$(date +%Y)</inceptionYear>"
    expect_file_match "${starter_application}/pom.xml" 'starters: the source control names the repository given' \
        '<connection>scm:git:https://git[.]example[.]invalid/task-tracker[.]git</connection>'
    expect_file_match "${starter_application}/pom.xml" 'starters: the application is proprietary by default' \
        '<name>LicenseRef-Proprietary</name>'
    expect_file_match "${starter_application}/package.json" 'starters: the package.json repeats the license' \
        '"license": "LicenseRef-Proprietary"'

    run_maven starter_occupied starters "${starter_home}" "${starter_goal}" \
        -Dairness.groupId=org.sample -Dairness.artifactId=task-tracker \
        "${starter_organization}" -Dairness.url=https://git.example.invalid/task-tracker
    expect_exit starter_occupied 'starters: airness:new refuses a directory that is not empty' 1
    expect_match starter_occupied 'starters: the refusal names the occupied directory' \
        'task-tracker already exists and is not empty'

    run_maven starter_keyword starters "${starter_home}" "${starter_goal}" \
        -Dairness.groupId=org.sample -Dairness.artifactId=new \
        "${starter_organization}" -Dairness.url=https://git.example.invalid/new
    expect_exit starter_keyword 'starters: airness:new refuses a package with a Java keyword in it' 1
    expect_match starter_keyword 'starters: the refusal names the package' \
        "airness.package is 'org.sample.new', but a part of it is a Java keyword"

    run_maven starter_owner starters "${starter_home}" "${starter_goal}" \
        -Dairness.groupId=org.sample -Dairness.artifactId=owned -Dairness.url=http://git.example.invalid/owned \
        -Dairness.license=Unlicense
    expect_exit starter_owner 'starters: airness:new refuses an application nobody owns' 1
    expect_match starter_owner 'starters: the refusal asks who owns the application' \
        'airness[.]organization is not set'
    expect_match starter_owner 'starters: the refusal asks for an https repository' \
        "airness[.]url is 'http://git[.]example[.]invalid/owned', but it must be the https address"
    expect_match starter_owner 'starters: the refusal keeps the code from the public domain' \
        "airness[.]license is 'Unlicense', but Unlicense places the code in the public domain"
}
