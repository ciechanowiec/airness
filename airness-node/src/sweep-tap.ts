// The sweep's touch pass (rule `tap-ignored`): on a touch phone, every element that presents as something to
// operate is tapped, and the page is watched for any answer. One route per template and one story per
// component are visited, still, at the phone's viewport, in a context that emulates touch, so a page that
// asks whether the device can hover or which pointer pressed is answered as a phone answers. An element the
// tap answered is tapped on a page loaded afresh for the next, so no answer leaves its state behind. An element
// the tap left unanswered is clicked with a mouse on the same page in a second browser context at the same
// width that does not emulate touch, and only one the click answers is reported. Every verdict is decided in
// `tap-policy`; the page functions are self-contained: Playwright ships them as text.
import { type Browser, type BrowserContext, type Page, type Request, test } from '@playwright/test'
import { classifyHitTarget, type HitTargetProbe } from './a11y-hit-target.js'
import { type BrowserFinding, type FindingPlace, findingAt } from './browser-findings.js'
import { PHONE } from './browser-viewports.js'
import { type TargetFacts, targetFacts } from './layout.js'
import { settleFindings } from './scanner.js'
import { settleForScan } from './settle.js'
import { hitTargetInPage } from './sweep-hit-target.js'
import { carried } from './sweep-interaction.js'
import { revealInWindowInPage } from './sweep-pointer-only.js'
import {
  armBudget,
  type Budget,
  type ComputedStyleOf,
  type Discovery,
  type DomDocument,
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
import {
  isTapAnswered,
  type TapFacts,
  type TapResponse,
  TOUCH_RULES,
  tapIgnoredMessage,
  tapSignal,
} from './tap-policy.js'

// The browser surface the page functions below touch, declared for the reason `settle.ts` records.
declare const document: DomDocument
declare const getComputedStyle: ComputedStyleOf

/** The attribute naming, for one reading, each element a tap may be aimed at, by its index. */
const CANDIDATE_ATTRIBUTE: string = 'data-airness-tap'

/** The global the page keeps its count of changes to the document under while a tap is watched. */
const WATCH_KEY: string = '__airnessTapWatch'

// A page full of clickable tiles reports its first few; each tap may cost a fresh load, and the rest add
// nothing an agent repairing the first would not find.
const MAX_TAPPED: number = 20

/** How long a page has to answer a tap, in milliseconds: a request a script makes first, then renders. */
const ANSWER_MS: number = 2000

/** How long a loaded page may keep the network busy before it is pressed regardless, in milliseconds. */
const IDLE_MS: number = 3000

/** How often the page is read while a tap is awaited, in milliseconds. */
const POLL_MS: number = 100

/** How long reading a marked element may take before the element is taken to be gone. */
const PROBE_TIMEOUT_MS: number = 5000

/** The kinds of request by which a page answers: a navigation, or data a script asked for. */
const ANSWERING_REQUESTS: ReadonlySet<string> = new Set<string>([
  'document',
  'fetch',
  'xhr',
  'eventsource',
  'websocket',
])

/** An element a tap may be aimed at: its index among all the document's elements, and what it shows. */
interface TapCandidate {
  readonly element: number
  readonly facts: TapFacts
}

// Every element a tap may be aimed at, marked with its index among all the document's elements, which names
// it alike in every context the page is loaded in, with what decides whether it is. The test for presenting is
// repeated here only to keep the reading small; the policy decides.
const tapFactsInPage = (attribute: string): readonly TapCandidate[] => {
  const earlier: readonly Element[] = Array.from(document.querySelectorAll(`[${CSS.escape(attribute)}]`))
  for (const marked of earlier) {
    marked.removeAttribute(attribute)
  }
  // The buttons a form answers by itself: a submit, reset or image button inside a form or naming one.
  const formButtons: string =
    ':is(button:not([type]), button[type="submit" i], button[type="reset" i], input[type="submit" i], ' +
    'input[type="reset" i], input[type="image" i]):is(form *, [form])'
  const read = (element: Element): TapFacts => {
    const type: unknown = Reflect.get(element, 'type')
    return {
      tag: element.tagName.toLowerCase(),
      type: typeof type === 'string' ? type.toLowerCase() : '',
      role: (element.getAttribute('role') ?? '').trim().split(/\s+/u, 1)[0] ?? '',
      hasOnclick: element.hasAttribute('onclick'),
      cursor: getComputedStyle(element).cursor,
      parentCursor: getComputedStyle(element.parentElement ?? element).cursor,
      hasHref: element.matches('a[href], area[href]'),
      isFormButton: element.matches(formButtons),
      isLabelOfControl: element instanceof HTMLLabelElement && element.control !== null,
      isEditable: element instanceof HTMLElement && element.isContentEditable,
      isDisabled: element.matches(':disabled, [aria-disabled="true"], [aria-disabled="true"] *'),
      isInert: element.closest('[inert]') !== null,
      isRendered: element.checkVisibility({ visibilityProperty: true, opacityProperty: true }),
      isRoot: element.matches(':root, body'),
    }
  }
  const presenting: readonly (readonly [Element, TapCandidate])[] = Array.from(
    document.querySelectorAll('*'),
    (element: Element, index: number): readonly [Element, TapCandidate] => [
      element,
      { element: index, facts: read(element) },
    ],
  ).filter(
    ([, { facts }]: readonly [Element, TapCandidate]): boolean =>
      facts.tag === 'button' ||
      facts.tag === 'input' ||
      facts.role !== '' ||
      facts.hasOnclick ||
      (facts.cursor === 'pointer' && facts.parentCursor !== 'pointer'),
  )
  return presenting.map(([element, candidate]: readonly [Element, TapCandidate]): TapCandidate => {
    element.setAttribute(attribute, String(candidate.element))
    return candidate
  })
}

// Marks one element by its index among all the document's elements, as the touch page marked it.
const markElementInPage = ([attribute, index]: readonly [string, number]): void => {
  document.querySelectorAll('*')[index]?.setAttribute(attribute, String(index))
}

/** Where a tap on a marked element lands: the centre of its first fragment that answers there. */
interface TapPoint {
  readonly x: number
  readonly y: number
}

const tapPointInPage = (selector: string): TapPoint | undefined => {
  const element: Element | null = document.querySelector(selector)
  if (element === null) {
    return undefined
  }
  const halves: number = 2
  const fragments: readonly DOMRect[] = Array.from(element.getClientRects())
  return (fragments.length > 1 ? fragments : [element.getBoundingClientRect()])
    .map((box: DOMRect): TapPoint => ({ x: box.left + box.width / halves, y: box.top + box.height / halves }))
    .find((point: TapPoint): boolean => {
      const answer: Element | null = document.elementFromPoint(point.x, point.y)
      return answer !== null && element.contains(answer)
    })
}

/** What the page shows that a tap can change, besides its tree. */
interface TapState {
  readonly address: string
  readonly history: number
  readonly form: string
  readonly layers: number
  readonly scroll: string
}

/** The page's state and how many changes its tree has seen since the watch began. */
interface TapReading {
  readonly mutations: number
  readonly state: TapState
}

// Starts counting the changes to the document, the harness's own marks left out, and reads the state a tap
// can change. A watch left by the previous tap is stopped first.
const readTapInPage = ([key, isStart]: readonly [string, boolean]): TapReading => {
  const observerKey: string = `${key}Observer`
  if (isStart) {
    const previous: unknown = Reflect.get(globalThis, observerKey)
    if (previous instanceof MutationObserver) {
      previous.disconnect()
    }
    Reflect.set(globalThis, key, 0)
    const observer: MutationObserver = new MutationObserver((records: readonly MutationRecord[]): void => {
      const counted: number = records.filter(
        (record: MutationRecord): boolean =>
          !(record.type === 'attributes' && (record.attributeName ?? '').startsWith('data-airness')),
      ).length
      const earlier: unknown = Reflect.get(globalThis, key)
      Reflect.set(globalThis, key, (typeof earlier === 'number' ? earlier : 0) + counted)
    })
    observer.observe(document, { attributes: true, childList: true, characterData: true, subtree: true })
    Reflect.set(globalThis, observerKey, observer)
  }
  const openPopovers = (): number => {
    try {
      return document.querySelectorAll(':popover-open').length
    } catch {
      return 0
    }
  }
  const fields: readonly Element[] = Array.from(document.querySelectorAll('input, select, textarea'))
  const count: unknown = Reflect.get(globalThis, key)
  return {
    mutations: typeof count === 'number' ? count : 0,
    state: {
      address: location.href,
      history: history.length,
      form: fields
        .map((field: Element): string =>
          field instanceof HTMLInputElement
            ? `${field.value}:${String(field.checked)}`
            : String(Reflect.get(field, 'value')),
        )
        .join('|'),
      layers: openPopovers() + document.querySelectorAll('dialog[open]').length,
      scroll: `${String(scrollX)},${String(scrollY)}`,
    },
  }
}

const responseOf = (before: TapState, after: TapReading, hasReachedOut: boolean): TapResponse => ({
  mutations: after.mutations,
  hasMovedAddress: after.state.address !== before.address,
  hasMovedHistory: after.state.history !== before.history,
  hasChangedForm: after.state.form !== before.form,
  hasToggledLayer: after.state.layers !== before.layers,
  hasScrolled: after.state.scroll !== before.scroll,
  hasReachedOut,
})

// Resolves true as soon as one check does, and false once every check has resolved false.
const isAnyTrue = async (checks: readonly Promise<boolean>[]): Promise<boolean> => {
  try {
    return await Promise.any(
      checks.map(
        async (check: Promise<boolean>): Promise<boolean> =>
          (await check) ? true : await Promise.reject(new Error('this check saw no answer')),
      ),
    )
  } catch {
    return false
  }
}

// Whether an awaited event arrived before its wait ran out.
const isEventSeen = async (event: Promise<unknown>): Promise<boolean> => {
  try {
    await event
    return true
  } catch {
    return false
  }
}

// Whether the page opened a popup within the wait; one that opened is closed again.
const isPopupOpened = async (page: Page): Promise<boolean> => {
  try {
    const popup: Page = await page.waitForEvent('popup', { timeout: ANSWER_MS })
    await popup.close()
    return true
  } catch {
    return false
  }
}

// Whether the page reaches beyond itself within the wait: a navigation or a request for data, a popup, or a
// download.
const hasOutwardAnswer = async (page: Page): Promise<boolean> =>
  await isAnyTrue([
    isEventSeen(
      page.waitForEvent('request', {
        predicate: (request: Request): boolean => ANSWERING_REQUESTS.has(request.resourceType()),
        timeout: ANSWER_MS,
      }),
    ),
    isPopupOpened(page),
    isEventSeen(page.waitForEvent('download', { timeout: ANSWER_MS })),
  ])

// Whether a promise has already resolved true, without waiting for it.
const hasResolvedTrue = async (pending: Promise<boolean>): Promise<boolean> =>
  await Promise.race([pending, Promise.resolve(false)])

// The page's state now; a page that navigated away cannot be read.
const readingOf = async (page: Page): Promise<TapReading | undefined> => {
  try {
    return await page.evaluate(readTapInPage, [WATCH_KEY, false] as const)
  } catch {
    return undefined
  }
}

// Reads the page until it shows an answer or the wait runs out. A page that navigated away cannot be read,
// and has answered.
const isAnsweredWithin = async (
  page: Page,
  before: TapState,
  outward: Promise<boolean>,
  waited: number,
): Promise<boolean> => {
  const reading: TapReading | undefined = await readingOf(page)
  if (reading === undefined || isTapAnswered(responseOf(before, reading, await hasResolvedTrue(outward)))) {
    return true
  }
  if (waited >= ANSWER_MS) {
    return false
  }
  await new Promise<void>((resolve: () => void): void => {
    setTimeout(resolve, POLL_MS)
  })
  return await isAnsweredWithin(page, before, outward, waited + POLL_MS)
}

// An element the page removed since it was marked has nothing to probe.
const probeOf = async (page: Page, selector: string): Promise<HitTargetProbe | undefined> => {
  try {
    return await page.locator(selector).evaluate(hitTargetInPage, undefined, { timeout: PROBE_TIMEOUT_MS })
  } catch {
    return undefined
  }
}

/** How an element took a press: out of a pointer's reach, answered, or ignored. */
type PressOutcome = 'unreachable' | 'answered' | 'ignored'

/** One way of pressing a point of the page: a finger's tap, or a mouse's click. */
type Press = (page: Page, point: TapPoint) => Promise<void>

const tap: Press = async (page: Page, point: TapPoint): Promise<void> => {
  await page.touchscreen.tap(point.x, point.y)
}

const click: Press = async (page: Page, point: TapPoint): Promise<void> => {
  await page.mouse.click(point.x, point.y)
}

// Presses a marked element where a pointer reaches it, and watches the page for an answer.
const pressAt = async (page: Page, selector: string, press: Press): Promise<PressOutcome> => {
  await page.evaluate(revealInWindowInPage, selector)
  const probe: HitTargetProbe | undefined = await probeOf(page, selector)
  const point: TapPoint | undefined =
    probe !== undefined && classifyHitTarget(probe).verdict === 'hoverable'
      ? await page.evaluate(tapPointInPage, selector)
      : undefined
  if (point === undefined) {
    return 'unreachable'
  }
  const before: TapReading = await page.evaluate(readTapInPage, [WATCH_KEY, true] as const)
  const outward: Promise<boolean> = hasOutwardAnswer(page)
  await press(page, point)
  return (await isAnsweredWithin(page, before.state, outward, 0)) ? 'answered' : 'ignored'
}

/** The elements of a page a tap is aimed at, by their index among all its elements, and why. */
interface TapTarget {
  readonly element: number
  readonly signal: string
}

const selectorOf = (target: TapTarget): string => `[${CANDIDATE_ATTRIBUTE}="${String(target.element)}"]`

const targetsIn = async (page: Page): Promise<readonly TapTarget[]> => {
  const candidates: readonly TapCandidate[] = await page.evaluate(tapFactsInPage, CANDIDATE_ATTRIBUTE)
  return candidates
    .flatMap((candidate: TapCandidate): readonly TapTarget[] => {
      const signal: string | undefined = tapSignal(candidate.facts)
      return signal === undefined ? [] : [{ element: candidate.element, signal }]
    })
    .slice(0, MAX_TAPPED)
}

/** The touch phone's page, and the page in a context without touch that a mouse clicks in. */
interface TouchPages {
  readonly touch: Page
  readonly mouse: Page
}

// Waits until the page has stopped asking the network for anything, so the scripts that answer a press have
// loaded and run: a framework attaches its handlers to markup already settled, which changes nothing a settle
// sees. A page that keeps a connection busy is pressed once the wait runs out.
const scriptsRun = async (page: Page): Promise<void> => {
  try {
    await page.waitForLoadState('networkidle', { timeout: IDLE_MS })
  } catch {
    // A page that never falls quiet, such as one that polls, is pressed as it stands.
  }
}

// Loads the location afresh on a page, settled and still. A page renders the same elements in the same order
// on every load, so an element's index names the same element.
const freshLoad = async (
  page: Page,
  location: SweepLocation,
  place: FindingPlace,
): Promise<readonly BrowserFinding[]> => {
  const loaded: readonly BrowserFinding[] = await loadLocation(page, location, PHONE, place)
  await settleForScan(page)
  await scriptsRun(page)
  return loaded
}

// Whether a mouse click answers an element a tap left unanswered, on the location loaded afresh where the
// device can hover.
const isClickAnswered = async (
  pages: TouchPages,
  location: SweepLocation,
  place: FindingPlace,
  target: TapTarget,
): Promise<boolean> => {
  await freshLoad(pages.mouse, location, place)
  await pages.mouse.evaluate(markElementInPage, [CANDIDATE_ATTRIBUTE, target.element] as const)
  return (await pressAt(pages.mouse, selectorOf(target), click)) === 'answered'
}

/** One element tapped: the finding if a mouse answers it and a tap does not, and whether the page needs a fresh load. */
interface Tapped {
  readonly visit: Visit
  readonly isAnswered: boolean
}

const tapTarget = async (
  pages: TouchPages,
  location: SweepLocation,
  place: FindingPlace,
  target: TapTarget,
): Promise<Tapped> => {
  const selector: string = selectorOf(target)
  const outcome: PressOutcome = await pressAt(pages.touch, selector, tap)
  if (outcome !== 'ignored' || !(await isClickAnswered(pages, location, place, target))) {
    return { visit: EMPTY_VISIT, isAnswered: outcome === 'answered' }
  }
  // Confirmed on a page loaded afresh and quiet, so a tap that came before the page's scripts ran is not
  // taken for one the page ignores.
  await freshLoad(pages.touch, location, place)
  await pages.touch.evaluate(tapFactsInPage, CANDIDATE_ATTRIBUTE)
  const confirmed: PressOutcome = await pressAt(pages.touch, selector, tap)
  if (confirmed !== 'ignored') {
    return { visit: EMPTY_VISIT, isAnswered: confirmed === 'answered' }
  }
  const facts: TargetFacts | undefined = await targetFacts(pages.touch, selector)
  return {
    visit: carried(
      findingAt(place, 'tap-ignored', tapIgnoredMessage(facts?.label ?? 'an element', target.signal)),
      facts,
    ),
    isAnswered: false,
  }
}

/** The taps of one page so far, and whether the last one left the page needing a fresh load. */
interface Taps {
  readonly visits: readonly Visit[]
  readonly isDirty: boolean
}

const touchVisit = async (pages: TouchPages, location: SweepLocation): Promise<Visit> => {
  const place: FindingPlace = placeAt(pages.touch, location, PHONE, 'touch')
  const loaded: readonly BrowserFinding[] = await loadLocation(pages.touch, location, PHONE, place)
  const unsettled: readonly BrowserFinding[] = await settleFindings(pages.touch, place)
  await scriptsRun(pages.touch)
  const targets: readonly TapTarget[] = await targetsIn(pages.touch)
  const taps: Taps = await targets.reduce(
    async (earlier: Promise<Taps>, target: TapTarget): Promise<Taps> => {
      const done: Taps = await earlier
      const reloaded: readonly BrowserFinding[] = done.isDirty ? await freshLoad(pages.touch, location, place) : []
      if (done.isDirty) {
        await pages.touch.evaluate(tapFactsInPage, CANDIDATE_ATTRIBUTE)
      }
      const tapped: Tapped = await tapTarget(pages, location, place, target)
      return {
        visits: [...done.visits, { ...EMPTY_VISIT, findings: reloaded }, tapped.visit],
        isDirty: tapped.isAnswered,
      }
    },
    Promise.resolve<Taps>({ visits: [], isDirty: false }),
  )
  return merged([
    { ...EMPTY_VISIT, findings: [...loaded, ...unsettled] },
    ...taps.visits,
    await healthOf(pages.touch, place),
  ])
}

// A page in a context of its own, at the phone's width, without touch: a mouse that can hover clicks in it.
// It carries the touch context's session, so a signed-in visit stays signed in.
const openMousePage = async (page: Page): Promise<Page> => {
  const browser: Browser | null = page.context().browser()
  if (browser === null) {
    throw new Error('the touch pass needs the browser of its page to open a context that clicks with a mouse')
  }
  const context: BrowserContext = await browser.newContext({
    viewport: PHONE,
    storageState: await page.context().storageState(),
  })
  const mouse: Page = await context.newPage()
  await preparePage(mouse)
  return mouse
}

/**
 * Tap, on a touch phone, every element of one shard of one route per template and one story per component
 * that presents as something to operate, and judge whether the page answers a tap wherever it answers a mouse.
 * @param page the test's page, in a context that emulates a touch phone.
 * @param shard the part of the pass this test visits.
 */
export const expectTouchResponses = async (page: Page, shard: Shard): Promise<void> => {
  test.info().skip(!isChromium(page), 'the touch pass runs in Chromium')
  const discovery: Discovery = readDiscovery(test.info())
  const locations: readonly SweepLocation[] = sampledLocations(discovery)
  const budget: Budget = armBudget(page, 'touch', shard, locations.length)
  await preparePage(page)
  const mouse: Page = await openMousePage(page)
  try {
    const { visits, taken }: ShardVisits<SweepLocation> = await visitAll(locations, {
      budget,
      labelOf: (location: SweepLocation): string => location.location,
      visit: async (location: SweepLocation): Promise<Visit> => await touchVisit({ touch: page, mouse }, location),
    })
    finishPass(page, {
      budget,
      pass: 'touch',
      visits,
      judgedRules: TOUCH_RULES,
      locations: shardLocations(taken, (location: SweepLocation): SweepLocation => location),
    })
  } finally {
    await mouse.context().close()
  }
}
