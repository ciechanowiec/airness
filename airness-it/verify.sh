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
# The three source-shaping tools run at process-resources, well before anything below them, and this
# module's fixtures are unformatted on purpose. Left running they would end every build before the case
# under test got anywhere, so each case that is about something else turns them off, and the three cases
# that are about them turn them back on one at a time.
no_shaping='-Drewrite.skip=true -Dformatter.skip=true -Dimpsort.skip=true'
only_checkstyle="-Dpmd.skip=true -Dspotbugs.skip=true $lenient $no_shaping"
only_pmd="-Dcheckstyle.skip=true -Dspotbugs.skip=true $lenient $no_shaping"
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

# SpotBugs is the analyzer whose silence says nothing: a filter it could not resolve leaves it
# reporting zero bugs, and so does a clean project. No project can repoint that filter, so the way this
# is watched is a fixture carrying a bug the shipped configuration is known to report. If the resource
# ever moves, the plugin fails to resolve it and this finding stops appearing.
# shellcheck disable=SC2086
expect 'spotbugs: the shipped filter reaches consumer sources' \
    'EI_EXPOSE_REP' 1 \
    -Dcheckstyle.skip=true -Dpmd.skip=true -Dairness.governance.enforce=false $no_shaping

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
    'Set airness\.group\.root' 1 $no_shaping -Dairness.group.root=UNSET

# The governance goals read the repository rather than the code being built, and they reach this
# consumer's tree through the parent alone. Each case names the rule it is about, so a rule that stops
# resolving shows up here rather than as a quieter build everywhere the harness is used.
# shellcheck disable=SC2086
expect 'governance: typography reaches a tracked file' \
    'Banned typography found' 1 $no_analyzers $no_shaping $lenient
# shellcheck disable=SC2086
expect 'governance: comment prose reaches consumer sources' \
    'semicolon where a full stop' 1 $no_analyzers $no_shaping $lenient
# shellcheck disable=SC2086
expect 'governance: a full stop on a @return is reported' \
    '@return completes' 1 $no_analyzers $no_shaping $lenient
# shellcheck disable=SC2086
expect 'governance: javadoc links reaches consumer sources' \
    'does not link, though they resolve' 1 $no_analyzers $no_shaping $lenient

# The claim the whole plugin exists for. A check that shipped as a test would be gone under either of
# these two flags, and neither of them is a request to stop reading the repository. Both spellings are
# named, because a hole with two spellings is two holes to anyone who only knows the first.
# shellcheck disable=SC2086
expect 'governance: the gates still run under -DskipTests' \
    'Banned typography found' 1 $no_analyzers $no_shaping $lenient -DskipTests
# shellcheck disable=SC2086
expect 'governance: the gates still run under -Dmaven.test.skip' \
    'Banned typography found' 1 $no_analyzers $no_shaping $lenient -Dmaven.test.skip=true

# The adoption ramp withholds the failure and nothing else. Both halves are needed: without the first,
# a project taking the harness on could not build at all, and without the second the ramp would be a
# skip, which leaves that project with no idea what it is in for.
# shellcheck disable=SC2086
expect 'governance: a finding fails the build when enforcement is on' \
    'BUILD FAILURE' 1 $no_analyzers $no_shaping
# shellcheck disable=SC2086
expect 'governance: enforcement off withholds the failure alone' \
    'BUILD SUCCESS' 1 $no_analyzers $no_shaping $lenient

# An exemption that stops excluding anything is reported. Without this a list of vendored directories
# would outlive the directories, and the next one added under a name already on the list would be
# exempt without anyone deciding that.
# shellcheck disable=SC2086
expect 'governance: an exemption that excludes nothing is reported' \
    'exclusion prefix excluded nothing' 1 $no_analyzers $no_shaping $lenient \
    -Dairness.typography.excludes=.vale/,docinfo,no-such-directory/

# The parameters the harness cannot default stop the build at validate, before a check that reads one
# of them has the chance to pass by reading nothing.
# shellcheck disable=SC2086
expect 'preflight: an unset package root stops the build' \
    'Set airness\.package\.root' 1 $no_analyzers $no_shaping -Dairness.package.root=UNSET
# shellcheck disable=SC2086
expect 'preflight: an empty entry-file list is not a declaration' \
    'Set airness\.entry\.files' 1 $no_analyzers $no_shaping -Dairness.entry.files=
# shellcheck disable=SC2086
expect 'preflight: a group root and a package root that disagree are reported' \
    'describe different projects' 1 $no_analyzers $no_shaping -Dairness.group.root='com\.example'

# The files the harness owns are checked against the bytes it ships. airness is its own first consumer
# here: this module's repository is the airness working tree, so a drift between what airness ships and
# what airness itself holds fails these cases rather than going unnoticed until some other project
# inherits it.
# shellcheck disable=SC2086
expect 'assets: an opt-out that no longer differs is reported' \
    'no longer differ' 1 $no_analyzers $no_shaping $lenient -Dairness.assets.unmanaged=.editorconfig
# shellcheck disable=SC2086
expect 'assets: an opt-out naming nothing is reported' \
    'does not own' 1 $no_analyzers $no_shaping $lenient -Dairness.assets.unmanaged=no-such-file

# A file the harness supplies from elsewhere must not also sit in the tree, where it would be edited and
# then ignored. rewrite.yml is the case: OpenRewrite reads the recipe set off its own plugin classpath,
# so a copy in the project is a copy nothing reads. The fixture is created and removed here, because the
# only tree this consumer has is the one it lives in.
trap 'rm -f ../rewrite.yml' EXIT INT TERM
printf 'a file the harness already supplies\n' > ../rewrite.yml
# shellcheck disable=SC2086
expect 'assets: a forbidden file in the tree is reported' \
    'must not be in the tree' 1 $no_analyzers $no_shaping $lenient
rm -f ../rewrite.yml
trap - EXIT INT TERM

# The repair goal is the counterpart of the check, and the pair has to be shown working together: a
# check that reports drift nobody can fix is advice, and a sync nothing verifies is a hope. The pinned
# file is perturbed, seen to be reported, repaired by the goal, and seen to be clean again.
cp ../.editorconfig ../.editorconfig.orig
trap 'mv -f ../.editorconfig.orig ../.editorconfig 2>/dev/null || true' EXIT INT TERM
printf '\n# a line this project added\n' >> ../.editorconfig
# shellcheck disable=SC2086
expect 'assets: a drifted pinned file is reported' \
    'changed or is missing' 1 $no_analyzers $no_shaping $lenient
mvn -q airness:assets-sync > /dev/null 2>&1
# shellcheck disable=SC2086
expect 'assets: the sync goal repairs what the check reported' \
    'changed or is missing' 0 $no_analyzers $no_shaping $lenient
# Restored from the backup rather than left as the sync goal put it. If that goal ever stops repairing,
# the case above goes red and this line is what stops the drift being left in the tree as well.
mv -f ../.editorconfig.orig ../.editorconfig
trap - EXIT INT TERM

# The three source-shaping tools reach this consumer's sources, each reading its configuration off the
# plugin classpath rather than from a file in the project. Neither the formatter profile nor the recipe
# set is a property a project can repoint, so the only way either stops resolving is if this repository
# moves the resource, and then these cases go red: an unresolvable profile makes the plugin fail before
# it can report a formatting finding at all.
expect 'formatter: the shipped Eclipse profile reaches consumer sources' \
    'has not been previously formatted' 1 \
    -Dcheckstyle.skip=true -Dpmd.skip=true -Dspotbugs.skip=true -Drewrite.skip=true -Dimpsort.skip=true \
    -Dairness.governance.enforce=false
expect 'rewrite: the shipped recipe set is discovered from the plugin classpath' \
    'Using active recipe\(s\) \[eu.ciechanowiec.airness.Modernization\]' 1 \
    -Dcheckstyle.skip=true -Dpmd.skip=true -Dspotbugs.skip=true -Dformatter.skip=true -Dimpsort.skip=true \
    -Dairness.governance.enforce=false

if [ "$failures" -ne 0 ]; then
    printf '\n%s case(s) failed: the harness is not reaching a consumer the way it claims to.\n' "$failures" >&2
    exit 1
fi
printf '\nAll cases passed.\n'
