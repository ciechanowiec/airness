#!/usr/bin/env sh

# Reuses the real, local advisory feed and patched tool graph of the scanner contract.
run_compliance_report_cases() {
    prepare_compliance_reactor
    run_compliance_evidence compliance_report -T2
    expect_exit compliance_report 'compliance: current license, scanner and SBOM evidence produces both formats' 0
    if python3 "${repository}/airness-it/compliance-report-check.py" "${vulnerability_consumer}" patched; then
        pass 'compliance: the report embeds the evaluated tool inventory and source identity'
    else
        fail 'compliance: the report embeds the evaluated tool inventory and source identity'
    fi
    run_compliance_evidence compliance_partial -pl report-first
    expect_exit compliance_partial 'compliance: a selected module can publish a draft reactor report' 0
    report_commit="$(git -C "${vulnerability_consumer}" rev-parse --short=12 HEAD)"
    expect_file_match \
        "${vulnerability_consumer}/target/compliance/oss-compliance-report-consumer-${report_commit}.html" \
        'compliance: the root report identifies a partial reactor' 'selected only part of the reactor'
    run_maven compliance_stale maven "${vulnerability_consumer}" -Pextended \
        airness:tree-snapshot airness:compliance-report-collect
    expect_exit compliance_stale 'compliance: a new invocation refuses earlier scanner and license evidence' 1
    expect_match compliance_stale 'compliance: stale evidence names regeneration as the repair' \
        'evidence predates this Maven invocation'
    rm -rf "${vulnerability_consumer}/target/compliance"
    run_maven compliance_skipped maven "${vulnerability_consumer}" -Pextended -DskipTests airness:compliance-report-collect
    expect_exit compliance_skipped 'compliance: a skipped build produces no new verdict' 0
    if [ ! -e "${vulnerability_consumer}/target/compliance" ]; then
        pass 'compliance: skipped verification produces no report pair'
    else
        fail 'compliance: skipped verification produces no report pair'
    fi
    run_standalone_compliance_report
}

run_compliance_evidence() {
    report_execution="$1"
    shift
    run_maven "${report_execution}" maven "${vulnerability_consumer}" "$@" -Pextended -Dairness.enforce=false \
        airness:tree-snapshot install:help shade:help download:help assembly:help build-helper:help failsafe:help \
        airness:plugin-inputs license:add-third-party@airness-license-evidence cyclonedx:makeBom \
        org.owasp:dependency-check-maven:check airness:compliance-report-collect
}

run_standalone_compliance_report() {
    new_consumer compliance-standalone
    run_maven compliance_standalone maven "${consumer_directory}" -Pextended -Dairness.enforce=false airness:compliance-report
    expect_exit compliance_standalone 'compliance: the public command regenerates its prerequisite evidence' 0
    if python3 "${repository}/airness-it/compliance-report-check.py" "${consumer_directory}" standalone; then
        pass 'compliance: standalone output identifies the invocation as a draft'
    else
        fail 'compliance: standalone output identifies the invocation as a draft'
    fi
}

prepare_compliance_reactor() {
    python3 - "${vulnerability_consumer}" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(sys.argv[1])
pom = root / "pom.xml"
source = pom.read_text(encoding="utf-8")
model = ET.fromstring(source)
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
group = model.findtext("m:groupId", namespaces=namespace)
artifact = model.findtext("m:artifactId", namespaces=namespace)
version = model.findtext("m:version", namespaces=namespace)
modules = ("report-first", "report-second")
for module in modules:
    directory = root / module
    directory.mkdir()
    directory.joinpath("pom.xml").write_text(
        '<project xmlns="http://maven.apache.org/POM/4.0.0">'
        '<modelVersion>4.0.0</modelVersion><parent>'
        f'<groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>{version}</version>'
        f'</parent><artifactId>{module}</artifactId><packaging>pom</packaging></project>\n',
        encoding="utf-8",
    )
pom.write_text(source.replace(
    '</project>', '<packaging>pom</packaging><modules>'
    + ''.join(f'<module>{module}</module>' for module in modules) + '</modules></project>',
), encoding="utf-8")
PYTHON
}
