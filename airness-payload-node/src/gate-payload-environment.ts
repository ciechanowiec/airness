// The validated environment module's reads reach the places they have to: documented in the member's or the
// repository's example file, and declared as build arguments in every Dockerfile that builds the member.
import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { memberDockerfiles } from '@airness/node/dockerfiles'
import { failed, type GateResult, notApplicable, passed } from '@airness/node/gate-result'
import {
  type DockerfileSource,
  ENVIRONMENT_EXAMPLE_FILES,
  findEnvironmentViolations,
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

const exampleOf = (directory: string): string | undefined =>
  ENVIRONMENT_EXAMPLE_FILES.map((file: string): string | undefined => readIfPresent(path.join(directory, file))).find(
    (content: string | undefined): content is string => content !== undefined,
  )

// The example files the member's variables may be documented in: the member's own, beside the `.env` Next.js and
// Payload load from the member's directory, which is where each application of a repository with several keeps
// its own, and the repository's, which a repository that is the member, or keeps one example for all, holds. A
// name either one documents counts.
const examplesOf = (member: string, repository: string): string | undefined => {
  const examples: readonly string[] = [...new Set([path.resolve(member), path.resolve(repository)])]
    .map((directory: string): string | undefined => exampleOf(directory))
    .filter((content: string | undefined): content is string => content !== undefined)
  return examples.length === 0 ? undefined : examples.join('\n')
}

// The Dockerfiles that build the member, the ones the node layer's image gate builds and boots.
const dockerfilesOf = (target: PayloadMember): readonly DockerfileSource[] =>
  memberDockerfiles(target.member).map(
    (file: string): DockerfileSource => ({
      file,
      content: readFileSync(path.join(target.member.root, file), 'utf8'),
    }),
  )

/**
 * Hold the validated environment module's reads to the example files and the member's Dockerfiles.
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
    example: examplesOf(target.member.root, repositoryRootOf(target.member.root)),
    dockerfiles: dockerfilesOf(target),
    serverUrlVariables: target.member.settings.serverUrlVariables,
  })
  return findings.length > 0
    ? failed(`${String(findings.length)} environment variable(s) do not reach where they have to`, findings)
    : passed(`every variable ${VALIDATED_ENVIRONMENT_MODULE} reads is documented and reaches the image`)
}
