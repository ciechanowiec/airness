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

// The registry's document for one package, holding the `time` of every version it published.
const publicationTimes = async (release: Release): Promise<Record<string, unknown> | string> => {
  try {
    const response: Response = await fetch(documentKey(release), {
      headers: {
        accept: 'application/json',
        ...(release.token !== undefined && { authorization: `Bearer ${release.token}` }),
      },
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    })
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
