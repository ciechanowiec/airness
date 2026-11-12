// Boots a project's image against the services its wrapper started, inside that wrapper: the image gate builds the
// image, and this runs it as a container on the run's network, hands it the services and its own origin, and asks
// it over HTTP until every probed path answers or the time a healthy start needs is over.
//
// The services are handed over in the variables the plan names, whose addresses point at ports Docker published on
// the host's loopback. A container's loopback is its own, so each such address is rewritten to the service
// container and its port, which the run's network resolves, the same way on Docker Desktop, Colima and Linux. The
// container is named under the run's prefix, so the run's removal and its guard take it with the services however
// the run ends; this removes it itself once the verdict is in.
import type { RunResult } from './exec.js'
import { asRecord, isArray, parseJsonc } from './json-shapes.js'
import {
  type PublishedPort,
  parseServicesDescription,
  SERVICES_DESCRIPTION_VARIABLE,
  type ServicesDescription,
} from './services-description.js'

/** What the image gate asks of one boot. */
export interface BootRequest {
  /** The image the gate built. */
  readonly image: string
  /** The one TCP port the image exposes. */
  readonly containerPort: number
  /** The origin the member declares, whose scheme and host the served origin keeps. */
  readonly serverUrl: string
  /** The variables the application reads its own origin from. */
  readonly serverUrlVariables: readonly string[]
  /** The paths asked, each of which must answer below 500. */
  readonly probes: readonly string[]
}

const isTextList = (raw: unknown): raw is readonly string[] =>
  isArray(raw) && raw.every((entry: unknown): boolean => typeof entry === 'string')

const isPortNumber = (raw: unknown): raw is number => typeof raw === 'number' && Number.isSafeInteger(raw)

const isOrigin = (raw: unknown): raw is string => typeof raw === 'string' && URL.canParse(raw)

/**
 * Read a request from the JSON the image gate passed.
 * @param text the JSON, undefined when none was passed.
 * @returns the request, or undefined when there is none or it is malformed.
 */
export const parseBootRequest = (text: string | undefined): BootRequest | undefined => {
  const record: Record<string, unknown> = asRecord(text === undefined ? undefined : parseJsonc(text).value)
  const { image, containerPort, serverUrl, serverUrlVariables, probes } = record
  if (!(isTextList(serverUrlVariables) && isTextList(probes))) {
    return undefined
  }
  return typeof image === 'string' && isPortNumber(containerPort) && isOrigin(serverUrl)
    ? { image, containerPort, serverUrl, serverUrlVariables, probes }
    : undefined
}

/** The answer to one request, or why none came. */
type ProbeOutcome = { readonly status: number } | { readonly problem: string }

/** The verdict of one boot. */
export interface BootVerdict {
  readonly ok: boolean
  readonly findings: readonly string[]
}

/** What a boot does to the machine, replaceable so its decisions can be measured without Docker. */
export interface BootRuntime {
  /** Run docker with these arguments, with the variables added to the environment it is started in. */
  readonly docker: (commandArguments: readonly string[], environment?: Readonly<Record<string, string>>) => RunResult
  readonly probe: (url: string) => Promise<ProbeOutcome>
  /** A loopback port nothing listens on. */
  readonly freePort: () => Promise<number>
  readonly pause: (milliseconds: number) => Promise<void>
  readonly now: () => number
  /** The environment the wrapper started the boot with. */
  readonly environment: Readonly<Record<string, string | undefined>>
}

/**
 * How long an application may take from its container starting to answering every probe. A server answers within
 * seconds of starting, and a JVM that also migrates an empty database within a minute, so a container still
 * silent after two is not starting.
 */
const BOOT_TIMEOUT_MS: number = 120_000

/** How long one request may take: an application that started answers well within it. */
const PROBE_TIMEOUT_MS: number = 10_000

const POLL_INTERVAL_MS: number = 1000
const MILLISECONDS_PER_SECOND: number = 1000
const LOG_LINES: string = '80'
const SERVER_ERROR: number = 500
// The names of the host's loopback a handed-over address may use, as one alternation.
const LOOPBACK: string = String.raw`(?:127\.0\.0\.1|localhost|\[::1\])`
const STATE_FIELDS: number = 2

/**
 * A handed-over value with each address of a published port on the host's loopback replaced by the service
 * container and port behind it.
 * @param value the value, such as a connection string.
 * @param published the published ports of the run.
 * @returns the value the container is given.
 */
const containerAddressOf = (value: string, published: readonly PublishedPort[]): string =>
  published.reduce(
    (current: string, entry: PublishedPort): string =>
      current.replaceAll(
        new RegExp(String.raw`${LOOPBACK}:${String(entry.port)}(?!\d)`, 'gu'),
        (): string => `${entry.host}:${String(entry.containerPort)}`,
      ),
    value,
  )

/**
 * The origin a boot serves on: the declared one's scheme and host, on the port chosen for the run. Its path stays
 * out, since every probe names an absolute path and an application reads an origin from its variables.
 * @param serverUrl the declared origin.
 * @param port the port.
 * @returns the origin.
 */
const servedOriginOf = (serverUrl: string, port: number): string => {
  const parsed: URL = new URL(serverUrl)
  return `${parsed.protocol}//${parsed.hostname}:${String(port)}`
}

/**
 * The variables the container starts with: the services the wrapper handed over, rewritten for the run's network,
 * and the served origin in each variable the application reads its own origin from.
 * @param description what the wrapper started and handed over.
 * @param request the boot request.
 * @param origin the served origin.
 * @param environment the wrapper's environment, which holds the handed-over values.
 * @returns the variables.
 */
const containerEnvironment = (
  description: ServicesDescription,
  request: BootRequest,
  origin: string,
  environment: Readonly<Record<string, string | undefined>>,
): Readonly<Record<string, string>> => ({
  ...Object.fromEntries(
    description.variables.map((name: string): readonly [string, string] => [
      name,
      containerAddressOf(environment[name] ?? '', description.published),
    ]),
  ),
  ...Object.fromEntries(request.serverUrlVariables.map((name: string): readonly [string, string] => [name, origin])),
})

/**
 * The `docker run` arguments that start the image. The values travel in docker's own environment and only the
 * names on its command line, so no credential the wrapper made appears in a process listing.
 * @param request the boot request.
 * @param description the run the container joins.
 * @param port the loopback port the image's port is published on.
 * @param variables the variables the container starts with.
 * @returns the arguments.
 */
const containerRunArguments = (
  request: BootRequest,
  description: ServicesDescription,
  port: number,
  variables: Readonly<Record<string, string>>,
): readonly string[] => [
  'run',
  '--detach',
  `--name=${description.prefix}-image`,
  `--network=${description.network}`,
  `--publish=127.0.0.1:${String(port)}:${String(request.containerPort)}`,
  ...Object.keys(variables).map((name: string): string => `--env=${name}`),
  request.image,
]

const isAnswered = (outcome: ProbeOutcome): boolean => 'status' in outcome && outcome.status < SERVER_ERROR

const described = (path: string, outcome: ProbeOutcome): string =>
  'status' in outcome
    ? `GET ${path} answered ${String(outcome.status)}`
    : `GET ${path} got no answer: ${outcome.problem}`

const logsOf = (runtime: BootRuntime, name: string): readonly string[] => {
  const logs: RunResult = runtime.docker(['logs', '--tail', LOG_LINES, name])
  const lines: readonly string[] = logs.output
    .split('\n')
    .map((line: string): string => line.trimEnd())
    .filter((line: string): boolean => line.length > 0)
  return lines.length === 0
    ? ['the container wrote nothing to its log']
    : [`the last ${LOG_LINES} lines the container logged:`, ...lines.map((line: string): string => `| ${line}`)]
}

// Why the container is no longer running, or undefined while it runs.
const stopped = (runtime: BootRuntime, name: string): string | undefined => {
  const state: RunResult = runtime.docker(['inspect', '--format', '{{.State.Running}} {{.State.ExitCode}}', name])
  const [running = '', exitCode = '']: readonly string[] = state.stdout.trim().split(' ', STATE_FIELDS)
  if (state.code !== 0) {
    return `the container is gone: ${state.output.trim()}`
  }
  return running === 'true' ? undefined : `the container stopped with exit code ${exitCode} before it answered`
}

/** One boot under way. */
interface Booting {
  readonly name: string
  readonly origin: string
  readonly probes: readonly string[]
  readonly deadline: number
}

const awaitAnswers = async (runtime: BootRuntime, booting: Booting): Promise<BootVerdict> => {
  const gone: string | undefined = stopped(runtime, booting.name)
  if (gone !== undefined) {
    return { ok: false, findings: [gone, ...logsOf(runtime, booting.name)] }
  }
  const outcomes: readonly ProbeOutcome[] = await Promise.all(
    booting.probes.map(async (path: string): Promise<ProbeOutcome> => await runtime.probe(`${booting.origin}${path}`)),
  )
  if (outcomes.every((outcome: ProbeOutcome): boolean => isAnswered(outcome))) {
    return { ok: true, findings: [] }
  }
  if (runtime.now() >= booting.deadline) {
    const unanswered: readonly string[] = booting.probes.flatMap((path: string, index: number): readonly string[] => {
      const outcome: ProbeOutcome = outcomes[index] ?? { problem: 'not asked' }
      return isAnswered(outcome) ? [] : [described(path, outcome)]
    })
    return {
      ok: false,
      findings: [
        `the application did not answer below ${String(SERVER_ERROR)} within ${String(BOOT_TIMEOUT_MS / MILLISECONDS_PER_SECOND)} seconds of starting:`,
        ...unanswered,
        ...logsOf(runtime, booting.name),
      ],
    }
  }
  await runtime.pause(POLL_INTERVAL_MS)
  return await awaitAnswers(runtime, booting)
}

const NO_SERVICES: string =
  'the test wrapper handed the image no description of its services; start them with withServices from ' +
  '@airness/node/services, which describes them when the image gate asks, or declare no testWrapper when the ' +
  'application needs none'

/**
 * Start the image beside the wrapper's services, wait for it to answer every probe, and remove it.
 * @param request what the image gate asks.
 * @param runtime the machine, replaced only by tests.
 * @returns the verdict.
 */
export const bootImage = async (request: BootRequest, runtime: BootRuntime): Promise<BootVerdict> => {
  const description: ServicesDescription | undefined = parseServicesDescription(
    runtime.environment[SERVICES_DESCRIPTION_VARIABLE],
  )
  if (description === undefined) {
    return { ok: false, findings: [NO_SERVICES] }
  }
  const port: number = await runtime.freePort()
  const origin: string = servedOriginOf(request.serverUrl, port)
  const variables: Readonly<Record<string, string>> = containerEnvironment(
    description,
    request,
    origin,
    runtime.environment,
  )
  const name: string = `${description.prefix}-image`
  const started: RunResult = runtime.docker(containerRunArguments(request, description, port, variables), variables)
  try {
    if (started.code !== 0) {
      return { ok: false, findings: [`the container could not be started: ${started.output.trim()}`] }
    }
    return await awaitAnswers(runtime, {
      name,
      origin,
      probes: request.probes,
      deadline: runtime.now() + BOOT_TIMEOUT_MS,
    })
  } finally {
    runtime.docker(['rm', '--force', name])
  }
}

// What a failed request says: the system's error code when the connection failed, such as ECONNREFUSED while the
// server is not listening yet, and otherwise the message, such as the timeout's.
const problemOf = (error: unknown): string => {
  if (!(error instanceof Error)) {
    return String(error)
  }
  const { cause } = error
  const code: unknown = cause instanceof Error && 'code' in cause ? cause.code : undefined
  return typeof code === 'string' ? code : error.message
}

/**
 * Ask one URL, without following a redirect: a redirect is the application answering.
 * @param url the URL.
 * @returns its status, or why no answer came within {@link PROBE_TIMEOUT_MS}.
 */
export const probeOnce = async (url: string): Promise<ProbeOutcome> => {
  try {
    const response: Response = await fetch(url, { redirect: 'manual', signal: AbortSignal.timeout(PROBE_TIMEOUT_MS) })
    await response.arrayBuffer()
    return { status: response.status }
  } catch (error: unknown) {
    return { problem: problemOf(error) }
  }
}
