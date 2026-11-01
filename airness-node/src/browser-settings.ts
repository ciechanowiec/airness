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
        // Optional, unlike the two above: a server started in the package under test needs no
        // directory, and only a sibling application does.
        const cwd: string = asText(record['cwd']).trim()
        return [cwd.length > 0 ? { command, url, cwd } : { command, url }]
      })
    : []

const VARIABLE_NAME: RegExp = /^[A-Z][A-Z0-9_]*$/u

// The harness sets these itself for the servers it starts, so a project naming one would overwrite what the
// harness relies on.
const HARNESS_VARIABLE: RegExp = /^(?:AIRNESS_|NODE_|NEXT_TELEMETRY_)|^(?:PORT|HOSTNAME)$/u

// Refused rather than skipped when malformed: a name that silently set nothing would leave the application
// building its links and its accepted origins from an address the run does not serve on.
const asServerUrlVariables = (raw: unknown): readonly string[] => {
  if (raw === undefined) {
    return []
  }
  if (!(isArray(raw) && raw.every((entry: unknown): entry is string => typeof entry === 'string'))) {
    throw new Error('airness.serverUrlVariables must be an array of environment-variable names')
  }
  const malformed: string | undefined = raw.find((name: string): boolean => !VARIABLE_NAME.test(name))
  if (malformed !== undefined) {
    throw new Error(`airness.serverUrlVariables entry "${malformed}" is not an upper-case environment-variable name`)
  }
  const owned: string | undefined = raw.find((name: string): boolean => HARNESS_VARIABLE.test(name))
  if (owned !== undefined) {
    throw new Error(`airness.serverUrlVariables entry "${owned}" is set by the harness itself`)
  }
  return [...new Set<string>(raw)]
}

/** The browser inputs of a manifest, with a pinned clock unless explicitly changed. */
export const browserSettings = (
  raw: Record<string, unknown>,
): {
  readonly clock: string
  readonly auxiliaryServers: readonly AuxiliaryServer[]
  readonly serverUrlVariables: readonly string[]
} => ({
  clock: clockOf(raw['clock']),
  auxiliaryServers: asAuxiliaryServers(raw['auxiliaryServers']),
  serverUrlVariables: asServerUrlVariables(raw['serverUrlVariables']),
})
