// What the interaction pass holds a control that carries no text to (rule `graphic-contrast`): enough of what
// it draws stands out from what lies beneath it, as WCAG 1.4.11 Non-text Contrast asks of the visual
// information that identifies a user interface component. An icon button, a marker drawn as a ring, a text
// field drawn as a border: each is told apart from the page only by its graphics, and a pale stroke on a
// pale ground leaves a reader with low vision nothing to find. The browser shoots the page with the control
// shown and again with it hidden, and everything that decides a verdict is here.
//
// WHY PIXELS AGAINST WHAT LIES BENEATH. A control's colours in its stylesheet say nothing about the ground it
// is drawn on, which may be an image, a gradient or a translucent fill, nor about a ring set apart by a halo.
// Hiding the control shows exactly what it covers, so each pixel it paints is compared with the pixel it
// replaced: the pixels that change are the control's, and those that change by at least 3:1 are what a
// reader can see of it.
//
// HOW MUCH MUST CONTRAST. WCAG 1.4.11 asks for the parts needed to identify the control, and names no area.
// A one-pixel line around the control's painted extent outlines its shape, so that much must contrast; a
// thin icon whose strokes cover less than that line passes when half of what it paints contrasts. A text
// label identifies a control by itself and is held to text contrast elsewhere, so a control showing text is
// not judged here, nor one the user agent draws, nor an inactive one, which WCAG 1.4.11 exempts.
import type { ElementBox, FocusRegion } from './focus-reach.js'
import { contrastRatio, luminanceAt, RGBA_BYTES } from './pixel-contrast.js'

/** The contrast the pixels identifying a control need against what they cover (WCAG 1.4.11). */
const GRAPHIC_CONTRAST_RATIO: number = 3

// The input types whose look the user agent draws until the author turns its appearance off.
const AGENT_DRAWN_TYPES: ReadonlySet<string> = new Set<string>(['checkbox', 'radio', 'range', 'color', 'file'])

/** What the browser reports about an operable element, which decides whether its graphics are judged. */
export interface GraphicFacts {
  /** The element's tag name, lower case. */
  readonly tag: string
  /** An input's `type`, lower case, or empty for any other element. */
  readonly type: string
  /** Its computed `appearance`. */
  readonly appearance: string
  /** Whether it shows text a reader can see: its own or a descendant's, or a button input's value. */
  readonly hasVisibleText: boolean
  /** Whether it is `:disabled` or sits under `aria-disabled="true"`. */
  readonly isDisabled: boolean
  /** Whether it or an ancestor carries `inert`. */
  readonly isInert: boolean
  /** Whether it renders, visible and not fully transparent. */
  readonly isRendered: boolean
}

const isAgentDrawn = (facts: GraphicFacts): boolean =>
  (facts.tag === 'select' || (facts.tag === 'input' && AGENT_DRAWN_TYPES.has(facts.type))) &&
  facts.appearance !== 'none'

/**
 * Whether a control is identified by its graphics alone, so its pixels are judged: it is available, renders,
 * shows no text, and draws itself rather than leaving its look to the user agent.
 * @param facts what the browser reports about the control.
 * @returns whether the control is judged.
 */
export const isGraphicCandidate = (facts: GraphicFacts): boolean =>
  facts.isRendered && !facts.isDisabled && !facts.isInert && !facts.hasVisibleText && !isAgentDrawn(facts)

/** One region of the page shot with the control shown and hidden, decoded to RGBA. */
export interface GraphicRenderings {
  readonly shown: ArrayLike<number>
  readonly hidden: ArrayLike<number>
  /** The region's width in device pixels: the length of one row of the RGBA data. */
  readonly width: number
  /** Device pixels per CSS pixel along one side. */
  readonly scale: number
}

/** How much of a control's drawing contrasts with what it covers, in square CSS pixels. */
export interface GraphicMeasure {
  /** The area of the pixels the control paints. */
  readonly painted: number
  /** The area of those that contrast at least 3:1 with what they cover. */
  readonly contrasting: number
  /** The area that must contrast. */
  readonly required: number
}

/** The painted pixels and the box around them, in device pixels. */
interface Painted {
  readonly count: number
  readonly contrasting: number
  readonly left: number
  readonly top: number
  readonly right: number
  readonly bottom: number
}

const NOTHING: Painted = {
  count: 0,
  contrasting: 0,
  left: Number.POSITIVE_INFINITY,
  top: Number.POSITIVE_INFINITY,
  right: Number.NEGATIVE_INFINITY,
  bottom: Number.NEGATIVE_INFINITY,
}

const BLUE_OFFSET: number = 2
const HALF: number = 0.5
// A box at most this many pixels across on a side is all band.
const BAND_SIDES: number = 2
const BAND_CORNERS: number = 4

const isChanged = (renderings: GraphicRenderings, offset: number): boolean =>
  renderings.shown[offset] !== renderings.hidden[offset] ||
  renderings.shown[offset + 1] !== renderings.hidden[offset + 1] ||
  renderings.shown[offset + BLUE_OFFSET] !== renderings.hidden[offset + BLUE_OFFSET]

const paintedOf = (renderings: GraphicRenderings): Painted => {
  const pixels: number = Math.floor(Math.min(renderings.shown.length, renderings.hidden.length) / RGBA_BYTES)
  const width: number = Math.max(1, renderings.width)
  return Array.from({ length: pixels }, (_unused: unknown, pixel: number): number => pixel).reduce(
    (painted: Painted, pixel: number): Painted => {
      const offset: number = pixel * RGBA_BYTES
      if (!isChanged(renderings, offset)) {
        return painted
      }
      const column: number = pixel % width
      const row: number = Math.floor(pixel / width)
      const ratio: number = contrastRatio(luminanceAt(renderings.shown, offset), luminanceAt(renderings.hidden, offset))
      return {
        count: painted.count + 1,
        contrasting: painted.contrasting + (ratio >= GRAPHIC_CONTRAST_RATIO ? 1 : 0),
        left: Math.min(painted.left, column),
        top: Math.min(painted.top, row),
        right: Math.max(painted.right, column),
        bottom: Math.max(painted.bottom, row),
      }
    },
    NOTHING,
  )
}

// The area of a one-pixel line around a box, in square CSS pixels; a box too thin to hold one is all line.
const bandOf = (width: number, height: number): number =>
  width <= BAND_SIDES || height <= BAND_SIDES ? width * height : BAND_SIDES * (width + height) - BAND_CORNERS

/**
 * How much of a control's drawing contrasts with what it covers, and how much must: the area of a one-pixel
 * line around the box of what it paints, or half of what it paints, whichever is less.
 * @param renderings the region with the control shown and hidden.
 * @returns the measure, or undefined when the control paints nothing in the region.
 */
export const measureGraphic = (renderings: GraphicRenderings): GraphicMeasure | undefined => {
  const painted: Painted = paintedOf(renderings)
  if (painted.count === 0 || renderings.scale <= 0) {
    return undefined
  }
  const square: number = renderings.scale * renderings.scale
  const area: number = painted.count / square
  const band: number = bandOf(
    (painted.right - painted.left + 1) / renderings.scale,
    (painted.bottom - painted.top + 1) / renderings.scale,
  )
  return { painted: area, contrasting: painted.contrasting / square, required: Math.min(band, area * HALF) }
}

/**
 * What is wrong with a control that carries no text and too little of whose drawing stands out (rule
 * `graphic-contrast`).
 * @param label the control, as a finding names it.
 * @param measure how much of its drawing contrasts, and how much must.
 * @returns the message, or undefined when enough contrasts.
 */
export const graphicContrastMessage = (label: string, measure: GraphicMeasure): string | undefined =>
  measure.contrasting >= measure.required
    ? undefined
    : `${label} carries no text, and too little of what it draws stands out from what lies beneath it: ` +
      `${String(Math.round(measure.contrasting))} px² of the ${String(Math.round(measure.painted))} px² it ` +
      `paints contrast ${String(GRAPHIC_CONTRAST_RATIO)}:1 with what they cover, and at least ` +
      `${String(Math.ceil(measure.required))} px² must (WCAG 1.4.11)`

/**
 * The region a control's drawing can show in: its box and its descendants' boxes, widened by as far as its
 * outline and shadows reach, in whole CSS pixels and clipped to the viewport.
 * @param boxes the control's box and its descendants' boxes, in the viewport.
 * @param reach how far its outline and shadows reach beyond its box (`indicatorReach`).
 * @param viewport the viewport's size.
 * @param viewport.width its width in CSS pixels.
 * @param viewport.height its height in CSS pixels.
 * @returns the region, or undefined when none of it is in the viewport.
 */
export const graphicRegion = (
  boxes: readonly ElementBox[],
  reach: number,
  viewport: { readonly width: number; readonly height: number },
): FocusRegion | undefined => {
  const drawn: readonly ElementBox[] = boxes.filter(
    (box: ElementBox): boolean => box.right > box.left && box.bottom > box.top,
  )
  if (drawn.length === 0) {
    return undefined
  }
  const lefts: readonly number[] = drawn.map((box: ElementBox): number => box.left)
  const tops: readonly number[] = drawn.map((box: ElementBox): number => box.top)
  const rights: readonly number[] = drawn.map((box: ElementBox): number => box.right)
  const bottoms: readonly number[] = drawn.map((box: ElementBox): number => box.bottom)
  const left: number = Math.max(0, Math.floor(Math.min(...lefts) - reach))
  const top: number = Math.max(0, Math.floor(Math.min(...tops) - reach))
  const right: number = Math.min(viewport.width, Math.ceil(Math.max(...rights) + reach))
  const bottom: number = Math.min(viewport.height, Math.ceil(Math.max(...bottoms) + reach))
  return right > left && bottom > top ? { x: left, y: top, width: right - left, height: bottom - top } : undefined
}
