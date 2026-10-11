#!/usr/bin/env python3
"""Read the artifacts and combined coverage of the optional-plugin consumer."""

import io
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile


def verify(consumer: Path) -> None:
    report = ET.parse(consumer / "target/site/jacoco/jacoco.xml")
    decision = report.find(".//class[@name='com/example/Decision']/counter[@type='BRANCH']")
    assert decision is not None
    assert decision.attrib == {"type": "BRANCH", "missed": "0", "covered": "2"}, decision.attrib
    summary = ET.parse(consumer / "target/failsafe-reports/failsafe-summary.xml")
    assert summary.findtext("completed") == "1"
    assert summary.findtext("failures") == "0"
    assert summary.findtext("errors") == "0"
    with zipfile.ZipFile(consumer / "target/consumer-9.4.2-bundle.zip") as distribution:
        with zipfile.ZipFile(io.BytesIO(distribution.read("lib/consumer-9.4.2.jar"))) as archive:
            assert archive.read("plugin-resource.txt") == b"Managed Build Helper resource.\n"


if __name__ == "__main__":
    verify(Path(sys.argv[1]))
