#!/usr/bin/env sh
# Copies the harness this clone installed into the shared local Maven repository, which governed projects build
# against. Run it while no governed project builds: a build that starts during the copy can read a harness that
# is partly this clone's and partly the one promoted before it.
set -eu

repository="$(cd "$(dirname "$0")/.." && pwd)"
. "${repository}/airness-it/local-repository.sh"
shared_repository="${HOME}/.m2/repository"
if [ "${local_repository}" = "${shared_repository}" ]; then
    printf 'This clone installs into the shared repository %s itself, so there is nothing to promote\n' \
        "${shared_repository}" >&2
    exit 1
fi
set -- "${local_repository}"/eu/ciechanowiec/airness*/
if [ ! -d "$1" ]; then
    printf 'This clone has installed no harness into %s; run mvn clean install in %s first\n' \
        "${local_repository}" "${repository}" >&2
    exit 1
fi
# One promotion at a time, so two clones promoting at once leave the harness of one of them rather than a mix of
# both. The lock is a directory, because creating one is atomic, and it names the process that holds it. A
# promotion waits for one that runs; a lock whose process is gone is left by a promotion that was killed, and it is
# reported rather than taken over, because two promotions taking it over at once could both proceed.
lock="${shared_repository}/eu/ciechanowiec/.airness-promotion.lock"
mkdir -p "$(dirname "${lock}")"
announced=false
until mkdir "${lock}" 2>/dev/null; do
    holder="$(cat "${lock}/pid" 2>/dev/null || true)"
    if [ -n "${holder}" ] && ! kill -0 "${holder}" 2>/dev/null; then
        printf 'A killed promotion, process %s, left the lock %s; remove it once no promotion runs\n' \
            "${holder}" "${lock}" >&2
        exit 1
    fi
    if [ "${announced}" = false ]; then
        printf 'Waiting for the promotion that process %s runs\n' "${holder:-unknown}"
        announced=true
    fi
    sleep 1
done
trap 'rm -rf "${lock}"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
printf '%s\n' "$$" > "${lock}/pid"
printf 'Promoting the harness of %s; no governed project may build until this ends\n' "${repository}"
for artifact in "$@"; do
    target="${shared_repository}/eu/ciechanowiec/$(basename "${artifact}")"
    mkdir -p "${target}"
    cp -pR "${artifact}." "${target}/"
    printf 'Promoted %s\n' "${target}"
done
