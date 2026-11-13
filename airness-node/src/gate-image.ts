// The deployable image of a node project: every Dockerfile of the member builds, with the node project's directory as
// it stands after the build as the context, so a member of a pnpm workspace reaches the lockfile it installs by, with
// no build argument and no service, and, for a member that serves an application, the image starts against services
// as a fresh deployment finds them and answers every probed path below 500.
//
// Linting a Dockerfile proves nothing about the image it makes. A `COPY` of a directory that does not exist, a
// build that reads a secret it is never given, a configuration that leaves an empty production database without a
// schema, an origin set only in the stage that builds: each passes the linter and fails the first deployment. So
// the image is built and run as a deployment runs it. The services come from the member's own test wrapper, asked
// through a variable of the harness's own to start them empty and to describe them, so the container reaches them
// on the run's network; a member without a wrapper gets a run with no services of the harness's own, which still
// names and removes the container. Every image is removed afterwards, also when the gate is stopped.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { doesInstallPackages, memberDockerfiles } from './dockerfiles.js'
import type { RunResult } from './exec.js'
import { isBrowserApp } from './facets.js'
import { failed, type GateContext, type GateResult, notApplicable, passed } from './gate-result.js'
import { isHarnessPackage } from './harness-packages.js'
import { type BootOutcome, type ImageMachine, SYSTEM_IMAGE_MACHINE } from './image-machine.js'
import { asRecord, declaredDependencies, parseJsonc } from './json-shapes.js'
import type { Member } from './project.js'

const BUILD_TAIL_LINES: number = 30
const TCP: string = '/tcp'

// The end of what a command printed, which names the step that failed and why.
const tailOf = (output: string): readonly string[] =>
  output
    .split('\n')
    .map((line: string): string => line.trimEnd())
    .filter((line: string): boolean => line.length > 0)
    .slice(-BUILD_TAIL_LINES)
    .map((line: string): string => `| ${line}`)

/**
 * The TCP ports an image exposes, read from what `docker image inspect` printed for them.
 * @param printed the JSON of the image's `ExposedPorts`, `null` when it exposes none.
 * @returns the port numbers, in the order the image lists them.
 */
const exposedTcpPorts = (printed: string): readonly number[] => {
  const exposed: Record<string, unknown> = asRecord(parseJsonc(printed.trim()).value)
  return Object.keys(exposed)
    .filter((port: string): boolean => port.endsWith(TCP))
    .map((port: string): number => Number(port.slice(0, -TCP.length)))
}

/**
 * The paths a boot asks: the declared origin's own path, then each a stack layer adds.
 * @param member the member.
 * @returns the paths, each once.
 */
const probePaths = (member: Member): readonly string[] => [
  ...new Set<string>([new URL(member.settings.serverUrl).pathname, ...member.settings.imageProbes]),
]

/** The one port an image serves on, or why it cannot be booted on one. */
type ContainerPort = { readonly port: number } | { readonly problem: string }

const containerPortOf = (machine: ImageMachine, dockerfile: string, tag: string): ContainerPort => {
  const inspected: RunResult = machine.exposedPorts(tag)
  if (inspected.code !== 0) {
    return {
      problem: `${dockerfile}: the built image could not be inspected: ${inspected.output.trim()}`,
    }
  }
  const ports: readonly number[] = exposedTcpPorts(inspected.stdout)
  const [port] = ports
  if (port !== undefined && ports.length === 1) {
    return { port }
  }
  return {
    problem:
      ports.length === 0
        ? `${dockerfile}: the image of an application that serves exposes no TCP port; add EXPOSE with the port ` +
          'it listens on, which the image gate publishes'
        : `${dockerfile}: the image exposes the TCP ports ${ports.join(', ')}, so the port the application serves ` +
          'on is not stated; expose that one alone',
  }
}

const bootFindings = async (
  machine: ImageMachine,
  member: Member,
  dockerfile: string,
  tag: string,
): Promise<readonly string[]> => {
  const port: ContainerPort = containerPortOf(machine, dockerfile, tag)
  if ('problem' in port) {
    return [port.problem]
  }
  const outcome: BootOutcome = await machine.boot(member, {
    image: tag,
    containerPort: port.port,
    serverUrl: member.settings.serverUrl,
    serverUrlVariables: member.settings.serverUrlVariables,
    probes: probePaths(member),
  })
  if (outcome.verdict === undefined) {
    return [
      `${dockerfile}: the image could not be booted, because the test wrapper did not complete the boot ` +
        `(exit code ${String(outcome.result.code)}):`,
      ...tailOf(outcome.result.output),
    ]
  }
  return outcome.verdict.ok
    ? []
    : [
        `${dockerfile}: the image does not boot against empty services:`,
        ...outcome.verdict.findings.map((finding: string): string => `  ${finding}`),
      ]
}

const imageFindings = async (machine: ImageMachine, member: Member, dockerfile: string): Promise<readonly string[]> => {
  const tag: string = machine.tag()
  const built: RunResult = await machine.build(member, dockerfile, tag)
  // Docker tags an image only once its build succeeded, so a failed build leaves nothing to remove.
  if (built.code !== 0) {
    return [
      `${dockerfile}: the image does not build from the node project's directory with no build argument and no ` +
        `service (docker build exited ${String(built.code)}):`,
      ...tailOf(built.output),
    ]
  }
  try {
    return isBrowserApp(member) ? await bootFindings(machine, member, dockerfile, tag) : []
  } finally {
    machine.remove(tag)
  }
}

const judged = async (
  machine: ImageMachine,
  member: Member,
  dockerfiles: readonly string[],
  isStopped: () => boolean,
): Promise<readonly string[]> => {
  const [dockerfile, ...rest] = dockerfiles
  if (dockerfile === undefined) {
    return []
  }
  if (isStopped()) {
    return [`${dockerfile}: not judged, because the gate was stopped`]
  }
  const findings: readonly string[] = await imageFindings(machine, member, dockerfile)
  return [...findings, ...(await judged(machine, member, rest, isStopped))]
}

const EMPTY_CONTEXT: GateContext = { evidenceDirectory: undefined }

const SNAPSHOT_SUFFIX: string = '-SNAPSHOT'

// A SNAPSHOT harness package is never published: the build installs it from a registry on this machine's loopback,
// which a Docker build cannot reach, and the public registry does not hold it. An image whose build installs the
// member's packages therefore cannot be built until the harness is released. An image that installs nothing, one
// that copies the sources it runs or an artifact the build made, reaches no registry, so it is built and booted
// all the same. The gate decides this on its own, because the version the member installs is the harness's, not a
// setting the member could use to switch the gate off.
const snapshotHarnessPackages = (member: Member): readonly string[] =>
  Object.entries(declaredDependencies(member.packageJson))
    .filter(
      ([name, version]: readonly [string, string]): boolean =>
        isHarnessPackage(name) && version.endsWith(SNAPSHOT_SUFFIX),
    )
    .map(([name, version]: readonly [string, string]): string => `${name}@${version}`)

// The Dockerfiles that wait for a released harness: those whose build installs packages, while the member installs a
// harness that is not yet published.
const waitingDockerfiles = (
  member: Member,
  dockerfiles: readonly string[],
  snapshots: readonly string[],
): readonly string[] =>
  snapshots.length === 0
    ? []
    : dockerfiles.filter((dockerfile: string): boolean =>
        doesInstallPackages(readFileSync(path.join(member.root, dockerfile), 'utf8')),
      )

/**
 * Build every Dockerfile of a member and boot the image of one that serves an application.
 * @param member the member.
 * @param _context the gate's context, which this gate writes no evidence to.
 * @param machine the machine, replaced only by tests.
 * @returns not applicable for a member without a Dockerfile, or whose every Dockerfile installs a harness not yet
 * released; otherwise the verdict over every image it can build.
 */
export const image = async (
  member: Member,
  _context: GateContext = EMPTY_CONTEXT,
  machine: ImageMachine = SYSTEM_IMAGE_MACHINE,
): Promise<GateResult> => {
  const dockerfiles: readonly string[] = memberDockerfiles(member)
  if (dockerfiles.length === 0) {
    return notApplicable(`${member.path} holds no Dockerfile`)
  }
  const snapshots: readonly string[] = snapshotHarnessPackages(member)
  const waiting: readonly string[] = waitingDockerfiles(member, dockerfiles, snapshots)
  const unpublished: string = `${member.path} installs the unpublished harness ${snapshots.join(', ')}, which a Docker build cannot reach`
  const ready: readonly string[] = dockerfiles.filter((dockerfile: string): boolean => !waiting.includes(dockerfile))
  if (ready.length === 0) {
    return notApplicable(
      `${unpublished}, and every Dockerfile of it installs packages; its images are built and booted once it ` +
        'depends on a released harness',
    )
  }
  const findings: readonly string[] = await machine.holdStops(
    async (isStopped: () => boolean): Promise<readonly string[]> => await judged(machine, member, ready, isStopped),
  )
  const what: string = isBrowserApp(member) ? 'builds an image that boots against empty services' : 'builds'
  const left: string =
    waiting.length === 0
      ? ''
      : `; ${waiting.join(', ')} installs packages, and is built once the harness is released: ${unpublished}`
  return findings.length === 0
    ? passed(`every Dockerfile (${ready.join(', ')}) ${what}${left}`)
    : failed(`an image of ${member.path} does not build or does not boot`, findings)
}
