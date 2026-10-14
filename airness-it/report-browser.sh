#!/usr/bin/env sh
# Installs the same browser the Maven plugin uses, before tests that cannot download tooling themselves.
set -eu

repository="$(cd "$(dirname "$0")/.." && pwd)"
version="$(sed -n 's|^ *<playwright.version>\(.*\)</playwright.version> *$|\1|p' \
    "${repository}/airness-maven-plugin/pom.xml")"
test -n "${version}"
local_repository="${MAVEN_REPO_LOCAL:-${HOME}/.m2/repository}"
mvn --batch-mode --non-recursive --file "${repository}/pom.xml" dependency:get \
    "-Dartifact=com.microsoft.playwright:playwright:${version}" "-Dmaven.repo.local=${local_repository}"
base="${local_repository}/com/microsoft/playwright"
classpath="${base}/playwright/${version}/playwright-${version}.jar"
classpath="${classpath}:${base}/driver/${version}/driver-${version}.jar"
classpath="${classpath}:${base}/driver-bundle/${version}/driver-bundle-${version}.jar"
case "${1-}" in
    '')
        java -cp "${classpath}" com.microsoft.playwright.CLI install chromium
        ;;
    --with-deps)
        java -cp "${classpath}" com.microsoft.playwright.CLI install --with-deps chromium
        ;;
    *)
        printf 'Usage: sh airness-it/report-browser.sh [--with-deps]\n' >&2
        exit 2
        ;;
esac
