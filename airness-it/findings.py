#!/usr/bin/env python3
"""Normalizes the analyzer reports one Maven execution wrote into one findings table.

Each analyzer already writes a machine-readable report beneath a build directory. This reads the
reports that are strictly newer than the execution's start marker, the same comparison find -newer
makes, so a report a previous execution left behind is never attributed to this one. It writes one
line per tool that wrote a report, and one line per finding:

    #report <TAB> tool
    tool <TAB> path:line <TAB> rule <TAB> message

The report lines let a negative assertion tell "the tool found nothing here" from "the tool wrote no
report". Paths are relative to the fixture root and use forward slashes: Checkstyle, PMD and CPD name
absolute files, and Qodana names repository-relative ones. A finding without a line carries the path
alone. A report this cannot read fails the collection, because a table missing a tool would read as that
tool having found nothing.
"""

import json
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPORTS = {
    'checkstyle-result.xml': 'checkstyle',
    'pmd.xml': 'pmd',
    'cpd.xml': 'cpd',
    'qodana.sarif.json': 'qodana',
}


def local(tag):
    return tag.rsplit('}', 1)[-1]


def flat(text):
    return ' '.join((text or '').split())


def relative(root, name):
    path = Path(name)
    if path.is_absolute():
        try:
            path = path.resolve().relative_to(root)
        except ValueError:
            return path.as_posix()
    return path.as_posix()


def located(path, line):
    return f'{path}:{line}' if line else path


def checkstyle(root, report):
    for file in ET.parse(report).getroot():
        if local(file.tag) != 'file':
            continue
        path = relative(root, file.get('name', ''))
        for error in file:
            if local(error.tag) != 'error':
                continue
            source = error.get('source', '')
            rule = source.split('#', 1)[1] if '#' in source else source.rsplit('.', 1)[-1].removesuffix('Check')
            yield located(path, error.get('line')), rule, flat(error.get('message'))


def pmd(root, report):
    for file in ET.parse(report).getroot():
        if local(file.tag) != 'file':
            continue
        path = relative(root, file.get('name', ''))
        for violation in file:
            if local(violation.tag) == 'violation':
                yield located(path, violation.get('beginline')), violation.get('rule', ''), flat(violation.text)


def cpd(root, report):
    for duplication in ET.parse(report).getroot():
        if local(duplication.tag) != 'duplication':
            continue
        files = [element for element in duplication if local(element.tag) == 'file']
        paths = [relative(root, element.get('path', '')) for element in files]
        for element, path in zip(files, paths):
            others = ', '.join(other for other in paths if other != path) or path
            message = f'{duplication.get("lines")} duplicated lines, also in {others}'
            yield located(path, element.get('line')), 'duplication', message


def qodana(root, report):
    document = json.loads(Path(report).read_text(encoding='utf-8'))
    for run in document.get('runs', []):
        for result in run.get('results', []):
            message = flat(result.get('message', {}).get('text'))
            locations = result.get('locations') or [{}]
            for location in locations:
                physical = location.get('physicalLocation', {})
                path = physical.get('artifactLocation', {}).get('uri', '')
                line = physical.get('region', {}).get('startLine')
                yield located(path, line), result.get('ruleId', ''), message


READERS = {
    'checkstyle': checkstyle,
    'pmd': pmd,
    'cpd': cpd,
    'qodana': qodana,
}


def reports(root, started):
    for directory, children, files in os.walk(root):
        children[:] = [child for child in children if child not in ('.git', 'node_modules')]
        if 'target' not in Path(directory).relative_to(root).parts:
            continue
        for name in files:
            tool = REPORTS.get(name)
            report = Path(directory) / name
            if tool and report.stat().st_mtime_ns > started:
                yield tool, report


def main():
    root = Path(sys.argv[1]).resolve()
    started = Path(sys.argv[2]).stat().st_mtime_ns
    destination = Path(sys.argv[3])
    rows = []
    for tool in sorted({tool for tool, _ in reports(root, started)}):
        rows.append('\t'.join(('#report', tool)))
    for tool, report in sorted(reports(root, started)):
        try:
            findings = list(READERS[tool](root, report))
        except (ET.ParseError, json.JSONDecodeError, OSError) as failure:
            print(f'{report}: {failure}', file=sys.stderr)
            return 1
        rows.extend('\t'.join((tool, location, rule, message)) for location, rule, message in findings)
    destination.write_text(''.join(row + '\n' for row in rows), encoding='utf-8')
    return 0


if __name__ == '__main__':
    sys.exit(main())
