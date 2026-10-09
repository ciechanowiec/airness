#!/usr/bin/env python3
"""Assert inventory and advisory behavior from a real Dependency-Check report."""

import json
import sys
from pathlib import Path


def verify(path):
    report = json.loads(path.read_text(encoding="utf-8"))
    dependencies = {dependency["fileName"]: dependency for dependency in report["dependencies"]}
    assert any(name.startswith("maven-install-plugin-") for name in dependencies)
    assert "log4j-api-2.14.1.jar" in dependencies
    core = dependencies["log4j-core-2.14.1.jar"]
    assert any(vulnerability["name"] == "CVE-2021-44228" for vulnerability in core["vulnerabilities"])


if __name__ == "__main__":
    verify(Path(sys.argv[1]))
