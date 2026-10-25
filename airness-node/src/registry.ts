// When each release was published, read from the registry pnpm fetched it from, through the proxy the
// environment names. Nothing is cached: every call asks the registry again, because the answer is about the
// world rather than about the project.
import { setGlobalProxyFromEnv } from 'node:http'
import { asRecord, asText } from './json-shapes.js'
import { redactUrls } from './redaction.js'

/** A release to look up: its name, its version, and the registry it was resolved from. */
export interface Release {
  readonly name: string
  readonly version: string
  /** The registry's URL, ending in a slash. */
  readonly registry: string
  /** The token the configuration names for that registry. */
  readonly token?: string | undefined
}

/** One release looked up: when it was published, or why that could not be read. */
export type Lookup =
  | (Release & { readonly publishedAt: number; readonly problem?: undefined })
  | (Release & { readonly publishedAt?: undefined; readonly problem: string })

// Enough requests in flight to read a few hundred documents in seconds, few enough not to look like abuse.
const CONCURRENCY: number = 16
const REQUEST_TIMEOUT_MS: number = 60_000

/** The document a release's times are read from: its registry and its name. */
const documentKey = (release: Release): string => `${release.registry}${release.name.replace('/', '%2f')}`

const describe = (error: unknown): string =>
  error instanceof Error ? [error.message, describe(error.cause)].filter((part) => part.length > 0).join(': ') : ''

// Redirects are followed here rather than by fetch, so that a registry redirecting from HTTPS to plain HTTP is
// refused instead of read: a publication time read over plain HTTP is one anyone on the way could have changed.
const MOST_REDIRECTS: number = 5
const MOVED_PERMANENTLY: number = 301
const FOUND: number = 302
const SEE_OTHER: number = 303
const TEMPORARY_REDIRECT: number = 307
const PERMANENT_REDIRECT: number = 308
const REDIRECT_STATUSES: ReadonlySet<number> = new Set([
  MOVED_PERMANENTLY,
  FOUND,
  SEE_OTHER,
  TEMPORARY_REDIRECT,
  PERMANENT_REDIRECT,
])

/**
 * Whether a redirect may be followed: to HTTPS from anywhere, and to plain HTTP only from plain HTTP, as a local
 * registry is reached.
 * @param from the address that answered.
 * @param to where it redirects.
 * @returns true when the redirect may be followed.
 */
export const isPermittedRedirect = (from: URL, to: URL): boolean =>
  to.protocol === 'https:' || (to.protocol === 'http:' && from.protocol === 'http:')

// Where a redirect leads, or why it is not followed: at most a few hops, and never from HTTPS to plain HTTP.
const nextHop = (address: string, location: string, hop: number): { readonly url?: URL; readonly problem?: string } => {
  const to: URL | undefined = URL.canParse(location, address) ? new URL(location, address) : undefined
  if (to === undefined) {
    return { problem: redactUrls(`${address} redirected to an address that does not parse`) }
  }
  return hop < MOST_REDIRECTS && isPermittedRedirect(new URL(address), to)
    ? { url: to }
    : {
        problem: redactUrls(
          `${address} redirected to ${to.href}, which is not followed: at most ${String(MOST_REDIRECTS)} ` +
            'redirects are, never from HTTPS to plain HTTP',
        ),
      }
}

// The answer at the end of the redirects, or why the chain was not followed. The token goes only to the origin it
// was configured for: a redirect to another scheme, host or port carries none.
const fetchFollowing = async (
  address: string,
  token: string | undefined,
  hop: number = 0,
): Promise<Response | string> => {
  const response: Response = await fetch(address, {
    headers: { accept: 'application/json', ...(token !== undefined && { authorization: `Bearer ${token}` }) },
    redirect: 'manual',
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  })
  const location: string | null = response.headers.get('location')
  if (location === null || !REDIRECT_STATUSES.has(response.status)) {
    return response
  }
  const next: { readonly url?: URL; readonly problem?: string } = nextHop(address, location, hop)
  if (next.url === undefined) {
    return next.problem ?? address
  }
  return fetchFollowing(next.url.href, new URL(address).origin === next.url.origin ? token : undefined, hop + 1)
}

// The registry's document for one package, holding the `time` of every version it published.
const publicationTimes = async (release: Release): Promise<Record<string, unknown> | string> => {
  try {
    const response: Response | string = await fetchFollowing(documentKey(release), release.token)
    if (typeof response === 'string') {
      return response
    }
    return response.ok
      ? asRecord(asRecord(await response.json())['time'])
      : redactUrls(`${release.registry} answered ${String(response.status)} ${response.statusText}`)
  } catch (error: unknown) {
    return redactUrls(describe(error) || String(error))
  }
}

const lookupIn = (times: Record<string, unknown> | string | undefined, release: Release): Lookup => {
  if (typeof times === 'string') {
    return { ...release, problem: times }
  }
  const publishedAt: number = Date.parse(asText(asRecord(times)[release.version]))
  return Number.isNaN(publishedAt)
    ? { ...release, problem: redactUrls(`${release.registry} names no publication time for this version`) }
    : { ...release, publishedAt }
}

/**
 * Look up when each release was published, each from its own registry, through the proxy that
 * `HTTPS_PROXY`, `HTTP_PROXY` and `NO_PROXY` name.
 * @param releases the releases to look up.
 * @returns one lookup per release, in the order given.
 */
export const publicationTimesOf = async (releases: readonly Release[]): Promise<readonly Lookup[]> => {
  const restore: () => void = setGlobalProxyFromEnv()
  try {
    // One request per document, however many releases of the package the lockfile holds.
    const unique: readonly Release[] = [
      ...new Map(
        releases.map((release: Release): readonly [string, Release] => [documentKey(release), release]),
      ).values(),
    ]
    const batches: readonly (readonly Release[])[] = Array.from(
      { length: Math.ceil(unique.length / CONCURRENCY) },
      (_: unknown, index: number): readonly Release[] => unique.slice(index * CONCURRENCY, (index + 1) * CONCURRENCY),
    )
    type Read = readonly [string, Record<string, unknown> | string]
    const read: ReadonlyMap<string, Record<string, unknown> | string> = new Map(
      await batches.reduce(
        async (previous: Promise<readonly Read[]>, batch: readonly Release[]): Promise<readonly Read[]> => [
          ...(await previous),
          ...(await Promise.all(
            batch.map(
              async (release: Release): Promise<Read> => [documentKey(release), await publicationTimes(release)],
            ),
          )),
        ],
        Promise.resolve([]),
      ),
    )
    return releases.map((release: Release): Lookup => lookupIn(read.get(documentKey(release)), release))
  } finally {
    restore()
  }
}
