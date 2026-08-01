#!/usr/bin/env sh
# Asserts that the harness a consumer inherits actually reaches that consumer's sources.
#
# Every case here is a claim that would otherwise be checked by reading build output, which is a check
# only for as long as someone keeps reading it. A configuration jar and a parent pom have no behaviour
# of their own to test: what can regress is whether an analyzer still resolves its configuration off the
# plugin classpath, and that is visible only from a project that inherits the parent. Hence a real
# consumer with sources that violate rules on purpose, and hence this script, which fails on a finding
# that stops appearing.
#
# An analyzer upgrade that renames a category path, or a rule that quietly stops resolving, shows up
# here as a missing finding rather than as a green build in every project that inherits the harness.
#
# Run from this directory, with the parent and the configuration jar installed. A non-zero exit means
# the harness did not reach the consumer the way it claims to.
set -eu

failures=0

# Runs one case and compares what the build reported against what the case expects. The build is
# expected to fail in most cases (the sources violate rules on purpose), so its exit status is not the
# signal and is deliberately discarded; the findings are.
expect() {
    label="$1"
    pattern="$2"
    wanted="$3"
    shift 3
    found="$(mvn clean package "$@" 2>&1 | grep -cE "$pattern" || true)"
    if [ "$found" -eq "$wanted" ]; then
        printf 'ok       %s\n' "$label"
    else
        printf 'FAILED   %s (expected %s matches of /%s/, got %s)\n' "$label" "$wanted" "$pattern" "$found"
        failures=$((failures + 1))
    fi
}

only_checkstyle='-Dpmd.skip=true -Dspotbugs.skip=true'

# Checkstyle resolves its configuration, including the custom AST checks that ship beside it. Naming
# the rules rather than counting findings keeps the case honest when the rule set grows.
# shellcheck disable=SC2086
expect 'checkstyle: shipped rules reach consumer sources' \
    '^\[ERROR\].*\[(RequireFinalClass|MagicNumber|NeedBraces)\]' 3 $only_checkstyle

# PMD resolves its ruleset off the same classpath.
expect 'pmd: shipped ruleset reaches consumer sources' \
    'PMD Failure' 3 -Dcheckstyle.skip=true -Dspotbugs.skip=true

# A configuration path that does not resolve fails the build rather than disabling a rule set. SpotBugs
# is the case that needs stating: with a filter it cannot find it would otherwise report zero bugs,
# which is indistinguishable from a filter that loaded and matched nothing.
expect 'spotbugs: unresolvable filter fails the build' \
    "Could not find resource 'BOGUS" 1 \
    -Dcheckstyle.skip=true -Dpmd.skip=true -Dairness.spotbugs.config=BOGUS.xml

# The group root reaches the fully-qualified-name rule. Pointed at this project's group the rule fires,
# and pointed at another project's group it goes quiet: the second case is the one worth pinning,
# because a rule that never fires is a rule that passes while enforcing nothing.
# shellcheck disable=SC2086
expect 'group root: configured root makes the rule fire' \
    'Qualified\.java.*Unnecessary fully-qualified' 1 $only_checkstyle
# shellcheck disable=SC2086
expect 'group root: foreign root leaves the rule silent' \
    'Qualified\.java' 0 $only_checkstyle -Dairness.group.root='com\.example'

# A group root left unset stops the build before an analyzer runs. Unset, it reaches the rule as an
# alternative matching nothing while java/javax/jakarta still match, so the rule would report on JDK
# names and nothing else: partial enforcement that reads as full enforcement.
expect 'group root: unset stops the build at validate' \
    'still UNSET' 1 -Dairness.group.root=UNSET

if [ "$failures" -ne 0 ]; then
    printf '\n%s case(s) failed: the harness is not reaching a consumer the way it claims to.\n' "$failures" >&2
    exit 1
fi
printf '\nAll cases passed.\n'
