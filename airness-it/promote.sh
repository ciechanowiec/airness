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
printf 'Promoting the harness of %s; no governed project may build until this ends\n' "${repository}"
for artifact in "$@"; do
    target="${shared_repository}/eu/ciechanowiec/$(basename "${artifact}")"
    mkdir -p "${target}"
    cp -pR "${artifact}." "${target}/"
    printf 'Promoted %s\n' "${target}"
done
