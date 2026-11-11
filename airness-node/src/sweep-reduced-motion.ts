// The scrolling pass's reduced-motion check (rule `reduced-motion`). Every other pass freezes animation when it
// emulates `prefers-reduced-motion: reduce`, because it measures a still frame; this one loads the sampled
// page with the preference and without the freeze, so whatever the page itself leaves moving is seen moving.
// It then does what a reader does - scrolls twice, and opens the first disclosure - and records every
// animation that ran and every element's transform around each step. `reduced-motion-policy` decides.
//
// WHY A STILL BASELINE FIRST. An animation already running before the reader does anything, such as a
// spinner, a marquee or an autoplaying carousel, was not set off by the reader. Each step is preceded by a
// short wait with no input in which every running animation is marked on its element as running before, so
// the step is judged only by what it started.
//
// WHY THE PAGE SCROLLS ITSELF. A wheel gesture is aimed at a point, and whatever passes under that point is
// hovered on the way, so a card that lifts on hover would read as motion the scroll set off. A scroll by the
// page moves the document as a wheel does - scroll events, observers and scroll-driven animations all answer
// it - without hovering anything; whether the page takes a wheel gesture over is the crossing's question.
//
// WHY ONE ROUTE PER TEMPLATE. Motion belongs to a template's styles and scripts, which every route of the
// template shares, so the check rides on the scrolling pass's sample: one route per template and one story per
// component, in Chromium, at the phone viewport.
import type { Page } from '@playwright/test'
import { type BrowserFinding, type FindingPlace, findingAt } from './browser-findings.js'
import { PHONE } from './browser-viewports.js'
import { type TargetFacts, targetFacts } from './layout.js'
import {
  type AnimationSample,
  animationMessage,
  animationSampleOf,
  parallaxMessage,
  type RecordedAnimation,
  smoothScrollMessage,
  type TransformTrack,
} from './reduced-motion-policy.js'
import { settleFindings } from './scanner.js'
import { DISCLOSURES, markDisclosuresInPage } from './sweep-disclosures.js'
import { carried } from './sweep-interaction.js'
import {
  type ComputedStyleOf,
  DISCLOSURE_ATTRIBUTE,
  type DomDocument,
  type DomElement,
  type DomRect,
  EMPTY_VISIT,
  loadLocation,
  merged,
  placeAt,
  type SweepLocation,
  type Visit,
} from './sweep-shared.js'

// The browser surface the page functions below touch, declared for the reason `settle.ts` records.
declare const document: DomDocument
declare const getComputedStyle: ComputedStyleOf
declare const scrollBy: (options: ScrollToOptions) => void
declare const setTimeout: (callback: () => void, delayMs: number) => unknown
declare const location: { readonly origin: string; readonly pathname: string }

const RULE: string = 'reduced-motion'

/** The state findings name, which tells this load from the crossing's normal-motion one. */
const STATE: string = 'reduced motion'

/** The attribute naming, on an element, the animations already running on it before a step. */
const BEFORE_ATTRIBUTE: string = 'data-airness-motion-before'

/** The attribute naming each element whose animation a step recorded, for the finding that names it. */
const MOTION_ATTRIBUTE: string = 'data-airness-motion'

/** The attribute numbering every element whose transform is followed across the steps. */
const TRANSFORM_ATTRIBUTE: string = 'data-airness-transform'

/** How long the page is watched with no input before a step, in milliseconds. */
const BASELINE_MS: number = 600

/** How long the page is watched after a step for the animations it sets off, in milliseconds. */
const WATCH_MS: number = 1200

/** How often the page reads its running animations while it is watched: about once a frame. */
const POLL_MS: number = 16

/** How far each of the two scrolls moves the document, as a share of the viewport's height. */
const SCROLL_SHARE: number = 0.5

/** How long the first disclosure may take to accept a click. */
const CLICK_TIMEOUT_MS: number = 5000

/** What the page reads off one animation, with the mark naming its element. */
interface MarkedRecording extends RecordedAnimation {
  readonly mark: string
}

/** One element's transform at one reading, by the number it was given at the first. */
interface TransformReading {
  readonly index: number
  readonly value: string
  readonly width: number
  readonly height: number
  readonly isAnimated: boolean
}

/** What a still baseline needs to know. */
interface BaselineQuery {
  readonly ms: number
  readonly pollMs: number
  readonly before: string
}

/** What the watch after a step needs to know. */
interface WatchQuery extends BaselineQuery {
  readonly motion: string
  /** The prefix of the marks this watch gives, unique to the step. */
  readonly step: string
  /** The control the step activated; a selector matching nothing for a scroll. */
  readonly control: string
}

// Watches the running animations for the given time with no input, about once a frame, and marks each on its
// element as running before, by a key that names it among the element's animations. Recursive rather than a
// loop, as the settle is: each step awaits a timer, so what grows is time rather than stack.
const baselineInPage = async (query: BaselineQuery): Promise<void> => {
  const markRunning = (animation: Animation, target: Element): void => {
    const name: unknown =
      Reflect.get(animation, 'animationName') ?? Reflect.get(animation, 'transitionProperty') ?? animation.id
    const marked: string = target.getAttribute(query.before) ?? ''
    target.setAttribute(query.before, `${marked} ${animation.constructor.name}:${String(name)}`.trim())
  }
  const deadline: number = performance.now() + query.ms
  const watch = async (): Promise<void> => {
    const targeted: readonly (readonly [Animation, Element])[] = document
      .getAnimations()
      .flatMap((animation: Animation): readonly (readonly [Animation, Element])[] =>
        animation.effect instanceof KeyframeEffect && animation.effect.target !== null
          ? [[animation, animation.effect.target]]
          : [],
      )
    for (const [animation, target] of targeted) {
      markRunning(animation, target)
    }
    if (performance.now() >= deadline) {
      return
    }
    await new Promise<void>((resolve: () => void): void => {
      setTimeout(resolve, query.pollMs)
    })
    await watch()
  }
  await watch()
}

// Watches the running animations for the given time after a step, about once a frame, and reads each the first
// time it is seen, marking its element so a finding can name it. An animation the baseline marked on its
// element was running before the step.
const watchInPage = async (query: WatchQuery): Promise<readonly MarkedRecording[]> => {
  /** What the watch has read so far. */
  type Found = readonly MarkedRecording[]
  const read = (animation: Animation, index: number): readonly MarkedRecording[] => {
    const effect: AnimationEffect | null = animation.effect
    if (!(effect instanceof KeyframeEffect) || effect.target === null) {
      return []
    }
    const target: Element = effect.target
    const name: unknown =
      Reflect.get(animation, 'animationName') ?? Reflect.get(animation, 'transitionProperty') ?? animation.id
    const mark: string = target.getAttribute(query.motion) ?? `${query.step}-${String(index)}`
    target.setAttribute(query.motion, mark)
    const box: DomRect = target.getBoundingClientRect()
    return [
      {
        mark,
        constructorName: animation.constructor.name,
        isTimeDriven: animation.timeline instanceof DocumentTimeline,
        keyframes: effect.getKeyframes(),
        activeMs: Number(effect.getComputedTiming().activeDuration),
        width: box.width,
        height: box.height,
        wasRunningBefore: (target.getAttribute(query.before) ?? '')
          .split(' ')
          .includes(`${animation.constructor.name}:${String(name)}`),
        isOnActivatedControl: target.closest(query.control) !== null,
      },
    ]
  }
  const deadline: number = performance.now() + query.ms
  const watch = async (seen: readonly Animation[], found: Found): Promise<Found> => {
    const fresh: readonly Animation[] = document
      .getAnimations()
      .filter((animation: Animation): boolean => animation.playState === 'running' && !seen.includes(animation))
    const recorded: Found = [
      ...found,
      ...fresh.flatMap((animation: Animation, index: number): readonly MarkedRecording[] =>
        read(animation, seen.length + index),
      ),
    ]
    if (performance.now() >= deadline) {
      return recorded
    }
    await new Promise<void>((resolve: () => void): void => {
      setTimeout(resolve, query.pollMs)
    })
    return await watch([...seen, ...fresh], recorded)
  }
  return await watch([], [])
}

// Every rendered element placed by a transform, numbered the first time it is read so later readings name it
// alike. Only those are numbered and reported: an element a reading leaves out read `none` then, which is the
// default every other element reads.
const transformsInPage = (attribute: string): readonly TransformReading[] => {
  const placementOf = (element: DomElement): string => {
    const style: CSSStyleDeclaration = getComputedStyle(element)
    return [style.transform, style.translate, style.rotate, style.scale].join(' ')
  }
  const placed: readonly DomElement[] = Array.from(document.querySelectorAll('body *')).filter(
    (element: DomElement): boolean => placementOf(element) !== 'none none none none' && element.checkVisibility(),
  )
  const numbered: number = document.querySelectorAll(`[${CSS.escape(attribute)}]`).length
  const unnumbered: readonly DomElement[] = placed.filter(
    (element: DomElement): boolean => !element.hasAttribute(attribute),
  )
  for (const [offset, element] of unnumbered.entries()) {
    element.setAttribute(attribute, String(numbered + offset))
  }
  return placed.map((element: DomElement): TransformReading => {
    const box: DomRect = element.getBoundingClientRect()
    return {
      index: Number(element.getAttribute(attribute)),
      value: placementOf(element),
      width: box.width,
      height: box.height,
      isAnimated: element.getAnimations().length > 0,
    }
  })
}

const scrollInPage = (share: number): void => {
  scrollBy({ top: document.documentElement.clientHeight * share, behavior: 'instant' })
}

/** How the document scrolls to its own fragments, and whether it links to any. */
interface FragmentScroll {
  readonly scrollBehavior: string
  readonly hasFragmentLinks: boolean
}

const fragmentScrollInPage = (): FragmentScroll => ({
  scrollBehavior: getComputedStyle(document.documentElement).scrollBehavior,
  hasFragmentLinks: Array.from(document.querySelectorAll('a[href*="#"]')).some(
    (link: Element): boolean =>
      link instanceof HTMLAnchorElement &&
      link.hash.length > 1 &&
      link.origin === location.origin &&
      link.pathname === location.pathname,
  ),
})

/** What one step recorded, with what the reader did. */
interface StepRecord {
  readonly trigger: string
  readonly samples: readonly MarkedRecording[]
}

/** A selector no element matches, standing for the control a scroll did not activate. */
const NO_CONTROL: string = ':not(*)'

const baselineQuery: BaselineQuery = { ms: BASELINE_MS, pollMs: POLL_MS, before: BEFORE_ATTRIBUTE }

/** One thing the reader does: its name among the marks, how a finding words it, and what it activates. */
interface Step {
  readonly name: string
  readonly trigger: string
  readonly control: string
}

// A still baseline, then the step, then the watch over what it set off.
const recordStep = async (page: Page, step: Step, act: () => Promise<void>): Promise<StepRecord> => {
  await page.evaluate(baselineInPage, baselineQuery)
  await act()
  const recorded: readonly MarkedRecording[] = await page.evaluate(watchInPage, {
    ...baselineQuery,
    ms: WATCH_MS,
    motion: MOTION_ATTRIBUTE,
    step: step.name,
    control: step.control,
  })
  return { trigger: step.trigger, samples: recorded }
}

const animationVisits = async (page: Page, place: FindingPlace, records: readonly StepRecord[]): Promise<Visit> => {
  const visits: readonly Visit[] = await Promise.all(
    records.flatMap((record: StepRecord): readonly Promise<Visit>[] =>
      record.samples.map(async (recorded: MarkedRecording): Promise<Visit> => {
        const sample: AnimationSample = animationSampleOf(recorded)
        if (animationMessage('', sample, record.trigger) === undefined) {
          return EMPTY_VISIT
        }
        const facts: TargetFacts | undefined = await targetFacts(page, `[${MOTION_ATTRIBUTE}="${recorded.mark}"]`)
        const message: string | undefined = animationMessage(facts?.label ?? 'an element', sample, record.trigger)
        return message === undefined ? EMPTY_VISIT : carried(findingAt(place, RULE, message), facts)
      }),
    ),
  )
  return merged(visits)
}

// Each element's transforms across the readings, `none` where a reading did not report it.
const tracksOf = (readings: readonly (readonly TransformReading[])[]): ReadonlyMap<number, TransformTrack> => {
  const indices: readonly number[] = [
    ...new Set<number>(readings.flat().map((reading: TransformReading): number => reading.index)),
  ]
  return new Map<number, TransformTrack>(
    indices.map((index: number): readonly [number, TransformTrack] => {
      const own: readonly (TransformReading | undefined)[] = readings.map(
        (reading: readonly TransformReading[]): TransformReading | undefined =>
          reading.find((entry: TransformReading): boolean => entry.index === index),
      )
      const last: TransformReading | undefined = own.findLast(
        (entry: TransformReading | undefined): boolean => entry !== undefined,
      )
      return [
        index,
        {
          values: own.map((entry: TransformReading | undefined): string => entry?.value ?? 'none none none none'),
          width: last?.width ?? 0,
          height: last?.height ?? 0,
          isAnimated: own.some((entry: TransformReading | undefined): boolean => entry?.isAnimated === true),
        },
      ]
    }),
  )
}

const parallaxVisits = async (
  page: Page,
  place: FindingPlace,
  readings: readonly (readonly TransformReading[])[],
): Promise<Visit> => {
  const visits: readonly Visit[] = await Promise.all(
    [...tracksOf(readings)].map(async ([index, track]: readonly [number, TransformTrack]): Promise<Visit> => {
      if (parallaxMessage('', track) === undefined) {
        return EMPTY_VISIT
      }
      const facts: TargetFacts | undefined = await targetFacts(page, `[${TRANSFORM_ATTRIBUTE}="${String(index)}"]`)
      const message: string | undefined = parallaxMessage(facts?.label ?? 'an element', track)
      return message === undefined ? EMPTY_VISIT : carried(findingAt(place, RULE, message), facts)
    }),
  )
  return merged(visits)
}

const smoothScrollVisit = async (page: Page, place: FindingPlace): Promise<Visit> => {
  const scroll: FragmentScroll = await page.evaluate(fragmentScrollInPage)
  const message: string | undefined = smoothScrollMessage(scroll.scrollBehavior, scroll.hasFragmentLinks)
  if (message === undefined) {
    return EMPTY_VISIT
  }
  const facts: TargetFacts | undefined = await targetFacts(page, 'html')
  return carried(findingAt(place, RULE, message), facts)
}

const SCROLL_TRIGGER: string = 'a scroll'

/** What the two scrolls recorded: the animations each set off, and the transform readings around them. */
interface ScrollRecord {
  readonly records: readonly StepRecord[]
  readonly readings: readonly (readonly TransformReading[])[]
}

// The two scrolls, each between transform readings: the first two readings bracket the still baseline, so an
// element a script moves on its own is told from one that follows the scroll.
const scrollSteps = async (page: Page): Promise<ScrollRecord> => {
  const start: readonly TransformReading[] = await page.evaluate(transformsInPage, TRANSFORM_ATTRIBUTE)
  await page.evaluate(baselineInPage, baselineQuery)
  const rested: readonly TransformReading[] = await page.evaluate(transformsInPage, TRANSFORM_ATTRIBUTE)
  const scroll = async (): Promise<void> => {
    await page.evaluate(scrollInPage, SCROLL_SHARE)
  }
  const first: StepRecord = await recordStep(
    page,
    { name: 'scroll-1', trigger: SCROLL_TRIGGER, control: NO_CONTROL },
    scroll,
  )
  const once: readonly TransformReading[] = await page.evaluate(transformsInPage, TRANSFORM_ATTRIBUTE)
  const second: StepRecord = await recordStep(
    page,
    { name: 'scroll-2', trigger: SCROLL_TRIGGER, control: NO_CONTROL },
    scroll,
  )
  const twice: readonly TransformReading[] = await page.evaluate(transformsInPage, TRANSFORM_ATTRIBUTE)
  return { records: [first, second], readings: [start, rested, once, twice] }
}

// The first disclosure it is safe to open, opened by a click as a reader on a phone opens it.
const disclosureStep = async (page: Page): Promise<readonly StepRecord[]> => {
  const count: number = await page.evaluate(markDisclosuresInPage, [DISCLOSURES, DISCLOSURE_ATTRIBUTE] as const)
  if (count === 0) {
    return []
  }
  const selector: string = `[${DISCLOSURE_ATTRIBUTE}="0"]`
  const facts: TargetFacts | undefined = await targetFacts(page, selector)
  const record: StepRecord = await recordStep(
    page,
    { name: 'disclosure', trigger: `opening ${facts?.label ?? 'a disclosure'}`, control: selector },
    async (): Promise<void> => {
      try {
        await page.locator(selector).click({ timeout: CLICK_TIMEOUT_MS })
      } catch {
        // A disclosure that would not take the click opened nothing, and the watch then records nothing new.
      }
    },
  )
  return [record]
}

/**
 * Load a sampled location again with reduced motion emulated and nothing held still, scroll it twice and open
 * its first disclosure, and judge what moved.
 * @param page the test's page, in the touch-phone context of the scrolling pass.
 * @param location the location.
 * @returns the visit carrying the findings.
 */
export const reducedMotionVisit = async (page: Page, location: SweepLocation): Promise<Visit> => {
  const place: FindingPlace = placeAt(page, location, PHONE, STATE)
  const loaded: readonly BrowserFinding[] = await loadLocation(page, location, PHONE, {
    ...place,
    motion: 'reduce',
    isStill: false,
  })
  const unsettled: readonly BrowserFinding[] = await settleFindings(page, place)
  const smooth: Visit = await smoothScrollVisit(page, place)
  const scrolled: ScrollRecord = await scrollSteps(page)
  const opened: readonly StepRecord[] = await disclosureStep(page)
  return merged([
    { ...EMPTY_VISIT, findings: [...loaded, ...unsettled] },
    smooth,
    await animationVisits(page, place, [...scrolled.records, ...opened]),
    await parallaxVisits(page, place, scrolled.readings),
  ])
}
