// What one crawled navigation answered beyond the page it showed: the redirects it followed, which decide
// whether it covers the address the crawl asked for, the security headers it carried, and the htmx fragments the
// page names, which are routes no link reaches.
import type { Page, Request, Response } from '@playwright/test'
import { requestedFragments } from './a11y.js'
import { type BrowserFinding, findingAt } from './browser-findings.js'
import { BROKEN_LINK_RULE } from './browser-health.js'
import { servedByOf } from './browser-server-policy.js'
import type { FragmentVerdict } from './htmx-fragments.js'
import { coversRequestedRoute } from './route-coverage.js'
import { engineOf } from './scanner.js'
import { securityHeaderProblems } from './security-headers.js'

// The status of every redirect a navigation followed before its final answer.
const redirectStatuses = async (request: Request | null): Promise<readonly number[]> => {
  if (request === null) {
    return []
  }
  const hop: Response | null = await request.response()
  return [...(hop === null ? [] : [hop.status()]), ...(await redirectStatuses(request.redirectedFrom()))]
}

/**
 * Whether an answered navigation covers the address the crawl asked for, by the redirects it followed.
 * @param response the navigation's final answer.
 * @param route the address the crawl asked for.
 * @param page the page, now on wherever the navigation landed.
 * @returns true when the requested address is covered, rather than only where it landed.
 */
export const coversItsRoute = async (response: Response, route: string, page: Page): Promise<boolean> =>
  coversRequestedRoute(await redirectStatuses(response.request().redirectedFrom()), route, new URL(page.url()).pathname)

/**
 * The security-header findings of an answered HTML page, judged as the server the run started answers: in a
 * verification run, the production server, which owes transport security and Secure cookies too. Read through
 * `allHeaders()` rather than `headers()`, which withholds the Set-Cookie lines.
 * @param response the navigation's final answer.
 * @param route the address the crawl asked for, which each finding names.
 * @param page the page, whose engine each finding names.
 * @returns one finding per problem, or nothing for a response that is not HTML.
 */
export const headerFindings = async (
  response: Response,
  route: string,
  page: Page,
): Promise<readonly BrowserFinding[]> => {
  const headers: Record<string, string> = await response.allHeaders()
  return headers['content-type']?.includes('text/html') === true
    ? securityHeaderProblems(headers, servedByOf(process.env)).map(
        (problem: string): BrowserFinding =>
          findingAt(
            { location: route, state: 'discovery', engine: engineOf(page), viewport: undefined },
            'security-headers',
            problem,
          ),
      )
    : []
}

/** The fragments of the crawl so far. */
export interface FragmentsSoFar {
  /** Every fragment address requested so far. */
  readonly requestedFragments: ReadonlySet<string>
  /** The paths of the fragments that rendered. */
  readonly fragments: ReadonlySet<string>
}

/** The crawl's fragments once one more page added its own. */
export interface CrawledFragments extends FragmentsSoFar {
  /** One finding per fragment of this page the server failed to render, placed on the page that names it. */
  readonly findings: readonly BrowserFinding[]
}

const absolute = (value: string, base: string): readonly string[] => {
  try {
    return [new URL(value, base).href]
  } catch {
    return []
  }
}

/**
 * Request the fragments a crawled page names that no earlier page did, in the crawl's session.
 * @param page the crawled page.
 * @param soFar the crawl's fragments before this page.
 * @param values the page's `hx-get` values.
 * @param route the page's route, where a failed fragment is reported.
 * @returns the crawl's fragments with this page's added, and this page's fragment findings.
 */
export const crawledFragments = async (
  page: Page,
  soFar: FragmentsSoFar,
  values: readonly string[],
  route: string,
): Promise<CrawledFragments> => {
  const fresh: readonly string[] = values.filter((value: string): boolean =>
    absolute(value, page.url()).some((url: string): boolean => !soFar.requestedFragments.has(url)),
  )
  if (fresh.length === 0) {
    return { requestedFragments: soFar.requestedFragments, fragments: soFar.fragments, findings: [] }
  }
  const verdict: FragmentVerdict = await requestedFragments(page, fresh)
  return {
    requestedFragments: new Set<string>([
      ...soFar.requestedFragments,
      ...fresh.flatMap((value: string): readonly string[] => absolute(value, page.url())),
    ]),
    fragments: new Set<string>([...soFar.fragments, ...verdict.credited]),
    findings: verdict.problems.map(
      (problem: string): BrowserFinding =>
        findingAt(
          { location: route, state: 'discovery', engine: engineOf(page), viewport: undefined },
          BROKEN_LINK_RULE,
          problem,
        ),
    ),
  }
}
