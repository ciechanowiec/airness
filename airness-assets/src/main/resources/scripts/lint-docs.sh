#!/usr/bin/env sh
# Lints each named AsciiDoc document with the three-engine linter in .docs/lib/adoc_lint.py: Vale over
# the shipped style library, a structural pass, and an Asciidoctor render. A non-zero exit means at
# least one of them reported something.
#
# This runs on the host rather than in a container, and it is the only gate that does. The linter shells
# out to pdftotext and tesseract for its source-verification passes, so an image would have to carry the
# whole document toolchain, and getting that wrong degrades to fewer engines running rather than to a
# failure. Fewer engines running is indistinguishable from a clean document, which is the one outcome a
# prose gate must not have.
#
# The toolchain is required here rather than by a separate step, so that the requirement sits next to
# the only thing that needs it and stands down with it. A project that has declared it has no prose is
# not asked to install a document toolchain.
set -eu

here="$(dirname "$0")"
root="$(git rev-parse --show-toplevel)"
documents="${1:-}"

if [ -z "$documents" ]; then
    echo "ERROR: no documents given. Set airness.docs, or the literal NONE to declare there are none." >&2
    exit 1
fi

if [ "$documents" = "NONE" ]; then
    echo "airness.docs is NONE, so this project declares it has no linted prose."
    exit 0
fi

sh "$here/require-tools.sh" python3 asciidoctor vale pdftotext tesseract

cd "$root"

# Vale reports a clean document when its styles resolve to nothing, so an absent or relocated style
# library reads exactly like prose with no findings. Asserting the library is there and not empty is
# what tells those two apart, and it is checked before the first engine runs rather than after.
styles="$root/.vale/styles"
if [ ! -d "$styles" ] || [ -z "$(ls -A "$styles" 2>/dev/null)" ]; then
    echo "ERROR: $styles is missing or empty, so Vale would report every document clean." >&2
    echo "       Run 'mvn airness:assets-sync' to restore the style library." >&2
    exit 1
fi

status=0
# A comma-separated list, walked without arrays because this is POSIX sh.
IFS=','
for document in $documents; do
    unset IFS
    if [ ! -f "$document" ]; then
        echo "ERROR: $document is named by airness.docs but is not in the repository." >&2
        status=1
        continue
    fi
    echo "Linting $document"
    python3 .docs/lib/adoc_lint.py "$document" || status=1
    IFS=','
done
unset IFS

exit "$status"
