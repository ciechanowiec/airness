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
// Pixels can't tell which part that is: an icon on a light fill is identified by the icon, not by the fill's
// edge. So a control passes once a dot of it three CSS pixels square stands out, or half of what it paints
// when it paints less, and fails when nothing of it a reader could find does: a pale stroke on a pale
// ground, a light ring on a light fill. A text label identifies a control by itself and is held to text
// contrast elsewhere, so a control showing text is not judged here; nor is a field, which its label and
// its place in a form identify; nor a control the user agent draws, nor an inactive one, which WCAG 1.4.11
// exempts.
import type { ElementBox, FocusRegion } from './focus-reach.js'
import { contrastRatio, luminanceAt, RGBA_BYTES } from './pixel-contrast.js'

/** The contrast the pixels identifying a control need against what they cover (WCAG 1.4.11). */
const GRAPHIC_CONTRAST_RATIO: number = 3

// The input types an author draws as a graphic of their own once the appearance the user agent gives them is
// turned off; every other input, like a textarea or a select, is a field a reader types into or picks from.
const DRAWN_TYPES: ReadonlySet<string> = new Set<string>(['checkbox', 'radio'])

// The area, in square CSS pixels, a reader must be able to find of a control: a dot three pixels square.
const FINDABLE_AREA: number = 9

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

const isField = (facts: GraphicFacts): boolean =>
  facts.tag === 'select' ||
  facts.tag === 'textarea' ||
  (facts.tag === 'input' && !(DRAWN_TYPES.has(facts.type) && facts.appearance === 'none'))

/**
 * Whether a control is identified by its graphics alone, so its pixels are judged: it is available, renders,
 * shows no text, and is no field, other than a checkbox or a radio button its author draws.
 * @param facts what the browser reports about the control.
 * @returns whether the control is judged.
 */
export const isGraphicCandidate = (facts: GraphicFacts): boolean =>
  facts.isRendered && !facts.isDisabled && !facts.isInert && !facts.hasVisibleText && !isField(facts)

/** One region of the page shot with the control shown and hidden, decoded to RGBA. */
export interface GraphicRenderings {
  readonly shown: ArrayLike<number>
  readonly hidden: ArrayLike<number>
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

/** How many pixels the control paints, and how many of those contrast, in device pixels. */
interface Painted {
  readonly count: number
  readonly contrasting: number
}

const NOTHING: Painted = { count: 0, contrasting: 0 }

const BLUE_OFFSET: number = 2
const HALF: number = 0.5

const isChanged = (renderings: GraphicRenderings, offset: number): boolean =>
  renderings.shown[offset] !== renderings.hidden[offset] ||
  renderings.shown[offset + 1] !== renderings.hidden[offset + 1] ||
  renderings.shown[offset + BLUE_OFFSET] !== renderings.hidden[offset + BLUE_OFFSET]

const paintedOf = (renderings: GraphicRenderings): Painted => {
  const pixels: number = Math.floor(Math.min(renderings.shown.length, renderings.hidden.length) / RGBA_BYTES)
  return Array.from({ length: pixels }, (_unused: unknown, pixel: number): number => pixel).reduce(
    (painted: Painted, pixel: number): Painted => {
      const offset: number = pixel * RGBA_BYTES
      if (!isChanged(renderings, offset)) {
        return painted
      }
      const ratio: number = contrastRatio(luminanceAt(renderings.shown, offset), luminanceAt(renderings.hidden, offset))
      return {
        count: painted.count + 1,
        contrasting: painted.contrasting + (ratio >= GRAPHIC_CONTRAST_RATIO ? 1 : 0),
      }
    },
    NOTHING,
  )
}

/**
 * How much of a control's drawing contrasts with what it covers, and how much must: a dot three CSS pixels
 * square, or half of what it paints, whichever is less.
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
  return { painted: area, contrasting: painted.contrasting / square, required: Math.min(FINDABLE_AREA, area * HALF) }
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
