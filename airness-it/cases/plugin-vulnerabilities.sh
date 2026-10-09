#!/usr/bin/env sh

# The scanner reads a fixed public advisory through its real file-feed and database implementations.
# Plugin declaration overrides below are confined to this deliberately noncompliant scanner fixture.
run_plugin_vulnerability_cases() {
    prepare_vulnerability_feed
    prepare_vulnerability_scan
    run_maven plugin_vulnerability maven "${vulnerability_consumer}" \
        install:help airness:plugin-inputs org.owasp:dependency-check-maven:check
    expect_exit plugin_vulnerability 'vulnerabilities: a finding reachable only through a plugin fails the scan' 1
    expect_match plugin_vulnerability 'vulnerabilities: the fixed advisory reaches the failure gate' 'CVE-2021-44228'
    if python3 "${repository}/airness-it/plugin-scan-check.py" \
        "${vulnerability_consumer}/target/dependency-check-report.json"; then
        pass 'vulnerabilities: plugin artifacts and transitive dependencies are scanned'
    else
        fail 'vulnerabilities: plugin artifacts and transitive dependencies are scanned'
    fi
    perl -0pi -e 's{<version>2.14.1</version>}{<version>2.26.1</version>}' \
        "${vulnerability_consumer}/pom.xml"
    run_maven plugin_scan_control maven "${vulnerability_consumer}" \
        install:help airness:plugin-inputs org.owasp:dependency-check-maven:check
    expect_exit plugin_scan_control 'vulnerabilities: a patched plugin override replaces the vulnerable version' 0
    expect_file_no_match "${vulnerability_consumer}/target/dependency-check-report.json" \
        'vulnerabilities: replaced plugin versions leave no stale scan inputs' 'log4j-core-2[.]14[.]1'
}

prepare_vulnerability_feed() {
    new_consumer plugin-vulnerability
    vulnerability_consumer="${consumer_directory}"
    mkdir -p "${vulnerability_consumer}/feed"
    cp "${repository}/airness-it/fixtures/nvd/cve-2021-44228.json" "${vulnerability_consumer}/feed/advisory.json"
    python3 - "${vulnerability_consumer}/feed" <<'PY'
import gzip
import sys
from pathlib import Path

feed = Path(sys.argv[1])
data = feed.joinpath("advisory.json").read_bytes()
feed.joinpath("nvdcve-modified.json.gz").write_bytes(gzip.compress(data, mtime=0))
feed.joinpath("cache.properties").write_text(
    "lastModifiedDate=2026-10-09T00:00:00Z\nlastModifiedDate.modified=2026-10-09T00:00:00Z\n",
    encoding="utf-8",
)
PY
}

prepare_vulnerability_scan() {
    vulnerability_plugins="$(cat <<'XML'
    <build><plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-install-plugin</artifactId>
            <dependencies>
                <dependency>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j-core</artifactId>
                    <version>2.14.1</version>
                </dependency>
            </dependencies>
        </plugin>
        <plugin>
            <groupId>org.owasp</groupId>
            <artifactId>dependency-check-maven</artifactId>
            <configuration>
                <dataDirectory>${project.build.directory}/advisory-database</dataDirectory>
                <nvdDatafeedUrl>file://${project.basedir}/feed/nvdcve-{0}.json.gz</nvdDatafeedUrl>
                <nvdValidForHours>24</nvdValidForHours>
                <hostedSuppressionsEnabled>false</hostedSuppressionsEnabled>
                <knownExploitedEnabled>false</knownExploitedEnabled>
                <retireJsAnalyzerEnabled>false</retireJsAnalyzerEnabled>
                <centralAnalyzerEnabled>false</centralAnalyzerEnabled>
                <formats><format>JSON</format></formats>
            </configuration>
        </plugin>
    </plugins></build>
XML
)"
    VULNERABILITY_PLUGINS="${vulnerability_plugins}" perl -0pi -e \
        's{</project>}{$ENV{VULNERABILITY_PLUGINS}."\n</project>"}e' "${vulnerability_consumer}/pom.xml"
}
