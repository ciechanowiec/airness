// Whether a browser run may start against a server that is already listening, and how to tell that a
// listener is there at all. The end-to-end gate asks before it starts Playwright, because a run that reused a
// stale server would judge a build nobody made.

/**
 * The variable the end-to-end gate sets on the Playwright run it starts. Its presence, whatever its value,
 * marks a verification run: the production server is started and no existing server is reused.
 */
export const VERIFICATION_ENVIRONMENT_VARIABLE: string = 'AIRNESS_VERIFICATION'

/**
 * Which server a browser run judges. A verification run starts the production server: the production build of a
 * Next.js application, or the declared `serve` script with NODE_ENV=production, which for a Java web interface
 * launches the packaged artifact. A run started any other way, such as a developer's own `playwright test`, may
 * be judging a development server, and is held only to what a development server can also meet.
 */
export type ServedBy = 'production' | 'development'

/** The server a browser run with the given environment judges, read from the mark the end-to-end gate sets. */
export const servedByOf = (environment: Readonly<Record<string, string | undefined>>): ServedBy =>
  environment[VERIFICATION_ENVIRONMENT_VARIABLE] === undefined ? 'development' : 'production'

/** The TCP endpoint behind a browser server's readiness URL. */
export interface BrowserServerEndpoint {
  readonly url: string
  readonly host: string
  readonly port: number
}

/** What the socket adapter established, without interpreting an unknown result as vacancy. */
export type BrowserServerProbe =
  | { readonly status: 'vacant' }
  | { readonly status: 'occupied' }
  | { readonly status: 'unknown'; readonly detail: string }

const HTTP_PORT: number = 80
const HTTPS_PORT: number = 443

/** Whether a direct browser run may use a server it did not start. */
export const canReuseBrowserServer = (environment: Readonly<Record<string, string | undefined>>): boolean =>
  (environment['CI'] ?? '').length === 0 && environment[VERIFICATION_ENVIRONMENT_VARIABLE] === undefined

/** Resolve a readiness URL to its TCP address, retaining the URL for repair messages. */
export const browserServerEndpoint = (url: string): BrowserServerEndpoint => {
  const parsed: URL = new URL(url)
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error('browser server URLs must use http or https')
  }
  const defaultPort: number = parsed.protocol === 'https:' ? HTTPS_PORT : HTTP_PORT
  const port: number = parsed.port.length === 0 ? defaultPort : Number(parsed.port)
  if (port === 0) {
    throw new Error('browser server URLs must name a fixed port, not port 0')
  }
  return {
    url,
    host: parsed.hostname.startsWith('[') ? parsed.hostname.slice(1, -1) : parsed.hostname,
    port,
  }
}

/** Only an explicit refusal establishes that an address has no TCP listener. */
export const browserServerConnectionFailure = (code: string | undefined, detail: string): BrowserServerProbe =>
  code === 'ECONNREFUSED' ? { status: 'vacant' } : { status: 'unknown', detail }

/** An endpoint is vacant only when every resolved address was checked and refused a connection. */
export const combineBrowserServerProbes = (probes: readonly BrowserServerProbe[]): BrowserServerProbe => {
  if (probes.some((probe: BrowserServerProbe): boolean => probe.status === 'occupied')) {
    return { status: 'occupied' }
  }
  return (
    probes.find((probe: BrowserServerProbe): boolean => probe.status === 'unknown') ??
    (probes.length === 0
      ? { status: 'unknown', detail: 'the hostname resolved to no addresses' }
      : { status: 'vacant' })
  )
}

/** The concrete repair for an occupied address or an inconclusive check. */
export const browserServerProblem = (
  endpoint: BrowserServerEndpoint,
  probe: BrowserServerProbe,
): string | undefined => {
  if (probe.status === 'vacant') {
    return undefined
  }
  const location: string = `port ${String(endpoint.port)} at ${endpoint.url}`
  return probe.status === 'occupied'
    ? `${location} is in use; stop the existing server before rerunning verification`
    : `cannot check ${location}: ${probe.detail}; fix the address or connectivity and rerun verification`
}
