#!/usr/bin/env sh
# Runs a diagnostic script with exactly the Node this harness installed for this platform.
set -eu
repository="$(cd "$(dirname "$0")/.." && pwd)"
. "${repository}/airness-it/local-repository.sh"
pins="${repository}/airness-maven-plugin/src/main/resources/eu/ciechanowiec/airness/node/toolchain.properties"
version="$(sed -n 's/^node[.]version=//p' "${pins}")"
case "$(uname -s)" in
    Darwin) platform=darwin ;;
    Linux) platform=linux ;;
    *) echo 'The Node diagnostic launcher supports Linux and macOS' >&2; exit 1 ;;
esac
case "$(uname -m)" in
    arm64|aarch64) architecture=arm64 ;;
    x86_64) architecture=x64 ;;
    *) echo 'The Node diagnostic launcher supports arm64 and x64' >&2; exit 1 ;;
esac
runtime="${local_repository}/.airness/toolchain/node-${version}-${platform}-${architecture}/bin/node"
if [ ! -x "${runtime}" ]; then
    echo "${runtime} is missing; build the harness to install its toolchain first" >&2
    exit 1
fi
exec "${runtime}" "$@"
