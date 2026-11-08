#!/usr/bin/env sh

# Names, for the script that sources it after setting repository to the clone's root, the local Maven repository
# into which this clone's builds install the harness, as local_repository.
#
# MAVEN_REPO_LOCAL overrides the answer. Otherwise Maven itself is asked, from the clone's root, so the answer is
# the one .mvn/maven.config and any MAVEN_ARGS give: the clone's repository is named after
# ${session.rootDirectory}, which Maven resolves through a symbolic link that pwd keeps. The help plugin is named
# with its version, because the aggregator manages no version for it and a bare prefix reads the newest one off
# the network. The plugin resolves from either repository, so the question needs no network once it has run.

if [ -n "${MAVEN_REPO_LOCAL-}" ]; then
    local_repository="${MAVEN_REPO_LOCAL}"
elif ! local_repository="$(cd "${repository}" && mvn --batch-mode --quiet --non-recursive \
    org.apache.maven.plugins:maven-help-plugin:3.5.2:evaluate \
    -Dexpression=settings.localRepository -DforceStdout)"; then
    printf 'Maven could not name the local repository of %s:\n%s\n' "${repository}" "${local_repository}" >&2
    exit 1
fi
