// Browser-specific inputs, shared by discovery and server startup.
import { clockOf } from './browser-run.js'
import { asRecord, asText, isArray } from './json-shapes.js'

export interface AuxiliaryServer {
  /** The command that starts it, as Playwright takes it: one string rather than an argv list. */
  readonly command: string
  /** The origin Playwright waits for before the suite starts. */
  readonly url: string
  /**
   * The directory to start it in, relative to the member under test.
   *
   * A sibling application is started from its own package, not from the one being tested. Without this
   * the only way to say so was a `cd` inside the command, which then resolves the framework binary from
   * the wrong package's `.bin` - correct exactly while the two packages pin the same version.
   */
  readonly cwd?: string
  /**
   * The environment variables the run sets to this server's origin, for the application, the other servers and
   * the browser suite. A run serves the server on a port of its own, so an application that reads a sibling such
   * as a CMS learns where it is from these, as it learns its own origin from `serverUrlVariables`.
   */
  readonly urlVariables?: readonly string[]
  /**
   * Whether the run serves this server on its declared port rather than on one of its own. Only for a server
   * whose address an application inlines when it is built, such as a public variable a framework writes into
   * its bundle: the build precedes the run's choice of ports, so no variable set afterwards reaches it. The
   * run then fails when the declared port is taken, and two such runs cannot share a machine.
   */
  readonly fixedPort?: true
}

const VARIABLE_NAME: RegExp = /^[A-Z][A-Z0-9_]*$/u

// The harness sets these itself for the servers it starts, so a project naming one would overwrite what the
// harness relies on.
const HARNESS_VARIABLE: RegExp = /^(?:AIRNESS_|NODE_|NEXT_TELEMETRY_)|^(?:PORT|HOSTNAME)$/u

// Refused rather than skipped when malformed: a name that silently set nothing would leave the application
// building its links and its accepted origins from an address the run does not serve on.
const asOriginVariables = (raw: unknown, key: string): readonly string[] => {
  if (raw === undefined) {
    return []
  }
  if (!(isArray(raw) && raw.every((entry: unknown): entry is string => typeof entry === 'string'))) {
    throw new Error(`${key} must be an array of environment-variable names`)
  }
  const malformed: string | undefined = raw.find((name: string): boolean => !VARIABLE_NAME.test(name))
  if (malformed !== undefined) {
    throw new Error(`${key} entry "${malformed}" is not an upper-case environment-variable name`)
  }
  const owned: string | undefined = raw.find((name: string): boolean => HARNESS_VARIABLE.test(name))
  if (owned !== undefined) {
    throw new Error(`${key} entry "${owned}" is set by the harness itself`)
  }
  return [...new Set<string>(raw)]
}

// One variable names one origin: a name given to two servers would leave the run choosing which one it reads.
const refuseSharedVariables = (
  serverUrlVariables: readonly string[],
  auxiliaryServers: readonly AuxiliaryServer[],
): void => {
  const names: readonly string[] = [
    ...serverUrlVariables,
    ...auxiliaryServers.flatMap((server: AuxiliaryServer): readonly string[] => server.urlVariables ?? []),
  ]
  const shared: string | undefined = names.find((name: string, index: number): boolean => names.indexOf(name) !== index)
  if (shared !== undefined) {
    throw new Error(`the origin variable "${shared}" is named for more than one server; each names one origin`)
  }
}

// Refused rather than read as false when it is not a boolean: a port silently left to the run would leave an
// application that inlined the declared address reading a server that is not there.
const isFixedPort = (raw: unknown, url: string): boolean => {
  if (raw !== undefined && typeof raw !== 'boolean') {
    throw new Error(`airness.auxiliaryServers fixedPort of ${url} must be true or false`)
  }
  return raw === true
}

// The fields a server declares only when it needs them, each left out when it is not set.
const optionalServerFields = (
  record: Record<string, unknown>,
  url: string,
): Pick<AuxiliaryServer, 'cwd' | 'urlVariables' | 'fixedPort'> => {
  // Optional, unlike the command and the URL: a server started in the package under test needs no
  // directory, and only a sibling application does.
  const cwd: string = asText(record['cwd']).trim()
  const urlVariables: readonly string[] = asOriginVariables(
    record['urlVariables'],
    'airness.auxiliaryServers urlVariables',
  )
  return {
    ...(cwd.length > 0 && { cwd }),
    ...(urlVariables.length > 0 && { urlVariables }),
    ...(isFixedPort(record['fixedPort'], url) && { fixedPort: true }),
  }
}

const asAuxiliaryServers = (raw: unknown): readonly AuxiliaryServer[] =>
  isArray(raw)
    ? raw.flatMap((entry: unknown): readonly AuxiliaryServer[] => {
        const record: Record<string, unknown> = asRecord(entry)
        const command: string = asText(record['command']).trim()
        const url: string = asText(record['url']).trim()
        if (command.length === 0 || url.length === 0) {
          return []
        }
        return [{ command, url, ...optionalServerFields(record, url) }]
      })
    : []

/** The browser inputs of a manifest, with a pinned clock unless explicitly changed. */
export const browserSettings = (
  raw: Record<string, unknown>,
): {
  readonly clock: string
  readonly auxiliaryServers: readonly AuxiliaryServer[]
  readonly serverUrlVariables: readonly string[]
} => {
  const auxiliaryServers: readonly AuxiliaryServer[] = asAuxiliaryServers(raw['auxiliaryServers'])
  const serverUrlVariables: readonly string[] = asOriginVariables(
    raw['serverUrlVariables'],
    'airness.serverUrlVariables',
  )
  refuseSharedVariables(serverUrlVariables, auxiliaryServers)
  return { clock: clockOf(raw['clock']), auxiliaryServers, serverUrlVariables }
}
