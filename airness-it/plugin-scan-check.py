#!/usr/bin/env python3
"""Assert inventory and advisory behavior from a real Dependency-Check report."""

import json
import sys
from pathlib import Path


def verify(path):
    report = json.loads(path.read_text(encoding="utf-8"))
    dependencies = {dependency["fileName"]: dependency for dependency in report["dependencies"]}
    assert any(name.startswith("maven-install-plugin-") for name in dependencies)
    assert any(name.startswith("maven-shade-plugin-") for name in dependencies)
    assert "plexus-utils-3.6.2.jar" in dependencies
    assert "plexus-utils-3.6.0.jar" not in dependencies
    assert "download-maven-plugin-2.1.0.jar" in dependencies
    assert "maven-assembly-plugin-3.8.0.jar" in dependencies
    assert "build-helper-maven-plugin-3.6.2.jar" in dependencies
    assert "maven-failsafe-plugin-3.6.0.jar" in dependencies
    assert "aircompressor-2.0.3.jar" in dependencies
    assert "aircompressor-0.27.jar" not in dependencies
    assert "commons-lang3-3.21.0.jar" in dependencies
    assert "commons-lang3-3.14.0.jar" not in dependencies
    assert "plexus-utils-4.0.1.jar" not in dependencies
    assert "log4j-api-2.14.1.jar" in dependencies
    core = dependencies["log4j-core-2.14.1.jar"]
    assert any(vulnerability["name"] == "CVE-2021-44228" for vulnerability in core["vulnerabilities"])


if __name__ == "__main__":
    verify(Path(sys.argv[1]))
