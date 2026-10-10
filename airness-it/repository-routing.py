#!/usr/bin/env python3
"""Exercise Maven repository routing against a real local HTTP repository."""

import functools
import http.server
import json
from collections.abc import Callable
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
from typing import Any
import zipfile

MAVEN_TIMEOUT_SECONDS: int = 120
THREAD_TIMEOUT_SECONDS: int = 5


def maven(consumer: Path, log: Path, *arguments: str) -> None:
    with log.open("w", encoding="utf-8") as output:
        result: subprocess.CompletedProcess[bytes] = subprocess.run(
            ["mvn", "--batch-mode", "--no-transfer-progress", *arguments],
            cwd=consumer, stdout=output, stderr=subprocess.STDOUT, timeout=MAVEN_TIMEOUT_SECONDS, check=False,
        )
    assert result.returncode == 0, log.read_text(encoding="utf-8")


def exercise(consumer: Path, scratch: Path) -> None:
    requests: Path = scratch / "requests.log"
    requests.touch()

    class Repository(http.server.SimpleHTTPRequestHandler):
        def log_request(self, code: int | str = "-", size: int | str = "-") -> None:
            with requests.open("a", encoding="utf-8") as output:
                output.write(f"{self.path}\t{code}\t{size}\n")

    handler: Callable[..., Repository] = functools.partial(Repository, directory=str(scratch))
    with http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler) as server:
        thread: threading.Thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            resolve(consumer, scratch, server.server_port, requests)
        finally:
            server.shutdown()
            thread.join(timeout=THREAD_TIMEOUT_SECONDS)


def resolve(consumer: Path, scratch: Path, port: int, requests: Path) -> None:
    artifact: Path = scratch / "injected.jar"
    with zipfile.ZipFile(artifact, "w") as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n\n")
    descriptor: Path = scratch / "injected.pom"
    descriptor.write_text(f"""<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId><artifactId>repository-probe</artifactId><version>9.4.2</version>
    <repositories><repository>
        <id>injected-repository</id><url>http://127.0.0.1:{port}/</url>
        <snapshots><enabled>true</enabled></snapshots>
    </repository><repository>
        <id>airness-central</id><url>http://127.0.0.1:{port}/</url>
        <snapshots><enabled>true</enabled></snapshots>
    </repository></repositories>
    <pluginRepositories><pluginRepository>
        <id>injected-plugins</id><url>http://127.0.0.1:{port}/</url>
    </pluginRepository></pluginRepositories>
    <dependencies><dependency>
        <groupId>commons-codec</groupId><artifactId>commons-codec</artifactId>
        <version>[1.19.0,1.20.0)</version>
    </dependency></dependencies>
</project>
""", encoding="utf-8")
    maven(consumer, scratch / "install.log", "install:install-file",
          f"-Dfile={artifact}", f"-DpomFile={descriptor}")
    pom: Path = consumer / "pom.xml"
    pom.write_text(pom.read_text(encoding="utf-8").replace("</project>", """
    <dependencies><dependency>
        <groupId>com.example</groupId><artifactId>repository-probe</artifactId>
        <version>9.4.2</version><scope>compile</scope>
    </dependency></dependencies>
</project>
"""), encoding="utf-8")
    maven(consumer, scratch / "routed.log", "-U", "cyclonedx:makeBom")
    assert requests.stat().st_size == 0, requests.read_text(encoding="utf-8")
    bom: dict[str, Any] = json.loads((consumer / "target/bom.json").read_text(encoding="utf-8"))
    names: set[str] = {component["name"] for component in bom["components"]}
    assert {"repository-probe", "commons-codec", "airness-annotations"} <= names
    output: str = (scratch / "routed.log").read_text(encoding="utf-8")
    assert "Could not transfer metadata" not in output
    assert "Unknown keyword" not in output
    verify_control(consumer, scratch, requests)


def verify_control(consumer: Path, scratch: Path, requests: Path) -> None:
    unrestricted: Path = scratch / "unrestricted.xml"
    unrestricted.write_text("<settings/>\n", encoding="utf-8")
    maven(consumer, scratch / "control.log", "-gs", str(unrestricted), "-U", "cyclonedx:makeBom")
    contacted: list[str] = requests.read_text(encoding="utf-8").splitlines()
    assert contacted, "The control must contact the repository to prove this fixture exercises routing"
    assert any(line.split("\t", 1)[0].endswith("maven-metadata.xml") for line in contacted), (
        contacted, (scratch / "control.log").read_text(encoding="utf-8")
    )
    print(f"PASS: managed routing sent zero requests; the control sent {len(contacted)}")


if __name__ == "__main__":
    with tempfile.TemporaryDirectory(prefix="airness-routing-http-") as directory:
        exercise(Path(sys.argv[1]), Path(directory))
