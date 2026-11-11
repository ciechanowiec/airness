// The sweep's scrolling pass: a touch phone, motion allowed, crossed from top to bottom and back by wheel
// gestures. Every other pass loads a page, settles it and measures it where it stands; none moves the
// document. A page that takes the scroll over, snapping the reader to a section after they stop or running
// on far past a small gesture, and a page whose text waits for an observer that never reports it, both look
// complete at rest and fail exactly while a reader moves through them. The verdicts are in `scroll-policy`.
//
// WHY A WHEEL. Playwright offers no touch scroll every engine answers, and a scripted `scrollTo` is the page
// scrolling itself, which no page intercepts. A wheel gesture is real input the browser scrolls by, so a page
// that intercepts scrolling intercepts it too. The context still emulates a touch phone - a coarse pointer
// without hover - so a page that keeps its scroll native on touch screens is judged on that path. The pass
// reports wheel gestures and never claims to have swiped.
//
// WHY ONE ROUTE PER TEMPLATE. How a page scrolls belongs to its layout and its scripts, which every route
// of a template shares, so the pass samples as the engine pass does: one route per template and one story
// per component. Each crossing waits for the document to rest after every gesture, which costs about a
// second a gesture, and a crawled site of a thousand product pages would otherwise pay it a thousand times.
//
// WHY THE PAGE IS SETTLED FIRST. The settle loads every lazy image before the first gesture, which a reader
// would not see, and that is deliberate: an image arriving above the viewport while the reader scrolls
// moves the scroll position through scroll anchoring, which is the `media-space` defect and not the page
// taking the scroll over. Settling first keeps the two apart.
import { type Page, test } from '@playwright/test'
import { type BrowserFinding, type FindingPlace, findingAt } from './browser-findings.js'
import { PHONE } from './browser-viewports.js'
import { type TargetFacts, targetFacts } from './layout.js'
import { settleFindings } from './scanner.js'
import {
  hasMoved,
  hijackMessage,
  REST_CEILING_MS,
  REST_QUIET_MS,
  REVEAL_BAND,
  REVEAL_MS,
  SCROLLING_RULES,
  type ScrollStep,
  scrollDeltas,
  unrevealedMessage,
} from './scroll-policy.js'
import { carried } from './sweep-interaction.js'
import { reducedMotionVisit } from './sweep-reduced-motion.js'
import {
  armBudget,
  type Budget,
  type ComputedStyleOf,
  type Discovery,
  type DomDocument,
  type DomElement,
  EMPTY_VISIT,
  finishPass,
  healthOf,
  isChromium,
  loadLocation,
  merged,
  placeAt,
  preparePage,
  readDiscovery,
  type Shard,
  type ShardVisits,
  type SweepLocation,
  sampledLocations,
  shardLocations,
  type Visit,
  visitAll,
} from './sweep-shared.js'

declare const document: DomDocument
declare const getComputedStyle: ComputedStyleOf
declare const scrollY: number
declare const setTimeout: (callback: () => void, delayMs: number) => unknown

const HIJACK_RULE: string = 'scroll-hijack'

const UNREVEALED_RULE: string = 'unrevealed-content'

/** The attribute that names, for one reading, each element transparent in view. */
const UNREVEALED_ATTRIBUTE: string = 'data-airness-unrevealed'

/** The attribute that marks text once seen visible in view, which is never judged again on that load. */
const SEEN_ATTRIBUTE: string = 'data-airness-seen'

/** How often the page reads the scroll position while it waits for the document to rest. */
const REST_POLL_MS: number = 16

/** The least change of scroll position that counts as the document moving, in CSS pixels. */
const MOVEMENT_PX: number = 0.5

/** How often the page reads the opacity of text it waits to see revealed. */
const REVEAL_POLL_MS: number = 50

/** The document's own scroll range, as the page reports it. */
interface DocumentScroll {
  readonly scrollable: number
  readonly viewportHeight: number
}

/** A point in the viewport, in CSS pixels. */
interface WheelPoint {
  readonly x: number
  readonly y: number
}

/** Where a point sits in the viewport, as shares of its width and height. */
interface ViewportShare {
  readonly across: number
  readonly down: number
}

const pointAt = (share: ViewportShare): WheelPoint => ({
  x: Math.round(PHONE.width * share.across),
  y: Math.round(PHONE.height * share.down),
})

/** The middle of the viewport, where a gesture is aimed unless something there answers the wheel itself. */
const CENTRE: WheelPoint = pointAt({ across: 0.5, down: 0.5 })

/** The points a gesture may be aimed at, in the order they are tried. */
const WHEEL_POINTS: readonly WheelPoint[] = [
  CENTRE,
  ...[
    { across: 0.5, down: 0.25 },
    { across: 0.5, down: 0.75 },
    { across: 0.1, down: 0.5 },
    { across: 0.9, down: 0.5 },
  ].map((share: ViewportShare): WheelPoint => pointAt(share)),
]

/** What the page read while it waited for the document to rest; `rest` is null when it never did. */
interface RestReading {
  readonly rest: number | null
  readonly travelled: number
  readonly maximum: number
}

const documentScrollInPage = (): DocumentScroll => {
  const root: DomElement = document.documentElement
  return { scrollable: root.scrollHeight - root.clientHeight, viewportHeight: root.clientHeight }
}

const scrollPositionInPage = (): number => scrollY

// A wheel over an inner scroller, an embedded frame or a map that answers the wheel itself never reaches
// the document. The gesture is aimed at the first of the points none of those covers, read again before
// each gesture because scrolling changes what lies beneath.
const freePointInPage = (points: readonly WheelPoint[]): number => {
  const root: DomElement = document.documentElement
  const isCapturingWheel = (element: DomElement | null): boolean => {
    if (element === null || element === root || element === document.body) {
      return false
    }
    const style: CSSStyleDeclaration = getComputedStyle(element)
    const isScroller: boolean =
      ['auto', 'scroll'].includes(style.overflowY) && element.scrollHeight > element.clientHeight
    return (
      isScroller || ['IFRAME', 'EMBED', 'OBJECT'].includes(element.tagName) || isCapturingWheel(element.parentElement)
    )
  }
  return points.findIndex(
    (point: WheelPoint): boolean => !isCapturingWheel(document.elementFromPoint(point.x, point.y)),
  )
}

// Reads the scroll position every few milliseconds until it has held for the quiet period, or the ceiling
// passes. Recursive rather than a loop, as the settle is: each step awaits a timer, so what grows is time
// rather than stack. A timer rather than an animation frame, so a page that stops drawing cannot stall it.
const restInPage = async ([from, quietMs, ceilingMs, pollMs, movementPx]: readonly [
  number,
  number,
  number,
  number,
  number,
]): Promise<RestReading> => {
  const root: DomElement = document.documentElement
  const startedAt: number = performance.now()
  const watch = async (last: number, since: number, travelled: number): Promise<RestReading> => {
    await new Promise<void>((resolve: () => void): void => {
      setTimeout(resolve, pollMs)
    })
    const now: number = performance.now()
    const current: number = scrollY
    const farthest: number = Math.max(travelled, Math.abs(current - from))
    const maximum: number = root.scrollHeight - root.clientHeight
    const hasChanged: boolean = Math.abs(current - last) >= movementPx
    if (!hasChanged && now - since >= quietMs) {
      return { rest: current, travelled: farthest, maximum }
    }
    if (now - startedAt >= ceilingMs) {
      return { rest: null, travelled: farthest, maximum }
    }
    return await watch(current, hasChanged ? now : since, farthest)
  }
  return await watch(scrollY, startedAt, Math.abs(scrollY - from))
}

// Drops the marks an earlier reading left, so each reading names only what it found.
const clearMarksInPage = (attribute: string): void => {
  const marked: readonly DomElement[] = Array.from(document.querySelectorAll(`[${CSS.escape(attribute)}]`))
  for (const element of marked) {
    element.removeAttribute(attribute)
  }
}

// The text a reader came to read that is in view and fully transparent, each marked with the attribute.
// Only headings, paragraphs, quotations and captions with text of their own are judged: a menu, a tooltip
// or a hover label is built from links, list items and spans that are transparent until they are used,
// which is their design. So is anything hidden from assistive technology, inert, inside a tooltip or a
// carousel slide, or pinned to the viewport the way an overlay or a sticky header is. Opacity alone decides
// it, because every reveal library fades with it; a `visibility: hidden` element is one a page keeps for
// its size as often as one it means to show. The observer measures what is in view after every ancestor's
// clipping, so a slide scrolled sideways out of its track is not in view.
//
// Text is judged only once it starts in the upper part of the view, the band `REVEAL_BAND` names: a reveal
// that waits until its block is a fifth or half of the way up the viewport is a design, and text resting just
// above the bottom edge has not reached it. Text once seen visible is never judged again, so a block that
// fades out as it leaves, having been read, is not mistaken for one that never arrived.
const markTransparentInPage = async ([attribute, seen, band]: readonly [string, string, number]): Promise<number> => {
  const isTransparent = (element: DomElement | null): boolean =>
    element !== null && (Number(getComputedStyle(element).opacity) === 0 || isTransparent(element.parentElement))
  const isPinned = (element: DomElement | null): boolean =>
    element !== null &&
    (['fixed', 'sticky'].includes(getComputedStyle(element).position) || isPinned(element.parentElement))
  const hasOwnText = (element: DomElement): boolean =>
    Array.from(element.childNodes).some(
      (child: ChildNode): boolean => child.nodeType === Node.TEXT_NODE && (child.textContent ?? '').trim() !== '',
    )
  const isHiddenByDesign = (element: DomElement): boolean =>
    element.closest('[aria-hidden="true"], [inert], [role="tooltip"], [aria-roledescription="slide"]') !== null ||
    isPinned(element)
  const candidates: readonly DomElement[] = Array.from(
    document.querySelectorAll('h1, h2, h3, h4, h5, h6, p, blockquote, figcaption'),
  ).filter(
    (element: DomElement): boolean =>
      !element.hasAttribute(seen) && hasOwnText(element) && element.checkVisibility() && !isHiddenByDesign(element),
  )
  // An observer given nothing to observe never calls back, so a page whose text has all been seen asks none.
  const inView: readonly IntersectionObserverEntry[] = await new Promise<readonly IntersectionObserverEntry[]>(
    (resolve: (found: readonly IntersectionObserverEntry[]) => void): void => {
      const observer: IntersectionObserver = new IntersectionObserver(
        (found: readonly IntersectionObserverEntry[]): void => {
          observer.disconnect()
          resolve(found.filter(({ intersectionRect }): boolean => intersectionRect.width * intersectionRect.height > 1))
        },
      )
      for (const candidate of candidates) {
        observer.observe(candidate)
      }
      if (candidates.length === 0) {
        resolve([])
      }
    },
  )
  for (const entry of inView) {
    entry.target.toggleAttribute(seen, !isTransparent(entry.target))
  }
  const unrevealed: readonly IntersectionObserverEntry[] = inView.filter(
    (entry: IntersectionObserverEntry): boolean =>
      isTransparent(entry.target) && entry.intersectionRect.top <= document.documentElement.clientHeight * band,
  )
  for (const [index, entry] of unrevealed.entries()) {
    entry.target.setAttribute(attribute, String(index))
  }
  return unrevealed.length
}

// Waits for the marked text to turn visible, dropping the mark from each that does, until none is left or
// the bound passes; answers the marks still transparent then.
const unrevealedInPage = async ([attribute, seen, revealMs, pollMs]: readonly [
  string,
  string,
  number,
  number,
]): Promise<readonly string[]> => {
  const isTransparent = (element: DomElement | null): boolean =>
    element !== null && (Number(getComputedStyle(element).opacity) === 0 || isTransparent(element.parentElement))
  const deadline: number = performance.now() + revealMs
  const stillHidden = async (): Promise<readonly DomElement[]> => {
    const marked: readonly DomElement[] = Array.from(document.querySelectorAll(`[${CSS.escape(attribute)}]`))
    const hidden: readonly DomElement[] = marked.filter((element: DomElement): boolean => {
      const isHidden: boolean = isTransparent(element)
      if (!isHidden) {
        element.removeAttribute(attribute)
        element.setAttribute(seen, '')
      }
      return isHidden
    })
    if (hidden.length === 0 || performance.now() >= deadline) {
      return hidden
    }
    await new Promise<void>((resolve: () => void): void => {
      setTimeout(resolve, pollMs)
    })
    return await stillHidden()
  }
  const hidden: readonly DomElement[] = await stillHidden()
  return hidden.map((element: DomElement): string => element.getAttribute(attribute) ?? '')
}

const unrevealedVisit = async (page: Page, place: FindingPlace): Promise<Visit> => {
  await page.evaluate(clearMarksInPage, UNREVEALED_ATTRIBUTE)
  const marked: number = await page.evaluate(markTransparentInPage, [
    UNREVEALED_ATTRIBUTE,
    SEEN_ATTRIBUTE,
    REVEAL_BAND,
  ] as const)
  if (marked === 0) {
    return EMPTY_VISIT
  }
  const hidden: readonly string[] = await page.evaluate(unrevealedInPage, [
    UNREVEALED_ATTRIBUTE,
    SEEN_ATTRIBUTE,
    REVEAL_MS,
    REVEAL_POLL_MS,
  ] as const)
  const visits: readonly Visit[] = await Promise.all(
    hidden.map(async (mark: string): Promise<Visit> => {
      const facts: TargetFacts | undefined = await targetFacts(page, `[${UNREVEALED_ATTRIBUTE}="${mark}"]`)
      return carried(findingAt(place, UNREVEALED_RULE, unrevealedMessage(facts?.label ?? 'text')), facts)
    }),
  )
  return merged(visits)
}

/** What one gesture found, and whether it moved the document. */
interface StepVisit {
  readonly visit: Visit
  readonly hasMoved: boolean
  readonly isHijacked: boolean
}

const gestureStep = async (page: Page, place: FindingPlace, delta: number): Promise<StepVisit> => {
  const free: number = await page.evaluate(freePointInPage, WHEEL_POINTS)
  const point: WheelPoint = WHEEL_POINTS[free] ?? CENTRE
  await page.mouse.move(point.x, point.y)
  const from: number = await page.evaluate(scrollPositionInPage)
  await page.mouse.wheel(0, delta)
  const reading: RestReading = await page.evaluate(restInPage, [
    from,
    REST_QUIET_MS,
    REST_CEILING_MS,
    REST_POLL_MS,
    MOVEMENT_PX,
  ] as const)
  const step: ScrollStep = {
    from,
    delta,
    rest: reading.rest ?? undefined,
    travelled: reading.travelled,
    maximum: reading.maximum,
  }
  const message: string | undefined = hijackMessage(step)
  const hijack: readonly BrowserFinding[] = message === undefined ? [] : [findingAt(place, HIJACK_RULE, message)]
  return {
    visit: merged([{ ...EMPTY_VISIT, findings: hijack }, await unrevealedVisit(page, place)]),
    hasMoved: hasMoved(step),
    isHijacked: message !== undefined,
  }
}

// Gesture by gesture, stopping at the first that the page takes over: the document is then wherever the
// page put it, so every later gesture would report the same defect from somewhere else.
const crossing = async (page: Page, place: FindingPlace, deltas: readonly number[]): Promise<readonly StepVisit[]> => {
  const [delta, ...rest]: readonly number[] = deltas
  if (delta === undefined) {
    return []
  }
  const step: StepVisit = await gestureStep(page, place, delta)
  return step.isHijacked ? [step] : [step, ...(await crossing(page, place, rest))]
}

const scrollVisit = async (page: Page, location: SweepLocation): Promise<Visit> => {
  const place: FindingPlace = placeAt(page, location, PHONE, 'scrolling')
  const loaded: readonly BrowserFinding[] = await loadLocation(page, location, PHONE, {
    ...place,
    motion: 'no-preference',
  })
  const unsettled: readonly BrowserFinding[] = await settleFindings(page, place)
  const start: DocumentScroll = await page.evaluate(documentScrollInPage)
  const opening: Visit = await unrevealedVisit(page, place)
  const steps: readonly StepVisit[] = await crossing(page, place, scrollDeltas(start.scrollable, start.viewportHeight))
  const isUnmoved: boolean = steps.length > 0 && !steps.some((step: StepVisit): boolean => step.hasMoved)
  return merged([
    {
      ...EMPTY_VISIT,
      findings: [...loaded, ...unsettled],
      notes: isUnmoved
        ? [`${location.location}: no wheel gesture moved the document, so how it scrolls went unjudged`]
        : [],
    },
    opening,
    ...steps.map((step: StepVisit): Visit => step.visit),
    await healthOf(page, place),
    await reducedMotionVisit(page, location),
  ])
}

/**
 * Cross one shard of one route per template and one story per component from top to bottom and back on a
 * touch phone with normal motion, and judge where the document rests after each wheel gesture and whether
 * the text it brings into view becomes visible; then load each again with reduced motion and nothing held
 * still, and judge whether scrolling or opening a disclosure still sets content moving.
 * @param page the test's page, in a context that emulates a touch phone.
 * @param shard the part of the pass this test visits.
 */
export const expectScrolling = async (page: Page, shard: Shard): Promise<void> => {
  test.info().skip(!isChromium(page), 'the scrolling pass runs in Chromium')
  const discovery: Discovery = readDiscovery(test.info())
  const locations: readonly SweepLocation[] = sampledLocations(discovery)
  const budget: Budget = armBudget(page, 'scrolling', shard, locations.length)
  await preparePage(page)
  const { visits, taken }: ShardVisits<SweepLocation> = await visitAll(locations, {
    budget,
    labelOf: (location: SweepLocation): string => location.location,
    visit: async (location: SweepLocation): Promise<Visit> => await scrollVisit(page, location),
  })
  finishPass(page, {
    budget,
    pass: 'scrolling',
    visits,
    judgedRules: SCROLLING_RULES,
    locations: shardLocations(taken, (location: SweepLocation): SweepLocation => location),
  })
}
