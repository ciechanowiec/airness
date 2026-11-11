// The page function that reads where a control is and what answers a pointer at its centre, shared by the
// interaction pass's hover and by its pointer-only check, which bring the control into view differently, and by
// the scanner, which excuses from target size what the visually-hidden pattern clips away.
import type { HitTargetProbe } from './a11y-hit-target.js'

// The browser surface the page function below touches, declared for the reason `settle.ts` records. It names
// the native DOM types rather than the aliases `sweep-shared.ts` keeps, because the scanner imports this module
// and `sweep-shared.ts` imports the scanner.
declare const getComputedStyle: (element: Element) => CSSStyleDeclaration

/**
 * Read an element's box and hit target together, in the page, without advancing a scroll-driven scene while
 * measuring it: the interaction pass scrolls a control into view first, the pointer-only check scrolls only the
 * window.
 * @param element the element, already in view.
 * @returns what the hit-target classification reads.
 */
export const hitTargetInPage = (element: Element): HitTargetProbe => {
  const halves: number = 2
  const box: DOMRect = element.getBoundingClientRect()
  const style: CSSStyleDeclaration = getComputedStyle(element)
  const atCentre: Element | null = element.ownerDocument.elementFromPoint(
    box.left + box.width / halves,
    box.top + box.height / halves,
  )
  return {
    width: box.width,
    height: box.height,
    clipPath: style.clipPath,
    clip: style.getPropertyValue('clip'),
    ownsCentrePoint: atCentre !== null && element.contains(atCentre),
    pointerEvents: style.getPropertyValue('pointer-events'),
    visibility: style.getPropertyValue('visibility'),
    isInert: element.closest('[inert]') !== null,
    isOutsidePage:
      box.bottom <= 0 ||
      box.right <= 0 ||
      box.top >= element.ownerDocument.documentElement.clientHeight ||
      box.left >= element.ownerDocument.documentElement.clientWidth,
  }
}
