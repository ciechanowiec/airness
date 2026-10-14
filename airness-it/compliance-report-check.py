"""Judge installed report output against the fixture's own commit and resolved tool override."""

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

PAIR_SIZE = 2
SHORT_COMMIT = 12

root = Path(sys.argv[1])
directory = root / "target/compliance"
files = sorted(directory.iterdir())
if len(files) != PAIR_SIZE or {file.suffix for file in files} != {".html", ".pdf"}:
    raise SystemExit("Expected exactly one HTML/PDF report pair")
html_file = next(file for file in files if file.suffix == ".html")
pdf_file = next(file for file in files if file.suffix == ".pdf")
if html_file.stem != pdf_file.stem or not pdf_file.read_bytes().startswith(b"%PDF-"):
    raise SystemExit("The report pair is incomplete or the PDF is not a PDF")
html = html_file.read_text(encoding="utf-8")
match = re.search(r'<script type="application/json" id="report-data">(.*?)</script>', html, re.S)
if match is None:
    raise SystemExit("The offline report has no embedded downloads")
downloads = json.loads(match.group(1))
sbom = json.loads(downloads["sbom"])
if sys.argv[2] == "patched":
    names = {component["name"] for component in sbom["components"]}
    if not {"consumer", "report-first", "report-second"}.issubset(names):
        raise SystemExit("The parallel report omitted a reactor module")
    if any((root / module / "target/compliance").exists() for module in ("report-first", "report-second")):
        raise SystemExit("A reactor module published an extra report pair")
    tools = [component for component in sbom["components"] if component.get("name") == "log4j-core"]
    if not any(component["version"] == "2.26.1" and component.get("licenses") for component in tools):
        raise SystemExit("The evaluated plugin override or its declared license is missing")
    if any(component["version"] == "2.14.1" for component in tools):
        raise SystemExit("The report retained a replaced tool version")
    if '"build"' not in downloads["csv"] or "log4j-core" not in downloads["csv"]:
        raise SystemExit("The CSV lost the tool's Maven use")
commit = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"], check=True, capture_output=True, text=True).stdout.strip()
if commit not in html or commit[:SHORT_COMMIT] not in html_file.name:
    raise SystemExit("The report does not identify its source commit")
if hashlib.sha256(downloads["sbom"].encode("utf-8")).hexdigest() not in html:
    raise SystemExit("The embedded SBOM differs from the digest printed beside it")
if "Draft report" not in html or "Report-only" not in html:
    raise SystemExit("The report-only invocation is presented as release evidence")
if re.search(r'<(?:script|link)[^>]+(?:src|href)="https?://', html):
    raise SystemExit("The report loads an external script or stylesheet")
print("HTML/PDF pair, inventory, licensing, source identity, draft status and embedded hashes passed")
