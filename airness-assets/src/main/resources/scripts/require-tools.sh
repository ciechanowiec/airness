#!/usr/bin/env sh
# Fails with a clear message when a tool the slow verification needs is not there, naming every one that
# is missing rather than stopping at the first.
#
# The gates behind this do not want one toolchain, they want several: the secret scan and the inspection
# scan want Docker, and the documentation lint wants a local Python, Asciidoctor, Vale and the two text
# extractors it shells out to. A machine with Docker and no Vale would otherwise get two gates in before
# failing, with a Python traceback three engines deep instead of a sentence naming what to install.
set -eu

missing=""
for tool in "$@"; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        missing="$missing $tool"
    fi
done

if [ -n "$missing" ]; then
    echo "ERROR: the slow verification needs tools this machine does not have:$missing" >&2
    echo "       Install them and retry, or run the default build, which needs none of them." >&2
    exit 1
fi

# Docker being installed is not Docker being usable, and the difference is a daemon nobody started.
for tool in "$@"; do
    if [ "$tool" = "docker" ] && ! docker info >/dev/null 2>&1; then
        echo "ERROR: Docker is installed but its daemon is not reachable." >&2
        echo "       Start Docker and retry." >&2
        exit 1
    fi
done
