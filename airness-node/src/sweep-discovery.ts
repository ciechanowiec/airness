// The sweep's discovery pass: what the later passes scan, read from the running application.
import { type APIResponse, expect, type Page, type Response, type TestInfo, test } from '@playwright/test'
import { declaredRoutes, isSkippedSweepRoute, MAX_SWEEP_ROUTES } from './a11y.js'
import {
  type AlternateLink,
  alternateHrefs,
  type DiscoveredStory,
  localAddress,
  onOrigin,
  type SitemapEntries,
  sitemapEntries,
  storiesOfIndex,
  storyLocation,
  storyUrl,
  templateSamples,
} from './browser-discovery.js'
import { type BrowserFinding, findingAt } from './browser-findings.js'
import { BROKEN_LINK_RULE } from './browser-health.js'
import { breakpointEdges } from './browser-viewports.js'
import { routeReason, storyReason } from './change-scope-policy.js'
import { writeJson } from './project.js'
import { projectSettings } from './project-settings.js'
import type { DeclaredRoute } from './route-map.js'
import { clearCompletedScans } from './scan-evidence.js'
import { engineOf } from './scanner.js'
import { settleForScan } from './settle.js'
import { type CrawledFragments, coversItsRoute, crawledFragments, headerFindings } from './sweep-answers.js'
import { discoverNotFound, type NotFoundDiscovery } from './sweep-not-found.js'
import {
  type Chosen,
  chosen,
  type NamedScope,
  namedScope,
  type ScopedFinds,
  scopeRecordOf,
  storyFile,
} from './sweep-scope.js'
import {
  type EarlierVisit,
  earlierVisit,
  guardedSession,
  NO_EARLIER_VISIT,
  pageStructure,
  type SessionWatch,
  withSessionWatch,
} from './sweep-session.js'
import {
  armBudget,
  type Budget,
  CONTROLS,
  type CssRule,
  DESKTOP,
  type Discovery,
  type DiscoveryReport,
  type DomDocument,
  type DomElement,
  discoveryFile,
  discoveryReportFile,
  loadLocation,
  origin,
  placeAt,
  preparePage,
  type SweepLocation,
  visitedAs,
  WHOLE_PASS,
} from './sweep-shared.js'
import { settledUnrequested } from './sweep-unrequested.js'
import {
  distinctLocations,
  isAtRouteCeiling,
  locationsToRevisit,
  sessionProblems,
  structureOf,
  type Visitor,
  visitorOfTags,
} from './sweep-visitors.js'

// The browser surface the page functions below touch, declared for the reason `settle.ts` records.
declare const document: DomDocument

const SEED_ROUTE: string = '/'

const STATIC_ASSET: RegExp = /\.[a-z\d]+$/iu

const parseUrl = (href: string): URL | undefined => {
  try {
    return new URL(href, origin)
  } catch {
    return undefined
  }
}

const toRoute = (href: string): string | undefined => {
  const url: URL | undefined = parseUrl(href)
  if (url?.origin !== origin) {
    return undefined
  }
  return isSkippedSweepRoute(url.pathname) || STATIC_ASSET.test(url.pathname) ? undefined : url.pathname
}

interface PageFacts {
  /** The width conditions of the media rules that style an element of this page. */
  readonly mediaTexts: readonly string[]
  /** The page's `<link rel="canonical">` href, or empty. */
  readonly canonical: string
  readonly hasDarkMedia: boolean
  readonly controls: number
  readonly hrefs: readonly string[]
  readonly alternates: readonly AlternateLink[]
  /** The `hx-get` values of the page, the fragments htmx would request from it. */
  readonly fragments: readonly string[]
}

// Read through the CSSOM, so a breakpoint in any stylesheet the page loaded counts, nested at-rules
// included. A stylesheet from another origin cannot be read and has already been blocked anyway.
const pageFactsInPage = (controls: string): PageFacts => {
  const rulesOf = (sheet: { readonly cssRules?: ArrayLike<CssRule> }): readonly CssRule[] => {
    try {
      return Array.from(sheet.cssRules ?? []).flatMap((rule: CssRule): readonly CssRule[] => [rule, ...rulesOf(rule)])
    } catch {
      return []
    }
  }
  const rules: readonly CssRule[] = Array.from(document.styleSheets).flatMap(
    (sheet: { readonly cssRules?: ArrayLike<CssRule> }): readonly CssRule[] => rulesOf(sheet),
  )
  // A media rule moves this page only when a rule inside it styles an element here. A stylesheet
  // shipping every breakpoint of a utility framework would otherwise add edges no element responds
  // to. State and pseudo-element parts are removed before matching, which can only widen the match.
  const isStyling = (rule: CssRule): boolean => {
    const selector: string = (rule.selectorText ?? '').replaceAll(/(?<!\\)::?[a-z-]+(?:\([^)]*\))?/giu, '').trim()
    if (rule.selectorText === undefined) {
      return false
    }
    try {
      return document.querySelector(selector === '' ? '*' : selector) !== null
    } catch {
      return true
    }
  }
  const mediaRules: readonly CssRule[] = rules.filter((rule: CssRule): boolean => rule.media !== undefined)
  const mediaTexts: readonly string[] = mediaRules
    .filter((rule: CssRule): boolean => rulesOf(rule).some((inner: CssRule): boolean => isStyling(inner)))
    .flatMap((rule: CssRule): readonly string[] => [rule.media?.mediaText ?? ''])
  return {
    mediaTexts: [...new Set<string>(mediaTexts)],
    canonical: document.querySelector('link[rel="canonical"]')?.getAttribute('href') ?? '',
    hasDarkMedia: mediaRules.some((rule: CssRule): boolean =>
      (rule.media?.mediaText ?? '').includes('prefers-color-scheme'),
    ),
    controls: document.querySelectorAll(controls).length,
    hrefs: Array.from(
      document.querySelectorAll('a[href]'),
      (anchor: DomElement): string => anchor.getAttribute('href') ?? '',
    ),
    alternates: Array.from(
      document.querySelectorAll('link[rel="alternate"][hreflang]'),
      (link: DomElement): AlternateLink => ({
        hreflang: link.getAttribute('hreflang') ?? '',
        href: link.getAttribute('href') ?? '',
      }),
    ),
    fragments: Array.from(
      document.querySelectorAll<HTMLElement>('[hx-get], [data-hx-get]'),
      (element: HTMLElement): string => element.getAttribute('hx-get') ?? element.dataset['hxGet'] ?? '',
    ),
  }
}

interface Queued {
  readonly route: string
  /** The page or sitemap that linked it, for a broken link's report. */
  readonly from: string
}

interface CrawlState {
  readonly queue: readonly Queued[]
  readonly seen: ReadonlySet<string>
  readonly dropped: ReadonlySet<string>
  readonly answered: ReadonlySet<string>
  /** The answered addresses that cover themselves, rather than only the page a temporary redirect showed. */
  readonly credited: ReadonlySet<string>
  /** The fragment addresses already requested, so each is asked for once. */
  readonly requestedFragments: ReadonlySet<string>
  /** The paths of the fragments that rendered. */
  readonly fragments: ReadonlySet<string>
  readonly reachable: readonly SweepLocation[]
  readonly findings: readonly BrowserFinding[]
  /** The routes an earlier visit reached, which count towards the route ceiling with this crawl's own. */
  readonly earlier: readonly string[]
  /** The route that proves the signed-in session, once the signed-in crawl found one. */
  readonly probe: string | undefined
  /** The route whose visit ended the signed-in session, when one did. */
  readonly endedAt: string | undefined
}

const enqueue = (state: CrawlState, hrefs: readonly string[], from: string): CrawlState =>
  hrefs.reduce((current: CrawlState, href: string): CrawlState => {
    const route: string | undefined = toRoute(href)
    if (route === undefined || current.seen.has(route) || current.dropped.has(route)) {
      return current
    }
    return isAtRouteCeiling(current.seen, current.earlier, MAX_SWEEP_ROUTES)
      ? { ...current, dropped: new Set<string>([...current.dropped, route]) }
      : {
          ...current,
          seen: new Set<string>([...current.seen, route]),
          queue: [...current.queue, { route, from }],
        }
  }, state)

const linkFinding = (queued: Queued, page: Page, reason: string): BrowserFinding =>
  findingAt(
    {
      location: queued.from,
      state: 'discovery',
      engine: engineOf(page),
      viewport: undefined,
    },
    BROKEN_LINK_RULE,
    `link to ${queued.route} ${reason}`,
  )

const visited = async (page: Page, url: string): Promise<Response | null> => {
  try {
    return await page.goto(url)
  } catch {
    return null
  }
}

const routeLocation = (url: string, facts: PageFacts, structure: string): SweepLocation => {
  return {
    location: new URL(url).pathname,
    url,
    isStory: false,
    edges: breakpointEdges(facts.mediaTexts),
    hasDarkMedia: facts.hasDarkMedia,
    controls: facts.controls,
    structure: structureOf(structure),
  }
}

// One route of the crawl: visited, judged, and its links queued.
const crawlRoute = async (page: Page, next: CrawlState, queued: Queued): Promise<CrawlState> => {
  const response: Response | null = await visited(page, `${origin}${queued.route}`)
  if (response?.ok() !== true) {
    const reason: string = response === null ? 'did not answer' : `answered ${String(response.status())}`
    return queued.route === SEED_ROUTE
      ? next
      : {
          ...next,
          findings: [...next.findings, linkFinding(queued, page, reason)],
        }
  }
  const answered: CrawlState = {
    ...next,
    answered: new Set<string>([...next.answered, queued.route]),
    credited: (await coversItsRoute(response, queued.route, page))
      ? new Set<string>([...next.credited, queued.route])
      : next.credited,
  }
  if (toRoute(page.url()) === undefined) {
    return answered
  }
  const settled: CrawlState = await settledUnrequested(page, answered, queued.route, response.url())
  const facts: PageFacts = await page.evaluate(pageFactsInPage, CONTROLS)
  const location: SweepLocation = routeLocation(page.url(), facts, await pageStructure(page))
  const fragments: CrawledFragments = await crawledFragments(page, settled, facts.fragments, queued.route)
  return enqueue(
    {
      ...settled,
      ...fragments,
      reachable: [...settled.reachable, location],
      findings: [...settled.findings, ...fragments.findings, ...(await headerFindings(response, queued.route, page))],
    },
    [...facts.hrefs, ...alternateHrefs(facts.alternates)].flatMap((href: string): readonly string[] => {
      const address: string | undefined = localAddress(href, page.url(), facts.canonical, origin)
      return address === undefined ? [] : [address]
    }),
    queued.route,
  )
}

const crawl = async (page: Page, state: CrawlState, watch: SessionWatch | undefined): Promise<CrawlState> => {
  const [queued, ...rest] = state.queue
  if (queued === undefined) {
    return state
  }
  const crawled: CrawlState = await crawlRoute(page, { ...state, queue: rest }, queued)
  return await crawl(
    page,
    watch === undefined ? crawled : await guardedSession(page, crawled, queued.route, watch),
    watch,
  )
}

const crawlAs = async (page: Page, visitor: Visitor, start: CrawlState): Promise<CrawlState> =>
  visitor === 'anonymous'
    ? await crawl(page, start, undefined)
    : await withSessionWatch(async (watch: SessionWatch): Promise<CrawlState> => await crawl(page, start, watch))

const MAX_SITEMAPS: number = 50

const fetched = async (page: Page, url: string): Promise<APIResponse<unknown> | undefined> => {
  try {
    return await page.request.get(url)
  } catch {
    return undefined
  }
}

const NO_ENTRIES: SitemapEntries = { pages: [], sitemaps: [] }

const sitemapAt = async (page: Page, url: string, read: number): Promise<SitemapEntries> => {
  if (read >= MAX_SITEMAPS || parseUrl(url)?.origin !== origin) {
    return NO_ENTRIES
  }
  const response: APIResponse<unknown> | undefined = await fetched(page, url)
  return response?.ok() === true ? sitemapEntries(await response.text()) : NO_ENTRIES
}

const sitemapPages = async (page: Page, urls: readonly string[], read: number = 0): Promise<readonly string[]> => {
  const [url, ...rest] = urls
  if (url === undefined) {
    return []
  }
  const entries: SitemapEntries = await sitemapAt(page, url, read)
  const local = (locations: readonly string[]): readonly string[] =>
    locations.flatMap((location: string): readonly string[] => {
      const address: string | undefined = onOrigin(location, origin)
      return address === undefined ? [] : [address]
    })
  return [...local(entries.pages), ...(await sitemapPages(page, [...rest, ...local(entries.sitemaps)], read + 1))]
}

const storiesOf = async (page: Page): Promise<readonly DiscoveredStory[]> => {
  const lists: readonly (readonly DiscoveredStory[])[] = await Promise.all(
    projectSettings.auxiliaryServers.map(async (server): Promise<readonly DiscoveredStory[]> => {
      const serverOrigin: string = new URL(server.url).origin
      const response: APIResponse<unknown> | undefined = await fetched(page, `${serverOrigin}/index.json`)
      if (response?.ok() !== true) {
        return []
      }
      try {
        return storiesOfIndex(await response.json(), serverOrigin) ?? []
      } catch {
        return []
      }
    }),
  )
  return lists.flat()
}

const readStoryLocation = async (page: Page, story: DiscoveredStory): Promise<SweepLocation> => {
  const location: SweepLocation = {
    location: storyLocation(story),
    url: storyUrl(story),
    isStory: true,
    edges: [],
    hasDarkMedia: false,
    controls: 0,
  }
  await loadLocation(page, location, DESKTOP, placeAt(page, location, undefined, 'discovery'))
  await settleForScan(page)
  const facts: PageFacts = await page.evaluate(pageFactsInPage, CONTROLS)
  return {
    ...location,
    edges: breakpointEdges(facts.mediaTexts),
    hasDarkMedia: facts.hasDarkMedia,
    controls: facts.controls,
  }
}

// Each story is read on a page of its own, so a story's state cannot reach the next one.
const storyLocationOf = async (page: Page, story: DiscoveredStory): Promise<SweepLocation> => {
  const storyPage: Page = await page.context().newPage()
  try {
    await preparePage(storyPage)
    return await readStoryLocation(storyPage, story)
  } finally {
    await storyPage.close()
  }
}

// Stories are read this many at a time. Each read is one load, independent of the others; the route
// crawl stays sequential, because the routes it visits next depend on the links it has read.
const STORY_CONCURRENCY: number = 4

const storyLocations = async (page: Page, stories: readonly DiscoveredStory[]): Promise<readonly SweepLocation[]> => {
  if (stories.length === 0) {
    return []
  }
  const batch: readonly SweepLocation[] = await Promise.all(
    stories
      .slice(0, STORY_CONCURRENCY)
      .map(async (story: DiscoveredStory): Promise<SweepLocation> => await storyLocationOf(page, story)),
  )
  return [...batch, ...(await storyLocations(page, stories.slice(STORY_CONCURRENCY)))]
}

// The seed route first, then every page the sitemaps list.
const crawlStart = (fromSitemap: readonly string[], earlier: readonly string[]): CrawlState =>
  enqueue(
    {
      queue: [{ route: SEED_ROUTE, from: SEED_ROUTE }],
      seen: new Set<string>([SEED_ROUTE]),
      dropped: new Set<string>(),
      answered: new Set<string>(),
      credited: new Set<string>(),
      requestedFragments: new Set<string>(),
      fragments: new Set<string>(),
      reachable: [],
      findings: [],
      earlier,
      probe: undefined,
      endedAt: undefined,
    },
    fromSitemap,
    'sitemap.xml',
  )

const wasRecorded = (file: string, value: unknown): boolean => {
  try {
    writeJson(file, value)
    return true
  } catch {
    return false
  }
}

const problemsOf = (crawled: CrawlState): readonly string[] => [
  ...(crawled.answered.has(SEED_ROUTE) ? [] : [`the crawl could not start: ${SEED_ROUTE} did not answer`]),
  ...(crawled.dropped.size === 0
    ? []
    : [
        `the sweep reached the ${String(MAX_SWEEP_ROUTES)}-route cap; raise the harness limit only after ` +
          'measuring the resulting run time',
      ]),
]

const discoveryOf = async (page: Page, finds: ScopedFinds, declared: readonly DeclaredRoute[]): Promise<Discovery> => {
  const routes: readonly string[] = finds.routes.map((route: Chosen<SweepLocation>): string => route.item.location)
  const stories: readonly DiscoveredStory[] = finds.stories.map(
    (story: Chosen<DiscoveredStory>): DiscoveredStory => story.item,
  )
  return {
    routes,
    stories,
    locations: [
      ...finds.routes.map((route: Chosen<SweepLocation>): SweepLocation => route.item),
      ...(await storyLocations(page, stories)),
    ],
    samples: templateSamples(routes, declared),
  }
}

const NO_NOT_FOUND: NotFoundDiscovery = { locations: [], problems: [] }

/** What one visitor's crawl found, before the scope narrows it. */
interface VisitFinds {
  readonly crawled: CrawlState
  /** Every location the crawl reached, each once. */
  readonly reachable: readonly SweepLocation[]
  /** The locations the passes visit: every one for the anonymous visitor, what is new signed in otherwise. */
  readonly revisited: readonly SweepLocation[]
  readonly missing: NotFoundDiscovery
  readonly stories: readonly DiscoveredStory[]
}

// Stories and not-found states look the same to every visitor, so only the anonymous visit reads them.
const visitFinds = async (page: Page, visitor: Visitor, info: TestInfo): Promise<VisitFinds> => {
  const earlier: EarlierVisit = visitor === 'anonymous' ? NO_EARLIER_VISIT : earlierVisit(info)
  const fromSitemap: readonly string[] = await sitemapPages(page, [`${origin}/sitemap.xml`])
  const crawled: CrawlState = await crawlAs(page, visitor, crawlStart(fromSitemap, earlier.routes))
  const reachable: readonly SweepLocation[] = distinctLocations(crawled.reachable)
  if (visitor === 'signed-in') {
    const revisited: readonly SweepLocation[] = locationsToRevisit(reachable, earlier.locations)
    return { crawled, reachable, revisited, missing: NO_NOT_FOUND, stories: [] }
  }
  const missing: NotFoundDiscovery = await discoverNotFound(
    page,
    reachable.map((location: SweepLocation): string => location.location),
  )
  return { crawled, reachable, revisited: reachable, missing, stories: await storiesOf(page) }
}

const visitProblems = (visitor: Visitor, { crawled, revisited }: VisitFinds): readonly string[] =>
  visitor === 'anonymous' ? [] : sessionProblems({ ...crawled, revisited: revisited.length })

/**
 * Discover every page the sweep scans, and record it with whatever made the discovery incomplete.
 *
 * Routes come from a link crawl from `/`, every same-origin `hreflang` alternate, and `sitemap.xml`;
 * stories from each auxiliary server that answers `/index.json`; breakpoints and colour schemes from
 * each page's stylesheets. A crawled internal link that does not answer is a broken link. Under a
 * `delta` run's change scope the crawl still reaches and checks every route, and the passes are handed
 * only the routes and stories the change reaches, each with the reason it is swept. Every pass depends
 * on this test, so it fails only when it could not write its record; the summary judges the findings
 * and problems it recorded.
 *
 * The signed-in visit crawls again in the session the sign-in setup left, after the anonymous visit, and
 * hands its passes only the pages that are new to it: those the anonymous visit did not reach and those
 * whose structure signing in changed. It watches its session as it goes, and stops at a visit that ends it.
 * @param page the test's page.
 */
export const expectDiscoveredSite = async (page: Page): Promise<void> => {
  const info: TestInfo = test.info()
  const visitor: Visitor = visitorOfTags(info.tags)
  if (visitor === 'anonymous') {
    clearCompletedScans(info.project.outputDir)
  }
  const budget: Budget = armBudget(page, 'discovery', WHOLE_PASS, MAX_SWEEP_ROUTES)
  await preparePage(page)
  const { scope, problem }: NamedScope = namedScope()
  const declared: readonly DeclaredRoute[] = declaredRoutes()
  const found: VisitFinds = await visitFinds(page, visitor, info)
  const { crawled, missing }: VisitFinds = found
  const finds: ScopedFinds = {
    routes: chosen([...found.revisited, ...missing.locations], (location: SweepLocation): string | undefined =>
      scope === undefined ? '' : routeReason(scope, declared, location.location),
    ),
    stories: chosen(found.stories, (story: DiscoveredStory): string | undefined =>
      scope === undefined ? '' : storyReason(scope, storyFile(story)),
    ),
    everyRoute: found.revisited.length + missing.locations.length,
    everyStory: found.stories.length,
  }
  const discovery: Discovery = await discoveryOf(page, finds, declared)
  const report: DiscoveryReport = {
    visitedRoutes: found.reachable.map((location: SweepLocation): string => location.location),
    answeredRoutes: [...crawled.credited],
    fragmentRoutes: [...crawled.fragments],
    findings: visitedAs(crawled.findings, visitor),
    problems: [
      ...missing.problems,
      ...problemsOf(crawled),
      ...(problem === undefined ? [] : [problem]),
      ...visitProblems(visitor, found),
    ],
    places: crawled.answered.size + discovery.stories.length,
    locations: discovery.locations.map((location: SweepLocation): string => location.location),
    elapsedMs: Math.round(performance.now() - budget.startedAt),
    scope: scopeRecordOf(scope, finds),
    ...(crawled.probe !== undefined && crawled.endedAt === undefined && { probeRoute: crawled.probe }),
  }
  const isRecorded: boolean =
    wasRecorded(discoveryReportFile(info), report) && wasRecorded(discoveryFile(info), discovery)
  expect(isRecorded, 'discovery could not write its record to the test output directory').toBe(true)
}
