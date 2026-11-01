// Where a verification run serves the application and its auxiliary servers. The declared `serverUrl` and
// auxiliary URLs keep their scheme, host and path, but in verification each gets a port the operating system
// chose for this run. A developer's server on the declared port, or another verification on the same machine,
// then cannot collide with the run. The e2e gate chooses the ports once and hands the result to Playwright
// through the environment, because the configuration and every worker read the settings anew from
// package.json, and the environment is the one channel that reaches all of them and the servers they start.
import { type AddressInfo, createServer, type Server } from 'node:net'
import type { AuxiliaryServer } from './browser-settings.js'
import { isArray } from './json-shapes.js'

/** The origin this run serves the application on, set by the e2e gate for Playwright and its workers. */
export const SERVER_URL_ENVIRONMENT_VARIABLE: string = 'AIRNESS_SERVER_URL'

/** The URLs of the auxiliary servers in declaration order, as a JSON array, set beside the origin. */
export const AUXILIARY_SERVER_URLS_ENVIRONMENT_VARIABLE: string = 'AIRNESS_AUXILIARY_SERVER_URLS'

/** The settings a served origin replaces, and the variables an application reads its own origin from. */
export interface OriginSettings {
  readonly serverUrl: string
  readonly auxiliaryServers: readonly AuxiliaryServer[]
  readonly serverUrlVariables: readonly string[]
}

/** The addresses one run serves on. */
export interface ServedOrigins {
  readonly serverUrl: string
  readonly auxiliaryUrls: readonly string[]
  /** The ports among them, which nothing else the run starts may take. */
  readonly ports: readonly number[]
}

/**
 * A declared URL on another port, with its scheme, host and path kept.
 * @param url the declared URL.
 * @param port the port this run serves it on.
 * @returns the URL, with no trailing slash added to a bare origin.
 */
export const onPort = (url: string, port: number): string => {
  const parsed: URL = new URL(url)
  const origin: string = `${parsed.protocol}//${parsed.hostname}:${String(port)}`
  return parsed.pathname === '/' ? origin : `${origin}${parsed.pathname}`
}

/**
 * The addresses a run serves on, given one port for the application and one per auxiliary server.
 * @param settings the declared addresses.
 * @param ports the ports, the application's first, then the auxiliary servers' in declaration order.
 * @returns the served addresses.
 */
export const servedOrigins = (settings: OriginSettings, ports: readonly number[]): ServedOrigins => {
  if (ports.length !== settings.auxiliaryServers.length + 1) {
    throw new Error('one port is needed for the application and one for each auxiliary server')
  }
  const [primary = 0, ...auxiliary] = ports
  return {
    serverUrl: onPort(settings.serverUrl, primary),
    auxiliaryUrls: settings.auxiliaryServers.map((server: AuxiliaryServer, index: number): string =>
      onPort(server.url, auxiliary[index] ?? 0),
    ),
    ports,
  }
}

/**
 * The environment that hands a run's addresses to Playwright, its workers and the servers it starts: the
 * harness's own two variables, and each variable the project reads its own origin from.
 * @param settings the project's settings.
 * @param origins the addresses this run serves on.
 * @returns the variables to set.
 */
export const originEnvironment = (
  settings: OriginSettings,
  origins: ServedOrigins,
): Readonly<Record<string, string>> => ({
  ...Object.fromEntries(
    settings.serverUrlVariables.map((name: string): readonly [string, string] => [
      name,
      new URL(origins.serverUrl).origin,
    ]),
  ),
  [SERVER_URL_ENVIRONMENT_VARIABLE]: origins.serverUrl,
  [AUXILIARY_SERVER_URLS_ENVIRONMENT_VARIABLE]: JSON.stringify(origins.auxiliaryUrls),
})

const auxiliaryUrlsOf = (raw: string, expected: number): readonly string[] => {
  const parsed: unknown = JSON.parse(raw)
  if (
    !isArray(parsed) ||
    parsed.length !== expected ||
    !parsed.every((entry: unknown): entry is string => typeof entry === 'string')
  ) {
    throw new Error(`${AUXILIARY_SERVER_URLS_ENVIRONMENT_VARIABLE} must list one URL per declared auxiliary server`)
  }
  return parsed
}

/**
 * The settings with the addresses this run serves on, when the e2e gate assigned them; otherwise, as outside
 * verification, the declared ones.
 * @param settings the declared settings.
 * @param environment the process environment.
 * @returns the settings the configuration and the sweep work with.
 */
export const servedSettings = <T extends OriginSettings>(
  settings: T,
  environment: Readonly<Record<string, string | undefined>>,
): T => {
  const served: string | undefined = environment[SERVER_URL_ENVIRONMENT_VARIABLE]
  if (served === undefined) {
    return settings
  }
  const auxiliary: readonly string[] = auxiliaryUrlsOf(
    environment[AUXILIARY_SERVER_URLS_ENVIRONMENT_VARIABLE] ?? '[]',
    settings.auxiliaryServers.length,
  )
  return {
    ...settings,
    serverUrl: served,
    auxiliaryServers: settings.auxiliaryServers.map(
      (server: AuxiliaryServer, index: number): AuxiliaryServer => ({
        ...server,
        url: auxiliary[index] ?? server.url,
      }),
    ),
  }
}

// ---------------------------------------------------------------------------------------------------
// Ports
//
// A port is found by listening on port 0 and then released for its server to take. The listeners of one
// request stay open together until all of them are known, so one request never returns the same port twice,
// and a caller names the ports it already holds for its own servers, so a later request does not offer one
// of them again. A listener on such a port is kept open too until the request is answered, because closed at
// once its port could be offered to the very next listener.

const LOOPBACK: string = '127.0.0.1'

interface Listening {
  readonly server: Server
  readonly port: number
}

const listener = async (): Promise<Listening> =>
  await new Promise<Listening>((resolve, reject): void => {
    const server: Server = createServer()
    server.once('error', reject)
    server.listen(0, LOOPBACK, (): void => {
      const address: AddressInfo | string | null = server.address()
      // Listening on a TCP port, never on a pipe, so the address is always the port's.
      resolve({ server, port: typeof address === 'object' && address !== null ? address.port : 0 })
    })
  })

const usable = (held: readonly Listening[], avoid: readonly number[]): readonly Listening[] =>
  held.filter((listening: Listening): boolean => !avoid.includes(listening.port))

const acquire = async (
  held: readonly Listening[],
  count: number,
  avoid: readonly number[],
): Promise<readonly Listening[]> =>
  usable(held, avoid).length >= count ? held : await acquire([...held, await listener()], count, avoid)

const closed = async (listening: Listening): Promise<void> => {
  await new Promise<void>((resolve): void => {
    listening.server.close((): void => {
      resolve()
    })
  })
}

/**
 * Loopback ports nothing listens on.
 * @param count how many distinct ports to find.
 * @param avoid ports the caller already holds for its own servers, which are not offered again.
 * @returns the ports, all released again so the servers they are meant for can bind them.
 */
export const freePorts = async (count: number, avoid: readonly number[] = []): Promise<readonly number[]> => {
  const held: readonly Listening[] = await acquire([], count, avoid)
  await Promise.all(
    held.map(async (listening: Listening): Promise<void> => {
      await closed(listening)
    }),
  )
  return usable(held, avoid)
    .slice(0, count)
    .map((listening: Listening): number => listening.port)
}
