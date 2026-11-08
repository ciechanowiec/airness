#!/usr/bin/env sh
# Installs the same browser the Maven plugin uses, before tests that cannot download tooling themselves.
set -eu

repository="$(cd "$(dirname "$0")/.." && pwd)"
version="$(sed -n 's|^ *<playwright.version>\(.*\)</playwright.version> *$|\1|p' \
    "${repository}/airness-maven-plugin/pom.xml")"
test -n "${version}"
local_repository="${MAVEN_REPO_LOCAL:-${HOME}/.m2/repository}"
# Maven copies each jar out of whichever repository holds it, because a build that chains the shared
# repository behind its own through maven.repo.local.tail resolves it there and never into its own.
jars="$(mktemp -d)"
trap 'rm -rf "${jars}"' EXIT
classpath=''
for artifact in playwright driver driver-bundle; do
    mvn --batch-mode --no-transfer-progress --quiet --non-recursive --file "${repository}/pom.xml" \
        dependency:copy "-Dartifact=com.microsoft.playwright:${artifact}:${version}" \
        "-DoutputDirectory=${jars}" "-Dmaven.repo.local=${local_repository}"
    classpath="${classpath:+${classpath}:}${jars}/${artifact}-${version}.jar"
done
report_browser_path="${HOME}/.cache/airness/report-browser/${version}"
case "${1-}" in
    '')
        PLAYWRIGHT_BROWSERS_PATH="${report_browser_path}" \
            java -cp "${classpath}" com.microsoft.playwright.CLI install chromium
        ;;
    --with-deps)
        PLAYWRIGHT_BROWSERS_PATH="${report_browser_path}" \
            java -cp "${classpath}" com.microsoft.playwright.CLI install --with-deps chromium
        ;;
    *)
        printf 'Usage: sh airness-it/report-browser.sh [--with-deps]\n' >&2
        exit 2
        ;;
esac
