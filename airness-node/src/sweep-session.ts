// What the signed-in crawl needs beyond the anonymous one: each page's structure, which tells the two
// visitors' renders apart, the anonymous visit it is compared with, and a watch over its session.
//
// A visit that ends the session would leave the rest of the crawl signed out without saying so, so the crawl
// proves its session on the first route that opens only with it, and asks that route again after every later
// visit; the decisions are the pure ones of `sweep-visitors.ts`.
import { type APIRequestContext, type APIResponse, type Page, request, type TestInfo } from '@playwright/test'
import {
  type Discovery,
  type DiscoveryReport,
  type DomDocument,
  type DomElement,
  discoveryFile,
  discoveryReportFile,
  isDiscovery,
  isDiscoveryReport,
  origin,
  readJson,
  type SweepLocation,
} from './sweep-shared.js'
import { isOwnAnswer, opensOnlyWithSession } from './sweep-visitors.js'

// The browser surface the page function below touches, declared for the reason `settle.ts` records.
declare const document: DomDocument

// What carries no structure: scripts and styles, and the hidden inputs, such as a form's request-forgery token,
// that differ between any two renders.
const STRUCTURED_ELEMENTS: string = 'body *:not(script, style, template, noscript, input[type="hidden"])'

/**
 * Every element of the page by its tag, role and input type, in document order, without any text. It runs in
 * the page, which receives only its source, so everything it reads arrives as its argument.
 * @param selector the elements that carry structure.
 * @returns the page's structure signature.
 */
const structureInPage = (selector: string): string =>
  Array.from(document.querySelectorAll(selector), (element: DomElement): string => {
    const role: string | null = element.getAttribute('role')
    const type: string | null = element.getAttribute('type')
    return (
      element.tagName.toLowerCase() + (role === null ? '' : `[role=${role}]`) + (type === null ? '' : `[type=${type}]`)
    )
  }).join(' ')

/**
 * Read the structure of the page the crawl is on.
 * @param page the crawl's page.
 * @returns the page's structure signature.
 */
export const pageStructure = async (page: Page): Promise<string> =>
  await page.evaluate(structureInPage, STRUCTURED_ELEMENTS)

/** What the anonymous visit handed on, which the signed-in visit is compared with. */
export interface EarlierVisit {
  /** The routes it reached, which count towards the route ceiling with the signed-in crawl's own. */
  readonly routes: readonly string[]
  /** The locations it handed its passes, with their structure. */
  readonly locations: readonly SweepLocation[]
}

export const NO_EARLIER_VISIT: EarlierVisit = { routes: [], locations: [] }

/**
 * What the anonymous visit recorded. A signed-in visit whose anonymous one recorded nothing compares itself with
 * nothing, so it misses nothing.
 * @param info the running test.
 * @returns the anonymous visit's routes and locations.
 */
export const earlierVisit = (info: TestInfo): EarlierVisit => {
  const report: DiscoveryReport | undefined = readJson(discoveryReportFile(info, 'anonymous'), isDiscoveryReport)
  const discovery: Discovery | undefined = readJson(discoveryFile(info, 'anonymous'), isDiscovery)
  return {
    routes: [...(report?.visitedRoutes ?? []), ...(report?.answeredRoutes ?? [])],
    locations: discovery?.locations ?? [],
  }
}

/** What the signed-in crawl tells its session apart with: a request context that carries no session. */
export interface SessionWatch {
  readonly withoutSession: APIRequestContext
}

// Without following a redirect, so a page that forwards a visitor to its sign-in page answers as forwarding.
const statusOf = async (context: APIRequestContext, route: string): Promise<number | undefined> => {
  try {
    const response: APIResponse<unknown> = await context.get(`${origin}${route}`, { maxRedirects: 0 })
    return response.status()
  } catch {
    return undefined
  }
}

const isSessionProbe = async (page: Page, route: string, watch: SessionWatch): Promise<boolean> => {
  const signedIn: number | undefined = await statusOf(page.request, route)
  const anonymous: number | undefined = await statusOf(watch.withoutSession, route)
  return signedIn !== undefined && anonymous !== undefined && opensOnlyWithSession(signedIn, anonymous)
}

/** The part of a crawl's state the watch reads and moves on. */
export interface WatchedCrawl {
  readonly queue: readonly unknown[]
  /** The routes that answered themselves, rather than only the page a temporary redirect showed. */
  readonly credited: ReadonlySet<string>
  readonly probe: string | undefined
  readonly endedAt: string | undefined
}

/**
 * Watch the session after a visit. The first route the session opens and a visitor without it cannot becomes
 * the probe; after every later visit the probe is asked again in the page's own session, and a visit after
 * which it no longer opens is the one that ended the session. The crawl stops there, because what it reached
 * afterwards it reached without signing in.
 * @param page the crawl's page, in the session.
 * @param state the crawl so far.
 * @param route the route just visited.
 * @param watch the request context without the session.
 * @returns the crawl with its probe found, or stopped where the session ended.
 */
export const guardedSession = async <State extends WatchedCrawl>(
  page: Page,
  state: State,
  route: string,
  watch: SessionWatch,
): Promise<State> => {
  if (state.probe === undefined) {
    return state.credited.has(route) && (await isSessionProbe(page, route, watch)) ? { ...state, probe: route } : state
  }
  const status: number | undefined = await statusOf(page.request, state.probe)
  return status !== undefined && isOwnAnswer(status) ? state : { ...state, endedAt: route, queue: [] }
}

/**
 * Run a crawl beside a request context without a session, which is disposed of however the crawl ends.
 * @param crawlWith the crawl, given the watch.
 * @returns what the crawl returned.
 */
export const withSessionWatch = async <Result>(
  crawlWith: (watch: SessionWatch) => Promise<Result>,
): Promise<Result> => {
  // Inside a test, a new request context inherits the test's options, the signed-in session among them, so the
  // context is handed an empty storage state of its own.
  const withoutSession: APIRequestContext = await request.newContext({ storageState: { cookies: [], origins: [] } })
  try {
    return await crawlWith({ withoutSession })
  } finally {
    await withoutSession.dispose()
  }
}
