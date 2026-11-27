// What one crawled navigation answered beyond the page it showed: the redirects it followed, which decide
// whether it covers the address the crawl asked for, the security headers it carried, the caching decisions of
// every answer of the application the page loaded, and the htmx fragments the page names, which are routes no link
// reaches.
import type { Page, Request, Response } from '@playwright/test'
import { requestedFragments } from './a11y.js'
import { type BrowserFinding, findingAt } from './browser-findings.js'
import { BROKEN_LINK_RULE } from './browser-health.js'
import { servedByOf } from './browser-server-policy.js'
import { cachingProblems, type Exchange } from './caching-headers.js'
import type { FragmentVerdict } from './htmx-fragments.js'
import { coversRequestedRoute } from './route-coverage.js'
import { engineOf, lastNavigationIndex } from './scanner.js'
import { securityHeaderProblems } from './security-headers.js'
import { origin } from './sweep-shared.js'

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

const exchangeOf = async (request: Request, response: Response): Promise<Exchange> => ({
  method: request.method(),
  status: response.status(),
  requestHeaders: await request.allHeaders(),
  responseHeaders: await response.allHeaders(),
})

const headerFinding = (page: Page, location: string, problem: string): BrowserFinding =>
  findingAt({ location, state: 'discovery', engine: engineOf(page), viewport: undefined }, 'security-headers', problem)

/**
 * The security-header findings of an answered page, judged as the server the run started answers: in a
 * verification run, the production server, which owes transport security and Secure cookies too. An HTML page is
 * held to the whole baseline, any other answer to its caching decisions alone. Read through `allHeaders()` rather
 * than `headers()`, which withholds the Set-Cookie lines and the request's Authorization.
 * @param response the navigation's final answer.
 * @param location where each finding is placed: the address the crawl asked for, or the missing-page state.
 * @param page the page, whose engine each finding names.
 * @returns one finding per problem, or nothing.
 */
const headerFindings = async (response: Response, location: string, page: Page): Promise<readonly BrowserFinding[]> => {
  const exchange: Exchange = await exchangeOf(response.request(), response)
  const problems: readonly string[] =
    exchange.responseHeaders['content-type']?.includes('text/html') === true
      ? securityHeaderProblems(exchange.responseHeaders, servedByOf(process.env), exchange)
      : cachingProblems(exchange)
  return problems.map((problem: string): BrowserFinding => headerFinding(page, location, problem))
}

/**
 * The caching findings of one answer the page navigated to, without the rest of the baseline.
 * @param response the navigation's final answer.
 * @param location where each finding is placed.
 * @param page the page, whose engine each finding names.
 * @returns one finding per caching problem, or nothing.
 */
export const cachingFindings = async (
  response: Response,
  location: string,
  page: Page,
): Promise<readonly BrowserFinding[]> =>
  cachingProblems(await exchangeOf(response.request(), response)).map(
    (problem: string): BrowserFinding => headerFinding(page, location, problem),
  )

/** What a crawl has judged of the answers its pages loaded. */
export interface JudgedAnswers {
  /** The answers judged so far, each as its method and URL, so that an answer many pages load is judged once. */
  readonly judged: ReadonlySet<string>
  /** One finding per caching problem of an answer judged here for the first time. */
  readonly findings: readonly BrowserFinding[]
}

const redirectsBefore = (request: Request | null): readonly Request[] =>
  request === null ? [] : [request, ...redirectsBefore(request.redirectedFrom())]

/**
 * The caching findings of every answer of the application a page loaded beside its document: the redirects the
 * navigation followed, and the scripts, stylesheets, images, fonts and fetches the page asked for. Another
 * origin's answer, such as a font or an analytics script, is left to its own owner.
 * @param page the page, settled where the navigation landed.
 * @param navigation the navigation's final answer, judged by {@link headerFindings} instead.
 * @param location where each finding is placed.
 * @param judged the answers the crawl judged so far.
 * @returns the answers judged so far with this page's added, and this page's findings.
 */
export const answerFindings = async (
  page: Page,
  navigation: Response,
  location: string,
  judged: ReadonlySet<string>,
): Promise<JudgedAnswers> => {
  const requests: readonly Request[] = await page.requests()
  const loaded: readonly Request[] = [
    ...redirectsBefore(navigation.request().redirectedFrom()),
    ...requests.slice(lastNavigationIndex(page, requests) + 1),
  ]
  return await loaded.reduce(
    async (earlier: Promise<JudgedAnswers>, request: Request): Promise<JudgedAnswers> => {
      const collected: JudgedAnswers = await earlier
      const key: string = `${request.method()} ${request.url()}`
      const response: Response | null = request.existingResponse()
      const url: URL = new URL(request.url())
      if (response === null || url.origin !== origin || collected.judged.has(key)) {
        return collected
      }
      const problems: readonly string[] = cachingProblems(await exchangeOf(request, response))
      return {
        judged: new Set<string>([...collected.judged, key]),
        findings: [
          ...collected.findings,
          ...problems.map(
            (problem: string): BrowserFinding =>
              headerFinding(page, location, `${request.method()} ${url.pathname}${url.search}: ${problem}`),
          ),
        ],
      }
    },
    Promise.resolve<JudgedAnswers>({ judged, findings: [] }),
  )
}

/** What a crawl has gathered from the answers its pages received so far. */
export interface AnswersSoFar {
  /** Every fragment address requested so far. */
  readonly requestedFragments: ReadonlySet<string>
  /** The paths of the fragments that rendered. */
  readonly fragments: ReadonlySet<string>
  /** The answers of the application its pages loaded that were judged, each as its method and URL. */
  readonly judgedAnswers: ReadonlySet<string>
}

/** The crawl's fragments once one more page added its own. */
interface CrawledFragments extends Pick<AnswersSoFar, 'requestedFragments' | 'fragments'> {
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
const crawledFragments = async (
  page: Page,
  soFar: AnswersSoFar,
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

/** What a crawl has gathered before its first page. */
export const NO_ANSWERS: AnswersSoFar = {
  requestedFragments: new Set<string>(),
  fragments: new Set<string>(),
  judgedAnswers: new Set<string>(),
}

/** The crawl's answers once one more page added its own. */
export interface PageAnswers extends AnswersSoFar {
  /** This page's fragment and security-header findings. */
  readonly findings: readonly BrowserFinding[]
}

/** One crawled page, settled where its navigation landed. */
export interface CrawledPage {
  /** The navigation's final answer. */
  readonly response: Response
  /** The address the crawl asked for, where the page's findings are placed. */
  readonly route: string
  /** The page's `hx-get` values. */
  readonly fragments: readonly string[]
}

/**
 * Judge what one crawled page answered beyond what it showed: the security headers of its document, the caching
 * decisions of every answer of the application it loaded that no earlier page did, and the fragments it names that
 * no earlier page did.
 * @param page the crawled page.
 * @param soFar what the crawl gathered before this page.
 * @param crawled the page's navigation, route and fragments.
 * @returns what the crawl gathered with this page's added, and this page's findings.
 */
export const pageAnswers = async (page: Page, soFar: AnswersSoFar, crawled: CrawledPage): Promise<PageAnswers> => {
  const loaded: JudgedAnswers = await answerFindings(page, crawled.response, crawled.route, soFar.judgedAnswers)
  const fragments: CrawledFragments = await crawledFragments(page, soFar, crawled.fragments, crawled.route)
  return {
    requestedFragments: fragments.requestedFragments,
    fragments: fragments.fragments,
    judgedAnswers: loaded.judged,
    findings: [
      ...fragments.findings,
      ...(await headerFindings(crawled.response, crawled.route, page)),
      ...loaded.findings,
    ],
  }
}
