// The deployable image of a node project: every Dockerfile of the member builds, with the node project's directory as
// it stands after the build as the context, so a member of a pnpm workspace reaches the lockfile it installs by, with
// no build argument and no service, and, for a member that serves an application, the image starts against services as
// a fresh deployment finds them and answers every probed path below 500.
//
// Linting a Dockerfile proves nothing about the image it makes. A `COPY` of a directory that does not exist, a build
// that reads a secret it is never given, a configuration that leaves an empty production database without a schema, an
// origin set only in the stage that builds: each passes the linter and fails the first deployment. So the image is
// built and run as a deployment runs it. The services come from the member's own test wrapper, asked through a variable
// of the harness's own to start them empty and to describe them, so the container reaches them on the run's network; a
// member without a wrapper gets a run with no services of the harness's own, which still names and removes the
// container. The image runs as an unprivileged user, and it boots as a hardened deployment runs it: with a root
// filesystem it cannot write, no capability and no way to gain a privilege, so a path the application writes is one the
// image declares as a volume. Before an image is removed, the packages it holds are listed beside the gate's result,
// which is the only moment the image can be read, for the closing step's vulnerability check. Every image is removed
// afterwards, also when the gate is stopped.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { doesInstallPackages, memberDockerfiles } from './dockerfiles.js'
import type { RunResult } from './exec.js'
import { isBrowserApp } from './facets.js'
import { failed, type GateContext, type GateResult, notApplicable, passed } from './gate-result.js'
import { isHarnessPackage } from './harness-packages.js'
import { type CatalogueOutcome, catalogue, type ImageRun, tailOf } from './image-catalogue.js'
import { type BootOutcome, type ImageMachine, SYSTEM_IMAGE_MACHINE } from './image-machine.js'
import { asRecord, declaredDependencies, parseJsonc } from './json-shapes.js'
import type { Member } from './project.js'

const TCP: string = '/tcp'
const ROOT_NAMES: ReadonlySet<string> = new Set<string>(['', 'root', '0'])

/** What the gate reads of the image's metadata: the user it runs as and the TCP ports it exposes. */
interface ImageMetadata {
  readonly user: string
  readonly ports: readonly number[]
}

/**
 * The image's metadata, read from what `docker image inspect` printed for its configuration.
 * @param printed the JSON of the image's `Config`.
 * @returns the user, empty when the image names none, and the TCP ports in the order the image lists them.
 */
const metadataOf = (printed: string): ImageMetadata => {
  const { User: user, ExposedPorts: ports }: Record<string, unknown> = asRecord(parseJsonc(printed.trim()).value)
  const exposed: Record<string, unknown> = asRecord(ports)
  return {
    user: typeof user === 'string' ? user.trim() : '',
    ports: Object.keys(exposed)
      .filter((port: string): boolean => port.endsWith(TCP))
      .map((port: string): number => Number(port.slice(0, -TCP.length))),
  }
}

/**
 * Why the image runs as root, if it does: it names no user, so it inherits root, or it names root by name or ID.
 * @param dockerfile the Dockerfile the image was built from.
 * @param user the user the image's configuration names, as `name`, `uid` or either with `:group`.
 * @returns the finding, none for an unprivileged user.
 */
const rootFindings = (dockerfile: string, user: string): readonly string[] => {
  const [name = '']: readonly string[] = user.split(':', 1)
  if (!ROOT_NAMES.has(name.trim())) {
    return []
  }
  const cause: string = user === '' ? 'neither its final stage nor its base image sets USER' : `it sets USER ${user}`
  return [
    `${dockerfile}: the image runs as root, because ${cause}; add USER with an unprivileged user ID, such as ` +
      'USER 10001:10001, to the final stage after the last step that needs root',
  ]
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

const containerPortOf = (dockerfile: string, ports: readonly number[]): ContainerPort => {
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

// What the container is allowed when it boots, named beside a boot that failed, as the likeliest cause of a write.
const HARDENED_BOOT: string =
  '  the container ran with a read-only root filesystem, no capability and no privilege escalation; only /tmp ' +
  'and the volumes the image declares are writable, so declare each other path the application writes with VOLUME'

const bootFindings = async (
  run: ImageRun,
  dockerfile: string,
  tag: string,
  ports: readonly number[],
): Promise<readonly string[]> => {
  const { machine, member } = run
  const port: ContainerPort = containerPortOf(dockerfile, ports)
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
        HARDENED_BOOT,
      ]
}

/** What judging one Dockerfile came back with: the findings, and the evidence written beside the result. */
type ImageOutcome = CatalogueOutcome

const imageOutcome = async (run: ImageRun, dockerfile: string): Promise<ImageOutcome> => {
  const { machine, member } = run
  const tag: string = machine.tag()
  const built: RunResult = await machine.build(member, dockerfile, tag)
  // Docker tags an image only once its build succeeded, so a failed build leaves nothing to remove.
  if (built.code !== 0) {
    return {
      findings: [
        `${dockerfile}: the image does not build from the node project's directory with no build argument and no ` +
          `service (docker build exited ${String(built.code)}):`,
        ...tailOf(built.output),
      ],
      evidence: [],
    }
  }
  try {
    const listed: CatalogueOutcome = catalogue(run, dockerfile, tag)
    const inspected: RunResult = machine.metadata(tag)
    if (inspected.code !== 0) {
      return {
        findings: [
          ...listed.findings,
          `${dockerfile}: the built image could not be inspected: ${inspected.output.trim()}`,
        ],
        evidence: listed.evidence,
      }
    }
    const metadata: ImageMetadata = metadataOf(inspected.stdout)
    const booted: readonly string[] = isBrowserApp(member)
      ? await bootFindings(run, dockerfile, tag, metadata.ports)
      : []
    return {
      findings: [...listed.findings, ...rootFindings(dockerfile, metadata.user), ...booted],
      evidence: listed.evidence,
    }
  } finally {
    machine.remove(tag)
  }
}

const judged = async (
  run: ImageRun,
  dockerfiles: readonly string[],
  isStopped: () => boolean,
): Promise<ImageOutcome> => {
  const [dockerfile, ...rest] = dockerfiles
  if (dockerfile === undefined) {
    return { findings: [], evidence: [] }
  }
  if (isStopped()) {
    return { findings: [`${dockerfile}: not judged, because the gate was stopped`], evidence: [] }
  }
  const outcome: ImageOutcome = await imageOutcome(run, dockerfile)
  const others: ImageOutcome = await judged(run, rest, isStopped)
  return { findings: [...outcome.findings, ...others.findings], evidence: [...outcome.evidence, ...others.evidence] }
}

// The verdict over every image built, carrying the evidence written for the images it could list.
const verdictOf = (member: Member, ready: readonly string[], left: string, outcome: ImageOutcome): GateResult => {
  const what: string = isBrowserApp(member) ? 'builds an image that boots against empty services' : 'builds'
  const verdict: GateResult =
    outcome.findings.length === 0
      ? passed(`every Dockerfile (${ready.join(', ')}) ${what}${left}`)
      : failed(`an image of ${member.path} does not build, cannot be listed or does not boot`, outcome.findings)
  return outcome.evidence.length === 0 ? verdict : { ...verdict, evidence: outcome.evidence }
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
 * Build every Dockerfile of a member, list the packages of each image, and boot the image of one that serves an
 * application.
 * @param member the member.
 * @param context the gate's context, beside whose result the packages of every built image are written.
 * @param machine the machine, replaced only by tests.
 * @returns not applicable for a member without a Dockerfile, or whose every Dockerfile installs a harness not yet
 * released; otherwise the verdict over every image it can build, listing the evidence it wrote.
 */
export const image = async (
  member: Member,
  context: GateContext = EMPTY_CONTEXT,
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
  const outcome: ImageOutcome = await machine.holdStops(
    async (isStopped: () => boolean): Promise<ImageOutcome> =>
      await judged({ machine, member, context }, ready, isStopped),
  )
  const left: string =
    waiting.length === 0
      ? ''
      : `; ${waiting.join(', ')} installs packages, and is built once the harness is released: ${unpublished}`
  return verdictOf(member, ready, left, outcome)
}
