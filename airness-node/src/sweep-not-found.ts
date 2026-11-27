// Missing-address probes join the ordinary sweep, so they use the application's real layout and
// every existing browser rule. Their expected document 404 is distinguished from broken subresources. The answers
// a probe receives owe their caching decisions as a crawled page's do: a cache may store a 404 for as long as it
// guesses, so a missing page declares its caching too.
import type { Page, Response } from '@playwright/test'
import { declaredNotFoundBoundaries, isSkippedSweepRoute } from './a11y.js'
import type { BrowserFinding } from './browser-findings.js'
import { NOT_FOUND_LOCATION_PREFIX, type NotFoundBoundary, notFoundProbes } from './not-found-routes.js'
import { settleForScan } from './settle.js'
import { answerFindings, cachingFindings, type JudgedAnswers } from './sweep-answers.js'
import { CONTROLS, origin, type SweepLocation } from './sweep-shared.js'

/** Discovered error states and boundaries for which no concrete URL was available. */
export interface NotFoundDiscovery {
  readonly locations: readonly SweepLocation[]
  readonly problems: readonly string[]
  /** The caching findings of the answers the probes received. */
  readonly findings: readonly BrowserFinding[]
}

/** One probe's missing-page state, and the findings of what it was answered. */
interface Probed {
  readonly location: SweepLocation
  readonly answers: JudgedAnswers
}

const STREAMED_PAGE_STATUS: number = 200
const MISSING_PAGE_STATUS: number = 404
const PAGE_STATUSES: ReadonlySet<number> = new Set<number>([STREAMED_PAGE_STATUS, MISSING_PAGE_STATUS])

const probe = async (page: Page, route: string, judged: ReadonlySet<string>): Promise<Probed> => {
  const response: Response | null = await page.goto(`${origin}${route}`)
  if (response === null || !PAGE_STATUSES.has(response.status())) {
    throw new Error(`Not-found probe ${route} did not return HTML with status 200 or 404`)
  }
  if (response.headers()['content-type']?.includes('text/html') !== true) {
    throw new Error(`Not-found probe ${route} did not return HTML`)
  }
  await settleForScan(page)
  const location: string = `${NOT_FOUND_LOCATION_PREFIX}${new URL(page.url()).pathname}`
  const loaded: JudgedAnswers = await answerFindings(page, response, location, judged)
  return {
    location: {
      location,
      url: page.url(),
      isStory: false,
      edges: [],
      hasDarkMedia: false,
      controls: await page.locator(CONTROLS).count(),
    },
    answers: {
      judged: loaded.judged,
      findings: [...(await cachingFindings(response, location, page)), ...loaded.findings],
    },
  }
}

/**
 * Discover missing-page states using concrete locale and route prefixes from the public crawl.
 * @param page the discovery page.
 * @param visited the public URLs already reached.
 * @param judged the answers the crawl already judged, each as its method and URL, which are not judged again.
 * @returns the error pages to scan, any unresolved boundaries, and the caching findings of the probes.
 */
export const discoverNotFound = async (
  page: Page,
  visited: readonly string[],
  judged: ReadonlySet<string>,
): Promise<NotFoundDiscovery> => {
  const boundaries: readonly NotFoundBoundary[] = declaredNotFoundBoundaries()
  const probes: readonly string[] = [
    ...new Set<string>(
      boundaries.flatMap((boundary: NotFoundBoundary): readonly string[] => notFoundProbes(boundary, visited)),
    ),
  ].filter((route: string): boolean => !isSkippedSweepRoute(route))
  const probed: readonly Probed[] = await probes.reduce(
    async (earlier: Promise<readonly Probed[]>, route: string): Promise<readonly Probed[]> => {
      const done: readonly Probed[] = await earlier
      return [...done, await probe(page, route, done.at(-1)?.answers.judged ?? judged)]
    },
    Promise.resolve<readonly Probed[]>([]),
  )
  return {
    locations: probed.map((each: Probed): SweepLocation => each.location),
    findings: probed.flatMap((each: Probed): readonly BrowserFinding[] => each.answers.findings),
    problems: boundaries
      .filter((boundary: NotFoundBoundary): boolean => notFoundProbes(boundary, visited).length === 0)
      .map(
        (boundary: NotFoundBoundary): string =>
          `${boundary.file}: no concrete public prefix reaches its not-found state`,
      ),
  }
}
