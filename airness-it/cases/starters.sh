# The starters domain: an application airness:new makes from a starter passes Default verification as it is
# made, and the goal refuses a request it cannot make. It needs Docker, because the application's tests start
# their own PostgreSQL.

starter_goal() {
    printf 'eu.ciechanowiec:airness-maven-plugin:%s:new' "${harness_version}"
}

run_starter_cases() {
    starter_home="${scratch}/starters"
    mkdir -p "${starter_home}"
    # The goal commits the application it makes, and a CI runner configures no Git identity.
    GIT_AUTHOR_NAME=Fixture
    GIT_AUTHOR_EMAIL=fixture@example.invalid
    GIT_COMMITTER_NAME=Fixture
    GIT_COMMITTER_EMAIL=fixture@example.invalid
    export GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL

    run_maven starter_new starters "${starter_home}" "$(starter_goal)" \
        -Dairness.groupId=org.sample -Dairness.artifactId=task-tracker
    expect_exit starter_new 'starters: airness:new makes an application from the spring-boot starter' 0
    expect_match starter_new 'starters: airness:new names the application and where it made it' \
        'Made Task Tracker in .*/task-tracker[.]'
    starter_application="${starter_home}/task-tracker"
    expect_file_match "${starter_application}/src/main/java/org/sample/tasktracker/Application.java" \
        'starters: the code moves to the package of the application' '^package org[.]sample[.]tasktracker;'
    expect_file_match "${starter_application}/pnpm-lock.yaml" \
        'starters: the first lockfile resolves the installed harness package' "'@airness/node@${harness_version}'"
    expect_file_match "${starter_application}/.airness/agent-guide.md" \
        'starters: assets-init writes the managed files of the harness that made the application' 'Airness'
    starter_commits="$(git -C "${starter_application}" rev-list --count HEAD 2>/dev/null || printf '0')"
    starter_changes="$(git -C "${starter_application}" status --porcelain 2>/dev/null || printf 'unreadable')"
    if [ "${starter_commits}" = 1 ] && [ -z "${starter_changes}" ]; then
        pass 'starters: the application is one commit with nothing left uncommitted'
    else
        fail 'starters: the application is one commit with nothing left uncommitted' \
            "${starter_commits} commit(s), and uncommitted: ${starter_changes}"
    fi

    run_maven starter_verify starters "${starter_application}" clean verify
    expect_exit starter_verify 'starters: the application made from the spring-boot starter passes mvn clean verify' 0
    expect_match starter_verify 'starters: the node gates run on the application' 'Node gate knip in [.] passed'
    expect_match starter_verify 'starters: the coverage floor holds for the application' \
        'All coverage checks have been met'

    run_maven starter_occupied starters "${starter_home}" "$(starter_goal)" \
        -Dairness.groupId=org.sample -Dairness.artifactId=task-tracker
    expect_exit starter_occupied 'starters: airness:new refuses a directory that is not empty' 1
    expect_match starter_occupied 'starters: the refusal names the occupied directory' \
        'task-tracker already exists and is not empty'

    run_maven starter_keyword starters "${starter_home}" "$(starter_goal)" \
        -Dairness.groupId=org.sample -Dairness.artifactId=new
    expect_exit starter_keyword 'starters: airness:new refuses a package with a Java keyword in it' 1
    expect_match starter_keyword 'starters: the refusal names the package' \
        "airness.package is 'org.sample.new', but a part of it is a Java keyword"
}
