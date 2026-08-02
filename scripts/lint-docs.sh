#!/usr/bin/env sh
# Airness's own documentation lint. This script is not packaged or inherited by consumers.
set -eu

missing=''
for tool in python3 asciidoctor vale pdftotext tesseract; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        missing="$missing $tool"
    fi
done
if [ -n "$missing" ]; then
    echo "ERROR: Airness documentation tools are missing:$missing" >&2
    exit 1
fi
if [ "$#" -eq 0 ]; then
    echo 'ERROR: name at least one Airness document to lint' >&2
    exit 1
fi

root="$(git rev-parse --show-toplevel)"
styles="$root/.vale/styles"
if [ ! -d "$styles" ] || [ -z "$(ls -A "$styles" 2>/dev/null)" ]; then
    echo "ERROR: $styles is missing or empty" >&2
    exit 1
fi

cd "$root"
status=0
for document in "$@"; do
    if [ ! -f "$document" ]; then
        echo "ERROR: Airness document is missing: $document" >&2
        status=1
    else
        python3 .docs/lib/adoc_lint.py "$document" || status=1
    fi
done
exit "$status"
