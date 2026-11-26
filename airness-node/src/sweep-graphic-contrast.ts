// The interaction pass's check that a control carrying no text stands out from what lies beneath it (rule
// `graphic-contrast`). Every operable element is read once and marked; each the policy judges, and a pointer
// can reach, is shot with the control shown and again with it hidden, and the policy compares the region it
// draws in. The page functions are self-contained: Playwright ships them to the page as text.
import type { Page } from '@playwright/test'
import { classifyHitTarget, type HitTargetProbe } from './a11y-hit-target.js'
import { type FindingPlace, findingAt } from './browser-findings.js'
import { type ElementBox, type FocusRegion, type IndicatorStyle, indicatorReach } from './focus-reach.js'
import {
  type GraphicFacts,
  type GraphicMeasure,
  graphicContrastMessage,
  graphicRegion,
  isGraphicCandidate,
  measureGraphic,
} from './graphic-contrast-policy.js'
import { type TargetFacts, targetFacts } from './layout.js'
import { adoptStyle } from './page-style.js'
import { OPERABLE_SELECTOR } from './pointer-only.js'
import { type RegionPixels, regionPixels } from './region-pixels.js'
import { sweepScreenshot } from './scanner.js'
import { hitTargetInPage } from './sweep-hit-target.js'
import { type CarryFinding, revealInWindowInPage } from './sweep-pointer-only.js'
import {
  type ComputedStyleOf,
  type DomDocument,
  type DomElement,
  EMPTY_VISIT,
  merged,
  type Visit,
} from './sweep-shared.js'

// The browser surface the page functions below touch, declared for the reason `settle.ts` records.
declare const document: DomDocument
declare const getComputedStyle: ComputedStyleOf

/** The attribute naming, for one reading, each operable element by its index. */
const CANDIDATE_ATTRIBUTE: string = 'data-airness-graphic'

/** The attribute that hides the control being shot, through the stylesheet below. */
const HIDDEN_ATTRIBUTE: string = 'data-airness-graphic-hidden'

// Hidden rather than removed, so nothing around the control moves into the space it leaves; no transition
// eases it out, so the second shot shows the page without it.
const HIDING_STYLE: string =
  `[${HIDDEN_ATTRIBUTE}], [${HIDDEN_ATTRIBUTE}] * ` +
  '{ visibility: hidden !important; transition: none !important; animation: none !important; }'

// A page full of icon buttons reports its first few; each costs two screenshots, and the rest add nothing an
// agent repairing the first would not find.
const MAX_SHOT: number = 20

/** How long reading a marked element may take before the element is taken to be gone. */
const PROBE_TIMEOUT_MS: number = 5000

// Every operable element, marked with its index in document order, with what decides whether its graphics
// are judged. Text counts only where a reader can see it: a visually hidden label - a one-pixel box, or one
// clipped away - names the control to assistive technology and draws nothing.
const graphicFactsInPage = ([selector, candidate]: readonly [string, string]): readonly GraphicFacts[] => {
  const earlier: readonly Element[] = Array.from(document.querySelectorAll(`[${CSS.escape(candidate)}]`))
  for (const marked of earlier) {
    marked.removeAttribute(candidate)
  }
  const buttonTypes: ReadonlySet<string> = new Set<string>(['button', 'submit', 'reset'])
  const isSeen = (element: Element): boolean => {
    const style: CSSStyleDeclaration = getComputedStyle(element)
    const box: DOMRect = element.getBoundingClientRect()
    return (
      element.checkVisibility({ visibilityProperty: true, opacityProperty: true }) &&
      box.width > 1 &&
      box.height > 1 &&
      style.clipPath === 'none' &&
      ['auto', ''].includes(style.getPropertyValue('clip'))
    )
  }
  const hasSeenText = (element: Element): boolean =>
    [element, ...Array.from(element.querySelectorAll('*'))].some(
      (holder: Element): boolean =>
        Array.from(holder.childNodes).some(
          (child: ChildNode): boolean =>
            child.nodeType === Node.TEXT_NODE && (child.textContent ?? '').trim().length > 0,
        ) && isSeen(holder),
    )
  const hasShownText = (element: Element): boolean =>
    element instanceof HTMLInputElement
      ? buttonTypes.has(element.type.toLowerCase()) && element.value.trim().length > 0
      : hasSeenText(element)
  return Array.from(document.querySelectorAll(selector), (element: Element, index: number): GraphicFacts => {
    element.setAttribute(candidate, String(index))
    return {
      tag: element.tagName.toLowerCase(),
      type: element instanceof HTMLInputElement ? element.type.toLowerCase() : '',
      appearance: getComputedStyle(element).appearance,
      hasVisibleText: hasShownText(element),
      isDisabled: element.matches(':disabled') || element.closest('[aria-disabled="true"]') !== null,
      isInert: element.closest('[inert]') !== null,
      isRendered: element.checkVisibility({ visibilityProperty: true, opacityProperty: true }),
    }
  })
}

/** Where a marked control draws: its box and its descendants', and the styles its outline and shadows reach by. */
interface DrawnFacts {
  readonly boxes: readonly ElementBox[]
  readonly style: IndicatorStyle
}

const drawnFactsInPage = (selector: string): DrawnFacts | undefined => {
  const element: DomElement | null = document.querySelector(selector)
  if (element === null) {
    return undefined
  }
  const maxDescendants: number = 200
  const style: CSSStyleDeclaration = getComputedStyle(element)
  return {
    boxes: [element, ...Array.from(element.querySelectorAll('*')).slice(0, maxDescendants)].map(
      (target: Element): ElementBox => {
        const box: DOMRect = target.getBoundingClientRect()
        return { left: box.left, top: box.top, right: box.right, bottom: box.bottom }
      },
    ),
    style: {
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
      outlineOffset: style.outlineOffset,
      outlineColor: style.outlineColor,
      boxShadow: style.boxShadow,
      borderColor: style.borderColor,
      backgroundColor: style.backgroundColor,
      color: style.color,
      textDecorationLine: style.textDecorationLine,
    },
  }
}

const toggleInPage = ([selector, name, isOn]: readonly [string, string, boolean]): void => {
  document.querySelector(selector)?.toggleAttribute(name, isOn)
}

const unmarkInPage = (name: string): void => {
  const marked: readonly Element[] = Array.from(document.querySelectorAll(`[${CSS.escape(name)}]`))
  for (const element of marked) {
    element.removeAttribute(name)
  }
}

// Focus left by the keyboard walk would draw its indicator into the shots.
const blurInPage = (): void => {
  const active: Element | null = document.activeElement
  if (active instanceof HTMLElement) {
    active.blur()
  }
}

// An element the page removed since it was marked has nothing to probe.
const probeOf = async (page: Page, selector: string): Promise<HitTargetProbe | undefined> => {
  try {
    return await page.locator(selector).evaluate(hitTargetInPage, undefined, { timeout: PROBE_TIMEOUT_MS })
  } catch {
    return undefined
  }
}

const SHOT: { readonly animations: 'disabled'; readonly caret: 'hide' } = { animations: 'disabled', caret: 'hide' }

const measureAt = async (page: Page, selector: string): Promise<GraphicMeasure | undefined> => {
  const viewport: { readonly width: number; readonly height: number } | null = page.viewportSize()
  const drawn: DrawnFacts | undefined = await page.evaluate(drawnFactsInPage, selector)
  const region: FocusRegion | undefined =
    viewport === null || drawn === undefined
      ? undefined
      : graphicRegion(drawn.boxes, indicatorReach(drawn.style), viewport)
  if (viewport === null || region === undefined) {
    return undefined
  }
  const shown: Uint8Array = await sweepScreenshot(page, SHOT)
  await page.evaluate(toggleInPage, [selector, HIDDEN_ATTRIBUTE, true] as const)
  const hidden: Uint8Array = await sweepScreenshot(page, SHOT)
  await page.evaluate(toggleInPage, [selector, HIDDEN_ATTRIBUTE, false] as const)
  const pixels: RegionPixels = await regionPixels(page, [shown, hidden], region, viewport.width)
  if (pixels.scale === 0) {
    throw new Error('the browser could not decode the graphic contrast screenshots')
  }
  return measureGraphic({
    shown: pixels.first,
    hidden: pixels.second,
    width: Math.round(region.width * pixels.scale),
    scale: pixels.scale,
  })
}

const graphicAt = async (page: Page, place: FindingPlace, index: number, carry: CarryFinding): Promise<Visit> => {
  const selector: string = `[${CANDIDATE_ATTRIBUTE}="${String(index)}"]`
  await page.evaluate(revealInWindowInPage, selector)
  const probe: HitTargetProbe | undefined = await probeOf(page, selector)
  if (probe === undefined || classifyHitTarget(probe).verdict !== 'hoverable') {
    return EMPTY_VISIT
  }
  const measure: GraphicMeasure | undefined = await measureAt(page, selector)
  if (measure === undefined) {
    return EMPTY_VISIT
  }
  const facts: TargetFacts | undefined = await targetFacts(page, selector)
  const message: string | undefined = graphicContrastMessage(facts?.label ?? 'a control', measure)
  return message === undefined ? EMPTY_VISIT : carry(findingAt(place, 'graphic-contrast', message), facts)
}

/**
 * Judge every control on the page that carries no text by how much of what it draws stands out from what
 * lies beneath it. The page is left with no mark of the check, but scrolled to the last control shot.
 * @param page the page, loaded and settled, animation stilled.
 * @param place where findings are made.
 * @param carry how a finding on an element is judged against the exemptions above it.
 * @returns the visit carrying the findings.
 */
export const graphicContrastVisit = async (page: Page, place: FindingPlace, carry: CarryFinding): Promise<Visit> => {
  await page.evaluate(blurInPage)
  await adoptStyle(page, HIDING_STYLE)
  const facts: readonly GraphicFacts[] = await page.evaluate(graphicFactsInPage, [
    OPERABLE_SELECTOR,
    CANDIDATE_ATTRIBUTE,
  ] as const)
  const judged: readonly number[] = facts
    .flatMap((candidate: GraphicFacts, index: number): readonly number[] =>
      isGraphicCandidate(candidate) ? [index] : [],
    )
    .slice(0, MAX_SHOT)
  try {
    const visits: readonly Visit[] = await judged.reduce(
      async (earlier: Promise<readonly Visit[]>, index: number): Promise<readonly Visit[]> => [
        ...(await earlier),
        await graphicAt(page, place, index, carry),
      ],
      Promise.resolve<readonly Visit[]>([]),
    )
    return merged(visits)
  } finally {
    await page.evaluate(unmarkInPage, CANDIDATE_ATTRIBUTE)
  }
}
