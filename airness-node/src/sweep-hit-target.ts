// The page function that reads where a control is and what answers a pointer at its centre, shared by the
// interaction pass's hover and by its pointer-only check, which bring the control into view differently, and by
// the scanner, which excuses from target size what the visually-hidden pattern clips away.
import type { HitTargetProbe } from './a11y-hit-target.js'

/** A box in the viewport, in CSS pixels, as the page reports one. */
interface PageBox {
  readonly left: number
  readonly top: number
  readonly right: number
  readonly bottom: number
  readonly width: number
  readonly height: number
}

/** The style the probe reads, as the page computes it. */
interface PageStyle {
  readonly clipPath: string
  getPropertyValue(property: string): string
}

/**
 * The part of an element the probe reads: its box, the boxes of its fragments, what its document answers at a point
 * and the style its window computes. A DOM element is one; naming only these lets a test hand in a stand-in.
 */
export interface HitTargetElement {
  getBoundingClientRect(): PageBox
  getClientRects(): ArrayLike<PageBox>
  contains(other: unknown): boolean
  closest(selector: string): unknown
  readonly ownerDocument: {
    elementFromPoint(x: number, y: number): unknown
    readonly documentElement: { readonly clientWidth: number; readonly clientHeight: number }
    readonly defaultView: { getComputedStyle(element: HitTargetElement): PageStyle } | null
  }
}

/**
 * Read an element's box and hit target together, in the page, without advancing a scroll-driven scene while
 * measuring it: the interaction pass scrolls a control into view first, the pointer-only check scrolls only the
 * window. A pointer reaches an element laid out in one box at that box's centre. One laid out in several, such as a
 * link that wraps onto a second line, is reached at the centre of any of its fragments: the centre of the box around
 * them all can fall on the text between them, which no pointer aiming at the link would press.
 * @param element the element, already in view.
 * @returns what the hit-target classification reads.
 */
export const hitTargetInPage = (element: HitTargetElement): HitTargetProbe => {
  const halves: number = 2
  const box: PageBox = element.getBoundingClientRect()
  const fragments: readonly PageBox[] = Array.from(element.getClientRects())
  const page: HitTargetElement['ownerDocument'] = element.ownerDocument
  // A page always has its window; a document without one has no style to read and nothing a pointer reaches.
  const style: PageStyle =
    page.defaultView === null
      ? { clipPath: 'none', getPropertyValue: (): string => '' }
      : page.defaultView.getComputedStyle(element)
  const isAnsweredAt = (fragment: PageBox): boolean => {
    const answer: unknown = page.elementFromPoint(
      fragment.left + fragment.width / halves,
      fragment.top + fragment.height / halves,
    )
    return answer !== null && element.contains(answer)
  }
  const isOutsidePage = (): boolean =>
    box.bottom <= 0 ||
    box.right <= 0 ||
    box.top >= page.documentElement.clientHeight ||
    box.left >= page.documentElement.clientWidth
  return {
    width: box.width,
    height: box.height,
    clipPath: style.clipPath,
    clip: style.getPropertyValue('clip'),
    ownsCentrePoint: (fragments.length > 1 ? fragments : [box]).some((fragment: PageBox): boolean =>
      isAnsweredAt(fragment),
    ),
    pointerEvents: style.getPropertyValue('pointer-events'),
    visibility: style.getPropertyValue('visibility'),
    isInert: element.closest('[inert]') !== null,
    isOutsidePage: isOutsidePage(),
  }
}
