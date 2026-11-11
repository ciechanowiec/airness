// The area WCAG 2.4.13 Focus Appearance measures a focus indicator against: that of a 2 CSS pixel thick
// perimeter of the unfocused element.
//
// The perimeter is the band 2px deep inside the element's border box, following its border radius, so a
// pill or a round button is measured by its own outline rather than by the rectangle around it:
//
//   band = (w * h - Σ k * rx * ry) - ((w - 4) * (h - 4) - Σ k * (rx - 2) * (ry - 2)),  k = 1 - π / 4
//
// which is `4w + 4h - 16` for a square-cornered box, and the whole box for one too small to hold the band.
// A 2px solid outline drawn outside the element covers more than that band, so it always passes. A curved
// corner is antialiased: the pixels along the edges of an arc are mixed with what lies beneath and contrast
// less than the indicator's colour does, so one pixel along each arc is not asked for. Only the part of the
// element inside the viewport is measured, and a side the viewport cuts off has no band along it, because no
// indicator drawn there can be seen.

/** A box in the viewport, in CSS pixels. */
export interface PerimeterBox {
  readonly left: number
  readonly top: number
  readonly right: number
  readonly bottom: number
}

/** The viewport's size, in CSS pixels. */
interface Viewport {
  readonly width: number
  readonly height: number
}

/** A focused element's shape, as the page reports it. */
export interface FocusShape {
  /** Its box fragments in the viewport: one for a block, one per line for an inline element that wraps. */
  readonly fragments: readonly PerimeterBox[]
  /**
   * Its computed corner radii, top-left, top-right, bottom-right and bottom-left, each as `8px`, `50%` or, for
   * an elliptical corner, `8px 4px`.
   */
  readonly radii: readonly string[]
}

/** The thickness of the perimeter WCAG 2.4.13 measures an indicator against, in CSS pixels. */
export const PERIMETER_THICKNESS: number = 2

const CORNERS: number = 4
// The share of a corner's bounding square an elliptical arc leaves outside itself: all but a quarter ellipse.
const CORNER_SHARE: number = 1 - Math.PI / CORNERS
// A quarter ellipse is about this share of π times the sum of its radii long.
const ARC_SHARE: number = 0.25
const PERCENT: number = 100
// A corner's radius names one length for a circular corner, or two for an elliptical one.
const RADIUS_LENGTHS: number = 2
const UNIT: RegExp = /(?:px|%)$/u

/** One corner's radii along the box's width and height, in CSS pixels. */
interface Corner {
  readonly x: number
  readonly y: number
}

const lengthOf = (token: string, basis: number): number => {
  const value: number = Number(token.trim().replace(UNIT, ''))
  if (!Number.isFinite(value)) {
    return 0
  }
  return token.trimEnd().endsWith('%') ? (basis * value) / PERCENT : value
}

const cornerOf = (radius: string, width: number, height: number): Corner => {
  const [horizontal = '0', vertical = horizontal]: readonly string[] = radius.trim().split(/\s+/u, RADIUS_LENGTHS)
  return { x: Math.max(0, lengthOf(horizontal, width)), y: Math.max(0, lengthOf(vertical, height)) }
}

// The corners in the order the page reports their radii.
const TOP_LEFT: number = 0
const TOP_RIGHT: number = 1
const BOTTOM_RIGHT: number = 2
const BOTTOM_LEFT: number = 3

// How far two radii along one side must shrink to fit it.
const ratio = (side: number, first: number, second: number): number =>
  first + second > side ? side / (first + second) : 1

// Radii that together exceed a side are all scaled down until they fit, as CSS Backgrounds 3 draws them.
const fittedCorners = (radii: readonly string[], width: number, height: number): readonly Corner[] => {
  const corners: readonly Corner[] = Array.from(
    { length: CORNERS },
    (_unused: unknown, index: number): Corner => cornerOf(radii[index] ?? '0', width, height),
  )
  const at = (index: number): Corner => corners[index] ?? { x: 0, y: 0 }
  const scale: number = Math.min(
    ratio(width, at(TOP_LEFT).x, at(TOP_RIGHT).x),
    ratio(width, at(BOTTOM_LEFT).x, at(BOTTOM_RIGHT).x),
    ratio(height, at(TOP_LEFT).y, at(BOTTOM_LEFT).y),
    ratio(height, at(TOP_RIGHT).y, at(BOTTOM_RIGHT).y),
  )
  return corners.map((corner: Corner): Corner => ({ x: corner.x * scale, y: corner.y * scale }))
}

const TOP: number = 0
const RIGHT: number = 1
const BOTTOM: number = 2
const LEFT: number = 3

// The two sides each corner joins, top-left, top-right, bottom-right and bottom-left.
const CORNER_SIDES: readonly (readonly [number, number])[] = [
  [TOP, LEFT],
  [TOP, RIGHT],
  [BOTTOM, RIGHT],
  [BOTTOM, LEFT],
]

const insetOf = (isShown: boolean | undefined): number => (isShown === true ? PERIMETER_THICKNESS : 0)

const bandOf = (fragment: PerimeterBox, radii: readonly string[], viewport: Viewport): number => {
  const left: number = Math.max(0, fragment.left)
  const top: number = Math.max(0, fragment.top)
  const right: number = Math.min(viewport.width, fragment.right)
  const bottom: number = Math.min(viewport.height, fragment.bottom)
  const width: number = right - left
  const height: number = bottom - top
  if (width <= 0 || height <= 0) {
    return 0
  }
  // Which sides of the fragment lie inside the viewport, top, right, bottom and left.
  const sides: readonly boolean[] = [
    top === fragment.top,
    right === fragment.right,
    bottom === fragment.bottom,
    left === fragment.left,
  ]
  const corners: readonly Corner[] = fittedCorners(
    radii,
    fragment.right - fragment.left,
    fragment.bottom - fragment.top,
  ).filter((_corner: Corner, index: number): boolean => {
    const [first, second] = CORNER_SIDES[index] ?? [TOP, LEFT]
    return sides[first] === true && sides[second] === true
  })
  const innerWidth: number = Math.max(0, width - insetOf(sides[LEFT]) - insetOf(sides[RIGHT]))
  const innerHeight: number = Math.max(0, height - insetOf(sides[TOP]) - insetOf(sides[BOTTOM]))
  const cutOuter: number = corners.reduce((sum: number, corner: Corner): number => sum + corner.x * corner.y, 0)
  const cutInner: number = corners.reduce(
    (sum: number, corner: Corner): number =>
      sum + Math.max(0, corner.x - PERIMETER_THICKNESS) * Math.max(0, corner.y - PERIMETER_THICKNESS),
    0,
  )
  const outer: number = width * height - CORNER_SHARE * cutOuter
  const inner: number = innerWidth > 0 && innerHeight > 0 ? innerWidth * innerHeight - CORNER_SHARE * cutInner : 0
  const antialiased: number = corners.reduce(
    (sum: number, corner: Corner): number => sum + ARC_SHARE * Math.PI * (corner.x + corner.y),
    0,
  )
  return Math.max(0, outer - inner - antialiased)
}

/**
 * The area of a 2 CSS pixel thick perimeter of a focused element's visible part, which WCAG 2.4.13 asks its
 * indicator to cover. A wrapped inline element is measured as the line boxes it draws, each with its radii.
 * @param shape the element's fragments and corner radii.
 * @param viewport the viewport's size.
 * @returns the area in CSS pixels, less one pixel along each curved corner.
 */
export const perimeterArea = (shape: FocusShape, viewport: Viewport): number =>
  shape.fragments.reduce(
    (sum: number, fragment: PerimeterBox): number => sum + bandOf(fragment, shape.radii, viewport),
    0,
  )
