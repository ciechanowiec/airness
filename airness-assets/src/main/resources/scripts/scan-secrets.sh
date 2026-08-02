#!/usr/bin/env sh
# Scans the whole commit history for credentials, in a pinned container.
#
# It reads the history rather than the working tree, because a credential that reached a commit is
# published the moment the branch is, and deleting it in a later commit removes it from the tree and
# from nothing else.
#
# The repository root is asked of git rather than taken from the module being built. Under a
# multi-module build the module directory is not a repository at all, and the scanner would fail there
# for a reason that has nothing to do with what it found. The mount is read-only, since a scan has no
# reason to write.
set -eu

image="${1:?usage: $0 <scanner-image>}"
root="$(git rev-parse --show-toplevel)"
config="$root/.gitleaks.toml"

if [ ! -f "$config" ]; then
    echo "ERROR: $config is missing. Run 'mvn airness:assets-sync' to write it." >&2
    exit 1
fi

sh "$(dirname "$0")/require-tools.sh" docker

exec docker run --rm -v "$root":/repo:ro "$image" \
    git /repo --no-banner --redact --config /repo/.gitleaks.toml
