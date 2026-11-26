// How a keyboard-focused element's indicator is captured for the focus rules. The page is shot whole with
// the element focused and again with it blurred, and both are compared within the region where focus can
// show: the element and each ancestor or descendant whose look changes with it, as far as their outlines
// and shadows reach (`focusRegion`). A card that rings itself when its stretched link takes focus, or an
// icon that fills in, is judged where it draws. The page functions are self-contained: Playwright ships
// them to the page as text.
import type { Page } from '@playwright/test'
import { type FocusArea, hasContrastingFocus, hasVisibleFocus, requiredFocusArea } from './browser-rules.js'
import {
  type ElementBox,
  type FocusRegion,
  focusRegion,
  type IndicatorElement,
  type IndicatorStyle,
} from './focus-reach.js'
import { type RegionPixels, regionPixels } from './region-pixels.js'
import { sweepScreenshot } from './scanner.js'

interface StyledElement {
  readonly getBoundingClientRect: () => ElementBox
  readonly parentElement: StyledElement | null
  readonly querySelectorAll: (selector: string) => ArrayLike<StyledElement>
  readonly setAttribute: (name: string, value: string) => void
  readonly removeAttribute: (name: string) => void
  readonly blur?: () => void
}

declare const document: {
  readonly activeElement: StyledElement | null
  readonly body: StyledElement
  readonly documentElement: StyledElement
  readonly querySelector: (selector: string) => StyledElement | null
}
declare const getComputedStyle: (element: StyledElement) => IndicatorStyle
declare const CSS: { readonly escape: (value: string) => string }

/** The look of the focused element, of its ancestors and of its descendants, in that order. */
interface IndicatorFacts {
  readonly focused: StyledFacts | undefined
  readonly related: readonly StyledFacts[]
}

interface StyledFacts {
  readonly box: ElementBox
  readonly style: IndicatorStyle
}

const FOCUSED_ATTRIBUTE: string = 'data-airness-focused'
// Enough for any control; a container holding more is not one whose descendants draw its focus.
const MAX_DESCENDANTS: number = 200

const indicatorFactsInPage = ([selector, maxDescendants]: readonly [string, number]): IndicatorFacts => {
  const focused: StyledElement | null = document.querySelector(selector)
  if (focused === null) {
    return { focused: undefined, related: [] }
  }
  const factsOf = (element: StyledElement): StyledFacts => {
    const style: IndicatorStyle = getComputedStyle(element)
    const box: ElementBox = element.getBoundingClientRect()
    return {
      box: {
        left: box.left,
        top: box.top,
        right: box.right,
        bottom: box.bottom,
      },
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
  const roots: ReadonlySet<StyledElement> = new Set<StyledElement>([document.body, document.documentElement])
  const ancestorsOf = (element: StyledElement | null): readonly StyledElement[] =>
    element === null || roots.has(element) ? [] : [element, ...ancestorsOf(element.parentElement)]
  return {
    focused: factsOf(focused),
    related: [
      ...ancestorsOf(focused.parentElement),
      ...Array.from(focused.querySelectorAll('*')).slice(0, maxDescendants),
    ].map((element: StyledElement): StyledFacts => factsOf(element)),
  }
}

const markFocusedInPage = (name: string): void => {
  document.activeElement?.setAttribute(name, '')
}

const unmarkInPage = (name: string): void => {
  document.querySelector(`[${CSS.escape(name)}]`)?.removeAttribute(name)
}

const blurInPage = (): void => {
  document.activeElement?.blur?.()
}

/** How a keyboard-focused element shows its focus. */
export type FocusVerdict = 'out of view' | 'unchanged' | 'faint' | 'contrasting'

const asIndicator = (focused: StyledFacts, unfocused: StyledFacts | undefined): IndicatorElement => ({
  box: focused.box,
  focused: focused.style,
  unfocused: (unfocused ?? focused).style,
})

const regionOf = (
  focused: IndicatorFacts,
  unfocused: IndicatorFacts,
  viewport: { readonly width: number; readonly height: number },
): FocusRegion | undefined => {
  if (focused.focused === undefined) {
    return undefined
  }
  // A tree that changed between the two readings cannot be paired element by element.
  const isPaired: boolean = focused.related.length === unfocused.related.length
  return focusRegion(
    asIndicator(focused.focused, unfocused.focused),
    isPaired
      ? focused.related.map(
          (facts: StyledFacts, index: number): IndicatorElement => asIndicator(facts, unfocused.related[index]),
        )
      : [],
    viewport,
  )
}

/** The page shot focused and blurred, with the look of the elements where focus can show in each. */
interface FocusCapture {
  readonly focusedShot: Uint8Array
  readonly unfocusedShot: Uint8Array
  readonly focusedFacts: IndicatorFacts
  readonly unfocusedFacts: IndicatorFacts
}

const captureFocus = async (page: Page): Promise<FocusCapture> => {
  const facts: readonly [string, number] = [`[${FOCUSED_ATTRIBUTE}]`, MAX_DESCENDANTS]
  await page.evaluate(markFocusedInPage, FOCUSED_ATTRIBUTE)
  const focusedFacts: IndicatorFacts = await page.evaluate(indicatorFactsInPage, facts)
  const focusedShot: Uint8Array = await sweepScreenshot(page, {
    animations: 'disabled',
    caret: 'hide',
  })
  await page.evaluate(blurInPage)
  const unfocusedFacts: IndicatorFacts = await page.evaluate(indicatorFactsInPage, facts)
  const unfocusedShot: Uint8Array = await sweepScreenshot(page, {
    animations: 'disabled',
    caret: 'hide',
  })
  await page.evaluate(unmarkInPage, FOCUSED_ATTRIBUTE)
  return { focusedShot, unfocusedShot, focusedFacts, unfocusedFacts }
}

// How the two renderings differ, judged against the area the focused element's indicator must reach. The
// browser's own ring is read from the focused element as it was shot focused.
const verdictOf = (renderings: RegionPixels, capture: FocusCapture, area: FocusArea): FocusVerdict => {
  const { first: focused, second: unfocused }: RegionPixels = renderings
  if (!hasVisibleFocus(unfocused, focused)) {
    return 'unchanged'
  }
  const outlineStyle: string = capture.focusedFacts.focused?.style.outlineStyle ?? ''
  return hasContrastingFocus({
    focused,
    unfocused,
    scale: renderings.scale,
    required: requiredFocusArea(area, outlineStyle),
  })
    ? 'contrasting'
    : 'faint'
}

/**
 * Shoot the page with the active element focused and again blurred, and judge its indicator where it shows.
 * The element is left blurred, as the tab walk expects.
 * @param page the page, with the element focused by keyboard.
 * @param area the element's perimeter area and longer side, which decide how much must contrast.
 * @returns how the element shows its focus.
 */
export const judgeFocusIndicator = async (page: Page, area: FocusArea): Promise<FocusVerdict> => {
  const viewport: { readonly width: number; readonly height: number } | null = page.viewportSize()
  const capture: FocusCapture = await captureFocus(page)
  const region: FocusRegion | undefined =
    viewport === null ? undefined : regionOf(capture.focusedFacts, capture.unfocusedFacts, viewport)
  if (viewport === null || region === undefined) {
    return 'out of view'
  }
  const renderings: RegionPixels = await regionPixels(
    page,
    [capture.focusedShot, capture.unfocusedShot],
    region,
    viewport.width,
  )
  if (renderings.scale === 0) {
    throw new Error('the browser could not decode the focus screenshots')
  }
  return verdictOf(renderings, capture, area)
}
