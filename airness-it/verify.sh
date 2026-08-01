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

# Every analyzer case turns governance enforcement off. The governance goals run before the analyzers
# and fail on fixtures this module carries on purpose, so left enforcing they would end the build before
# an analyzer ever ran, and every case below would then pass by finding nothing rather than by finding
# what it claims. Enforcement off withholds the failure and nothing else, which is what is wanted here
# and is itself a case further down.
lenient='-Dairness.governance.enforce=false'
only_checkstyle="-Dpmd.skip=true -Dspotbugs.skip=true $lenient"
only_pmd="-Dcheckstyle.skip=true -Dspotbugs.skip=true $lenient"
no_analyzers='-Dcheckstyle.skip=true -Dpmd.skip=true -Dspotbugs.skip=true'

# Checkstyle resolves its configuration, including the custom AST checks that ship beside it. Naming
# the rules rather than counting findings keeps the case honest when the rule set grows.
# shellcheck disable=SC2086
expect 'checkstyle: shipped rules reach consumer sources' \
    '^\[ERROR\].*\[(RequireFinalClass|MagicNumber|NeedBraces)\]' 3 $only_checkstyle

# PMD resolves its ruleset off the same classpath. Named rather than counted, for the reason given
# above: a case that counts every finding in the module has to be retuned each time a fixture is added
# for some other case, and a number retuned that often stops being read as a claim about anything.
# shellcheck disable=SC2086
expect 'pmd: shipped ruleset reaches consumer sources' \
    'PMD Failure.*Rule:(ControlStatementBraces|UnnecessaryFullyQualifiedName)' 3 $only_pmd

# The annotation the rule set demands is inherited from the parent, so a project that suppresses a rule
# does not first have to write an annotation of its own. Both halves are needed. Without the first the
# rule enforces nothing, and without the second it would be firing on the suppression rather than on the
# missing reason, which is a rule no correct code could satisfy. The two fixtures differ in that one
# annotation and in nothing else.
# shellcheck disable=SC2086
expect 'justification: an unexplained suppression fires the rule' \
    '\.Unjustified:.*Rule:SuppressionNeedsJustification' 1 $only_pmd
# shellcheck disable=SC2086
expect 'justification: the inherited annotation satisfies the rule' \
    '\.Justified:.*Rule:SuppressionNeedsJustification' 0 $only_pmd

# A suppression that suppresses nothing is a finding of its own. Without this, a suppression would
# outlive the code it covered and read as a rule deliberately set aside, which is the opposite of what
# it would then mean.
# shellcheck disable=SC2086
expect 'justification: a suppression that suppresses nothing is reported' \
    '\.Useless:.*Rule:UnnecessaryWarningSuppression' 1 $only_pmd

# A configuration path that does not resolve fails the build rather than disabling a rule set. SpotBugs
# is the case that needs stating: with a filter it cannot find it would otherwise report zero bugs,
# which is indistinguishable from a filter that loaded and matched nothing.
expect 'spotbugs: unresolvable filter fails the build' \
    "Could not find resource 'BOGUS" 1 \
    -Dcheckstyle.skip=true -Dpmd.skip=true -Dairness.spotbugs.config=BOGUS.xml \
    -Dairness.governance.enforce=false

# The group root reaches the fully-qualified-name rule. Pointed at this project's group the rule fires,
# and pointed at another project's group it goes quiet: the second case is the one worth pinning,
# because a rule that never fires is a rule that passes while enforcing nothing.
# shellcheck disable=SC2086
expect 'group root: configured root makes the rule fire' \
    'Qualified\.java.*Unnecessary fully-qualified' 1 $only_checkstyle
# shellcheck disable=SC2086
expect 'group root: foreign root leaves the rule silent' \
    'Qualified\.java' 0 $only_checkstyle \
    -Dairness.group.root='com\.example' -Dairness.package.root=com.example.airness.it

# A group root left unset stops the build before an analyzer runs. Unset, it reaches the rule as an
# alternative matching nothing while java/javax/jakarta still match, so the rule would report on JDK
# names and nothing else: partial enforcement that reads as full enforcement.
expect 'group root: unset stops the build at validate' \
    'Set airness\.group\.root' 1 -Dairness.group.root=UNSET

# The governance goals read the repository rather than the code being built, and they reach this
# consumer's tree through the parent alone. Each case names the rule it is about, so a rule that stops
# resolving shows up here rather than as a quieter build everywhere the harness is used.
# shellcheck disable=SC2086
expect 'governance: typography reaches a tracked file' \
    'Banned typography found' 1 $no_analyzers $lenient
# shellcheck disable=SC2086
expect 'governance: comment prose reaches consumer sources' \
    'semicolon where a full stop' 1 $no_analyzers $lenient
# shellcheck disable=SC2086
expect 'governance: a full stop on a @return is reported' \
    '@return completes' 1 $no_analyzers $lenient
# shellcheck disable=SC2086
expect 'governance: javadoc links reaches consumer sources' \
    'does not link, though they resolve' 1 $no_analyzers $lenient

# The claim the whole plugin exists for. A check that shipped as a test would be gone under either of
# these two flags, and neither of them is a request to stop reading the repository. Both spellings are
# named, because a hole with two spellings is two holes to anyone who only knows the first.
# shellcheck disable=SC2086
expect 'governance: the gates still run under -DskipTests' \
    'Banned typography found' 1 $no_analyzers $lenient -DskipTests
# shellcheck disable=SC2086
expect 'governance: the gates still run under -Dmaven.test.skip' \
    'Banned typography found' 1 $no_analyzers $lenient -Dmaven.test.skip=true

# The adoption ramp withholds the failure and nothing else. Both halves are needed: without the first,
# a project taking the harness on could not build at all, and without the second the ramp would be a
# skip, which leaves that project with no idea what it is in for.
# shellcheck disable=SC2086
expect 'governance: a finding fails the build when enforcement is on' \
    'BUILD FAILURE' 1 $no_analyzers
# shellcheck disable=SC2086
expect 'governance: enforcement off withholds the failure alone' \
    'BUILD SUCCESS' 1 $no_analyzers $lenient

# An exemption that stops excluding anything is reported. Without this a list of vendored directories
# would outlive the directories, and the next one added under a name already on the list would be
# exempt without anyone deciding that.
# shellcheck disable=SC2086
expect 'governance: an exemption that excludes nothing is reported' \
    'exclusion prefix excluded nothing' 1 $no_analyzers $lenient \
    -Dairness.typography.excludes=.vale/,docinfo,no-such-directory/

# The parameters the harness cannot default stop the build at validate, before a check that reads one
# of them has the chance to pass by reading nothing.
# shellcheck disable=SC2086
expect 'preflight: an unset package root stops the build' \
    'Set airness\.package\.root' 1 $no_analyzers -Dairness.package.root=UNSET
# shellcheck disable=SC2086
expect 'preflight: an empty entry-file list is not a declaration' \
    'Set airness\.entry\.files' 1 $no_analyzers -Dairness.entry.files=
# shellcheck disable=SC2086
expect 'preflight: a group root and a package root that disagree are reported' \
    'describe different projects' 1 $no_analyzers -Dairness.group.root='com\.example'

if [ "$failures" -ne 0 ]; then
    printf '\n%s case(s) failed: the harness is not reaching a consumer the way it claims to.\n' "$failures" >&2
    exit 1
fi
printf '\nAll cases passed.\n'
