#!/usr/bin/env python3
"""Check the observable contract of the installed SBOM generator against the consumer fixture."""

import json
import sys
from pathlib import Path


def verify(path, artifact, repository):
    content = path.read_text(encoding="utf-8")
    bom = json.loads(content)
    assert bom["bomFormat"] == "CycloneDX"
    assert bom["specVersion"] == "1.6"
    assert "serialNumber" not in bom
    assert "timestamp" not in bom["metadata"]
    root = bom["metadata"]["component"]
    assert root["name"] == artifact
    assert root["version"] == "9.4.2"
    components = {component["name"]: component for component in bom["components"]}
    assert {"commons-text", "commons-lang3", "commons-codec", "jspecify"} <= components.keys()
    assert not any(name.startswith("junit") for name in components)
    assert components["commons-text"]["scope"] == "optional"
    dependencies = {entry["ref"]: set(entry.get("dependsOn", [])) for entry in bom["dependencies"]}
    assert components["commons-text"]["bom-ref"] in dependencies[root["bom-ref"]]
    assert components["commons-codec"]["bom-ref"] in dependencies[root["bom-ref"]]
    assert components["commons-lang3"]["bom-ref"] in dependencies[components["commons-text"]["bom-ref"]]
    assert repository not in content
    assert str(Path.home()) not in content


if __name__ == "__main__":
    verify(Path(sys.argv[1]), sys.argv[2], sys.argv[3])
