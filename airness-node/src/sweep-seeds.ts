// The routes discovery requests because the route inventory declares them, rather than because a link led there.
//
// A crawl reaches what its pages link to and nothing else. A page that only a redirect or an email reaches, such
// as the page an identity provider returns to after sign-out or a confirmation opened from a message, is linked
// from no page at all, and the crawl alone would never see it. The inventory names every page the application
// declares, so once the links run out discovery asks for each inventory route that names one concrete address and
// no link reached. A route reached that way is discovered like a crawled one: every pass, every engine, the
// template sampling and the route ceiling apply to it alike. A pattern with a variable names a family of
// addresses, none of which the sweep could guess, so it stays with links and with the project's specifications.
import { type DeclaredRoute, normalisedRoute } from './route-map.js'
import { isAtRouteCeiling, isOwnAnswer, type Visitor } from './sweep-visitors.js'

/** What a seeded route names as the place it was found, where a crawled route names the page that linked it. */
export const INVENTORY_SOURCE: string = 'the route inventory'

/** A route the crawl will visit, with the page, sitemap or inventory it was found in. */
export interface Queued {
  readonly route: string
  /** The page or sitemap that linked it, for a broken link's report, or `INVENTORY_SOURCE`. */
  readonly from: string
}

/** The part of a crawl's state its queue reads and moves on. */
export interface QueuedCrawl {
  readonly queue: readonly Queued[]
  readonly seen: ReadonlySet<string>
  readonly dropped: ReadonlySet<string>
  /** The routes an earlier visit reached, which count towards the route ceiling with this crawl's own. */
  readonly earlier: readonly string[]
  /** The inventory routes queued with no link to them, each with what requesting it came to, once requested. */
  readonly seeded: ReadonlyMap<string, string | undefined>
}

/** An inventory route no link reached, which discovery requested by itself. */
export interface SeededRoute {
  readonly route: string
  /** What the request came to when it did not cover the route; absent when the route was swept. */
  readonly answer?: string
}

/**
 * Queue one route, unless it is queued already or the route ceiling is reached, which drops it instead.
 * @param state the crawl so far.
 * @param route the route, already confined to the swept surface.
 * @param from where it was found: the linking page, the sitemap, or `INVENTORY_SOURCE`.
 * @param ceiling the route ceiling, counting both visits' routes together.
 * @returns the crawl with the route queued or dropped.
 */
export const queuedRoute = <State extends QueuedCrawl>(
  state: State,
  route: string,
  from: string,
  ceiling: number,
): State => {
  if (state.seen.has(route) || state.dropped.has(route)) {
    return state
  }
  if (isAtRouteCeiling(state.seen, state.earlier, ceiling)) {
    return { ...state, dropped: new Set<string>([...state.dropped, route]) }
  }
  return {
    ...state,
    seen: new Set<string>([...state.seen, route]),
    queue: [...state.queue, { route, from }],
    ...(from === INVENTORY_SOURCE && {
      seeded: new Map<string, string | undefined>([...state.seeded, [route, undefined]]),
    }),
  }
}

/**
 * Queue every seed no link reached, after the crawl's own queue ran out.
 * @param state the crawl, its links followed.
 * @param seeds the inventory routes to request, as `inventorySeeds` chose them.
 * @param ceiling the route ceiling.
 * @returns the crawl with each new seed queued, or dropped at the ceiling.
 */
export const withSeeds = <State extends QueuedCrawl>(state: State, seeds: readonly string[], ceiling: number): State =>
  seeds.reduce((current: State, route: string): State => queuedRoute(current, route, INVENTORY_SOURCE, ceiling), state)

/**
 * The inventory routes discovery can request by themselves: each one naming a single concrete address outside
 * the skipped prefixes. A seed keeps a file extension, because the inventory declares it a page, which a link to
 * an address with an extension is not known to be.
 * @param declared the routes the inventory declares.
 * @param isSkipped whether the sweep skips an address.
 * @returns each concrete route once, in the inventory's order.
 */
export const inventorySeeds = (
  declared: readonly DeclaredRoute[],
  isSkipped: (route: string) => boolean,
): readonly string[] => [
  ...new Set<string>(
    declared
      .filter((route: DeclaredRoute): boolean => !route.isDynamic)
      .map((route: DeclaredRoute): string => normalisedRoute(route.route))
      .filter((route: string): boolean => !isSkipped(route)),
  ),
]

/**
 * What requesting a seed came to, when it did not cover the route.
 * @param status the final status of the navigation, or undefined when it did not answer.
 * @param isCredited whether the answer covers the requested address, as `coversItsRoute` decides.
 * @param landed where the navigation landed.
 * @returns nothing when the route was covered, otherwise the answer in words.
 */
export const seedAnswer = (status: number | undefined, isCredited: boolean, landed: string): string | undefined => {
  if (status === undefined) {
    return 'did not answer'
  }
  if (!isOwnAnswer(status)) {
    return `answered ${String(status)}`
  }
  return isCredited ? undefined : `was forwarded to ${landed}`
}

/**
 * Record what requesting a seed came to; a route the inventory did not seed is left alone.
 * @param state the crawl.
 * @param route the requested route.
 * @param answer what it came to, or nothing when it was covered.
 * @returns the crawl with the answer recorded.
 */
export const answeredSeed = <State extends QueuedCrawl>(
  state: State,
  route: string,
  answer: string | undefined,
): State =>
  state.seeded.has(route)
    ? { ...state, seeded: new Map<string, string | undefined>([...state.seeded, [route, answer]]) }
    : state

/**
 * The seeds of a finished crawl, as discovery reports them.
 * @param seeded the crawl's seeds and their answers.
 * @returns one entry per seed, in the order they were queued.
 */
export const seededRoutesOf = (seeded: ReadonlyMap<string, string | undefined>): readonly SeededRoute[] =>
  [...seeded].map(
    ([route, answer]: readonly [string, string | undefined]): SeededRoute =>
      answer === undefined ? { route } : { route, answer },
  )

/**
 * The coverage summary's lines for the seeds of one visit.
 * @param seeded the visit's seeds.
 * @returns one line per seed, naming what it answered when it was not swept.
 */
export const seededCoverageLines = (seeded: readonly SeededRoute[]): readonly string[] =>
  seeded.map((seed: SeededRoute): string =>
    seed.answer === undefined ? `  seeded: ${seed.route}` : `  seeded: ${seed.route} (${seed.answer})`,
  )

/** The seeds one visitor's discovery reported. */
interface VisitorSeeds {
  readonly visitor: Visitor
  readonly seeded: readonly SeededRoute[]
}

/**
 * What every visitor's request of each uncovered seed came to, for the finding that names the route.
 * @param visits each visitor's seeds.
 * @returns per route, one phrase per visitor whose request did not cover it.
 */
export const seedAnswersOf = (visits: readonly VisitorSeeds[]): ReadonlyMap<string, readonly string[]> =>
  visits.reduce((answers: ReadonlyMap<string, readonly string[]>, visit: VisitorSeeds) => {
    const phrases: readonly (readonly [string, string])[] = visit.seeded.flatMap(
      (seed: SeededRoute): readonly (readonly [string, string])[] =>
        seed.answer === undefined
          ? []
          : [[seed.route, visit.visitor === 'signed-in' ? `${seed.answer} signed in` : seed.answer]],
    )
    return phrases.reduce(
      (current: ReadonlyMap<string, readonly string[]>, [route, phrase]: readonly [string, string]) =>
        new Map<string, readonly string[]>([...current, [route, [...(current.get(route) ?? []), phrase]]]),
      answers,
    )
  }, new Map<string, readonly string[]>())
