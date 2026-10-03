#!/usr/bin/env sh

add_early_qodana_sources() {
    python3 - "${repository}/airness-governance/src/test/resources/early-analysis.xml" "$1" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

inventory = ET.parse(sys.argv[1])
destination = Path(sys.argv[2]) / 'src/main/java'
for index, rule in enumerate(inventory.findall("rule[@tool='Qodana']")):
    for number, case in enumerate(rule.findall('case')):
        package = f'com.example.early.rule{index}.case{number}'
        directory = destination / package.replace('.', '/')
        directory.mkdir(parents=True, exist_ok=True)
        source = case.findtext('source').replace('package example;', f'package {package};')
        (directory / 'Sample.java').write_text(source)
PY
}

verify_early_qodana_findings() {
    if python3 - "${repository}/airness-governance/src/test/resources/early-analysis.xml" "$1" <<'PY'
from collections import Counter
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

inventory = ET.parse(sys.argv[1])
report = json.loads(Path(sys.argv[2]).read_text())
findings = Counter(
    (finding['ruleId'], location['physicalLocation']['artifactLocation']['uri'])
    for run in report['runs']
    for finding in run.get('results', [])
    for location in finding.get('locations', [])
)
problems = []
for index, rule in enumerate(inventory.findall("rule[@tool='Qodana']")):
    for number, case in enumerate(rule.findall('case')):
        path = f'src/main/java/com/example/early/rule{index}/case{number}/Sample.java'
        expected = int(case.get('original', case.get('findings')))
        actual = findings[rule.get('name'), path]
        if actual != expected:
            problems.append(f'{rule.get("name")} at {path}: expected {expected}, got {actual}')
for problem in problems:
    print(problem, file=sys.stderr)
sys.exit(bool(problems))
PY
    then
        pass 'qodana: every early counterpart agrees with the retained inspection on shared fixtures'
    else
        fail 'qodana: an early counterpart disagrees with the retained inspection'
    fi
}

run_early_analysis_lifecycle() {
    new_consumer early-qodana
    cat > "${consumer_directory}/src/main/java/com/example/Identity.java" <<'JAVA'
package com.example;

final class Identity {

    int value(int foo) {
        return foo;
    }
}
JAVA
    expect_static_refusal "${consumer_directory}" early_qodana 'QuestionableName'
    expect_no_match early_qodana 'early analysis: a Qodana counterpart fails before the tests run' \
        'surefire:[^ ]+:test '

    new_consumer early-pmd
    cat > "${consumer_directory}/src/main/java/com/example/Explained.java" <<'JAVA'
package com.example;

import eu.ciechanowiec.airness.Justification;

@Justification(" ")
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class Explained {
}
JAVA
    expect_static_refusal "${consumer_directory}" early_pmd 'PMD.JustificationNeedsText'
    expect_no_match early_pmd 'early analysis: a PMD counterpart fails before the tests run' \
        'surefire:[^ ]+:test '
    run_maven early_original_pmd analysis "${consumer_directory}" pmd:check
    expect_exit early_original_pmd 'early analysis: the original PMD check remains enforcing' 1
    expect_match early_original_pmd 'early analysis: the original PMD rule still reports the violation' \
        'Rule:JustificationNeedsText'
}
