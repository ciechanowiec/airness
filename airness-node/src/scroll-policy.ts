// What the scrolling pass holds a page to, over the positions the browser measured while a reader scrolls
// it: the document comes to rest where each gesture left it (rule `scroll-hijack`), and text scrolled into
// view does not stay transparent (rule `unrevealed-content`). The browser side drives the gestures and reads
// the positions; everything that decides a verdict is here.
//
// WHY POSITIONS RATHER THAN CALLS. A smooth-scrolling library calls `scrollTo` on every frame of every
// gesture by design, and so does a page that snaps the reader to a section after they stop; only where the
// document comes to rest tells the two apart. Easing towards the point the gesture asked for is the
// reader's own scroll, delivered smoothly. Arriving somewhere else - a section boundary the reader did not
// ask for, or a long run onwards after a small nudge back - takes the page from under their finger.
//
// WHY PINNING IS NOT A FINDING. A section that holds still while the reader scrolls past it is `sticky`
// positioning or a scroll-driven animation, and neither moves the document: the scroll position still
// advances exactly as far as the gesture asked. Only a page that rewrites the scroll position is judged.

/**
 * The rules the scrolling pass judges, on one route per template rather than on every route: its own, and the
 * reduced-motion check that rides on its sample (`reduced-motion-policy`).
 */
export const SCROLLING_RULES: readonly string[] = ['scroll-hijack', 'unrevealed-content', 'reduced-motion']

/** How far from where a gesture left it the document may come to rest, in CSS pixels. */
const REST_TOLERANCE_PX: number = 2

/**
 * How long the document must hold still to count as at rest, in milliseconds. A snapping library waits for
 * the scroll events to stop before it moves the document, typically for a few hundred milliseconds, so a
 * shorter quiet would declare rest just before the snap that is the defect.
 */
export const REST_QUIET_MS: number = 750

/** How long after a gesture the document may keep moving before it is reported as never coming to rest. */
export const REST_CEILING_MS: number = 5000

/**
 * How long text scrolled into view may stay fully transparent once the document rests, in milliseconds: the
 * bound `expectPerceivable` holds a control to.
 */
export const REVEAL_MS: number = 1500

/**
 * The upper share of the viewport text must start in before it is judged. A reveal fired once its block is a
 * fifth or half of the way up the viewport has fired by then, so text still transparent there is waiting for
 * nothing a reader will do.
 */
export const REVEAL_BAND: number = 0.5

/** The share of the viewport height one gesture scrolls, so consecutive views overlap. */
const STEP_SHARE: number = 0.5

/** The most gestures one direction takes; a longer page is crossed in longer gestures. */
const MAX_STEPS: number = 10

const MS_PER_SECOND: number = 1000

/**
 * The wheel gestures that cross a document to its end and back again, so every view is reached going down
 * and left again going up, where a page that treats reversing differently from advancing shows it.
 * @param scrollable how far the document scrolls, in CSS pixels.
 * @param viewportHeight the height of the viewport.
 * @returns the vertical delta of each gesture in order; none for a document that does not scroll.
 */
export const scrollDeltas = (scrollable: number, viewportHeight: number): readonly number[] => {
  if (scrollable <= REST_TOLERANCE_PX || viewportHeight <= 0) {
    return []
  }
  const step: number = Math.max(Math.round(viewportHeight * STEP_SHARE), Math.ceil(scrollable / MAX_STEPS))
  const count: number = Math.ceil(scrollable / step)
  return [...Array.from({ length: count }, (): number => step), ...Array.from({ length: count }, (): number => -step)]
}

/** One wheel gesture and what the document did after it. */
export interface ScrollStep {
  /** The scroll position before the gesture. */
  readonly from: number
  /** The vertical delta the gesture asked for. */
  readonly delta: number
  /** Where the document came to rest, or undefined when it was still moving at the ceiling. */
  readonly rest: number | undefined
  /** The furthest the document was seen from where it started while it moved. */
  readonly travelled: number
  /** How far the document could scroll when it came to rest. */
  readonly maximum: number
}

/**
 * Where a gesture leaves the document when nothing but the browser scrolls it.
 * @param step the gesture.
 * @returns its start plus its delta, within the document's scroll range.
 */
const intendedRest = (step: ScrollStep): number =>
  Math.min(Math.max(step.from + step.delta, 0), Math.max(step.maximum, 0))

/**
 * Whether a gesture moved the document at all. A wheel over a map, an inner scroller or a canvas that
 * answers the wheel itself never reaches the document, which is not the page taking the scroll over.
 * @param step the gesture.
 * @returns true when the document was seen away from where it started.
 */
export const hasMoved = (step: ScrollStep): boolean => step.travelled > REST_TOLERANCE_PX

const px = (value: number): string => `${String(Math.round(value))}px`

/**
 * What is wrong with where a gesture left the document (rule `scroll-hijack`).
 * @param step the gesture.
 * @returns the message, or undefined when the document rests where the gesture left it.
 */
export const hijackMessage = (step: ScrollStep): string | undefined => {
  const gesture: string = `a wheel gesture of ${px(step.delta)} from ${px(step.from)}`
  if (step.rest === undefined) {
    return (
      `${gesture} left the document still moving ${String(REST_CEILING_MS / MS_PER_SECOND)}s later; ` +
      'scroll natively, and animate scroll-linked content without moving the document'
    )
  }
  const intended: number = intendedRest(step)
  return hasMoved(step) && Math.abs(step.rest - intended) > REST_TOLERANCE_PX
    ? `${gesture} came to rest at ${px(step.rest)} instead of ${px(intended)}: the page moved the document ` +
        'after the reader stopped; scroll natively, without snapping to sections or easing past the gesture'
    : undefined
}

/**
 * What is wrong with text that stays transparent in view (rule `unrevealed-content`).
 * @param label the element, as the layout measurement labels it.
 * @returns the message.
 */
export const unrevealedMessage = (label: string): string =>
  `${label} is scrolled into the upper half of the view but stays fully transparent ` +
  `${String(REVEAL_MS / MS_PER_SECOND)}s after the document comes to rest; reveal it when any of it enters ` +
  'the viewport, or show it without waiting'
