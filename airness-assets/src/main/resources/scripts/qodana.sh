#!/usr/bin/env sh
# Runs the IntelliJ inspection engine over the project in a pinned container, against the profile the
# harness ships. Any finding fails the run, because qodana.yaml sets the threshold to zero.
#
# The repository root is asked of git rather than taken from the module being built, for the same reason
# the other container-backed gate does it: under a multi-module build the module directory is not the
# project the engine is meant to read.
#
# Where a network inspects TLS, the container has to trust the same roots the host already does, or
# every fetch inside it fails with a certificate error that says nothing about the code. The host's
# trusted roots are exported and appended to the container's bundle. On a machine with nothing extra to
# trust the export is empty and the append is a no-op, so one script works in both places.
set -eu

image="${1:?usage: $0 <inspection-image>}"
root="$(git rev-parse --show-toplevel)"
profile="$(cd "$(dirname "$0")/../qodana" && pwd)/profile.xml"
results="$root/target/qodana"
roots="$root/target/qodana-trusted-roots.pem"

sh "$(dirname "$0")/require-tools.sh" docker

rm -rf "$results"
mkdir -p "$results"

: > "$roots"
if command -v security >/dev/null 2>&1; then
    security find-certificate -a -p /System/Library/Keychains/SystemRootCertificates.keychain >> "$roots" 2>/dev/null || true
    security find-certificate -a -p /Library/Keychains/System.keychain >> "$roots" 2>/dev/null || true
fi

# The profile is mounted and named on the command line rather than pointed at from qodana.yaml. A path
# in that file has to be written relative to the repository root, and where the build directory sits
# relative to that root is not the same in a single-module project, a multi-module one, and a project
# nested inside another repository. A mount has no such relationship to reason about.
exec docker run --rm \
    -v "$root":/data/project \
    -v "$results":/data/results \
    -v "$profile":/opt/inspection-profile.xml:ro \
    -v "$roots":/opt/hostca.pem:ro \
    --entrypoint /bin/sh \
    "$image" \
    -c 'bundle="$(readlink -f /etc/ssl/certs/ca-certificates.crt)"; [ -s /opt/hostca.pem ] && cat /opt/hostca.pem >> "$bundle"; exec /opt/idea/bin/qodana scan --disable-update-checks --profile-path /opt/inspection-profile.xml'
