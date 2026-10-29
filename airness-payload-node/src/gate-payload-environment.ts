// The validated environment module's reads reach the places they have to: documented in the repository's
// example file, and declared as build arguments in every Dockerfile that builds the member.
import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { memberFiles } from '@airness/node/source-selection'
import { workingTreeFiles } from '@airness/node/working-tree'
import { failed, type GateResult, notApplicable, passed } from './outcome.js'
import {
  type DockerfileSource,
  ENVIRONMENT_EXAMPLE_FILES,
  findEnvironmentViolations,
  isDockerfile,
  VALIDATED_ENVIRONMENT_MODULE,
} from './payload-environment.js'
import type { PayloadMember } from './payload-member.js'

const readIfPresent = (file: string): string | undefined => (existsSync(file) ? readFileSync(file, 'utf8') : undefined)

// The repository the member sits in, where the example file is kept. Without git, the member is the repository.
const repositoryRootOf = (root: string): string => {
  const shown: ReturnType<typeof spawnSync> = spawnSync('git', ['rev-parse', '--show-toplevel'], {
    cwd: root,
    encoding: 'utf8',
  })
  return shown.status === 0 ? String(shown.stdout).trim() : root
}

const exampleOf = (repository: string): string | undefined =>
  ENVIRONMENT_EXAMPLE_FILES.map((file: string): string | undefined => readIfPresent(path.join(repository, file))).find(
    (content: string | undefined): content is string => content !== undefined,
  )

// The Dockerfiles inside the member's own directory, without those of a member nested in it.
const dockerfilesOf = (target: PayloadMember): readonly DockerfileSource[] =>
  memberFiles(workingTreeFiles(target.member.root), target.member.siblingPaths)
    .filter((file: string): boolean => isDockerfile(file) && existsSync(path.join(target.member.root, file)))
    .map(
      (file: string): DockerfileSource => ({
        file,
        content: readFileSync(path.join(target.member.root, file), 'utf8'),
      }),
    )

/**
 * Hold the validated environment module's reads to the example file and the member's Dockerfiles.
 * @param target the member being judged.
 * @returns the gate result.
 */
export const payloadEnvironment = (target: PayloadMember): GateResult => {
  if (!target.isPayload) {
    return notApplicable('this member is no Payload application')
  }
  const module: string | undefined = readIfPresent(path.join(target.member.root, VALIDATED_ENVIRONMENT_MODULE))
  if (module === undefined) {
    return passed(`this member has no ${VALIDATED_ENVIRONMENT_MODULE}, so it reads no validated variable`)
  }
  const findings: readonly string[] = findEnvironmentViolations({
    module,
    example: exampleOf(repositoryRootOf(target.member.root)),
    dockerfiles: dockerfilesOf(target),
  })
  return findings.length > 0
    ? failed(`${String(findings.length)} environment variable(s) do not reach where they have to`, findings)
    : passed(`every variable ${VALIDATED_ENVIRONMENT_MODULE} reads is documented and reaches the image`)
}
