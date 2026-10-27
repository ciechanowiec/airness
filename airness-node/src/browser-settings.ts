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

/** The browser inputs of a manifest, with a pinned clock unless explicitly changed. */
export const browserSettings = (
  raw: Record<string, unknown>,
): {
  readonly clock: string
  readonly auxiliaryServers: readonly AuxiliaryServer[]
} => ({
  clock: clockOf(raw['clock']),
  auxiliaryServers: asAuxiliaryServers(raw['auxiliaryServers']),
})
