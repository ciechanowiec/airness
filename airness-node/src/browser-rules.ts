// The rules that judge rendered text, media, fonts, focus and disclosures, over values measured in the
// browser. Each threshold is a fixed constant: a false positive is a defect of the rule and is repaired
// here, never by a project setting.
import type { CandidateFinding } from './browser-exemptions.js'
import { findJustifiedText, findLongLines } from './layout-lines.js'
import {
  type FontFaceFacts,
  hasArea,
  isInAriaHidden,
  isInClip,
  type LayoutNode,
  type LayoutRect,
  type LayoutSnapshot,
  type MediaFacts,
} from './layout-snapshot.js'

/** The tolerance every geometric comparison allows, in CSS pixels. */
const GEOMETRY_TOLERANCE: number = 1

/** The smallest rendered text size, in CSS pixels. */
const MINIMUM_TEXT_SIZE: number = 12

/** How far an image may be enlarged beyond its natural resolution before it is judged upscaled. */
const UPSCALE_ALLOWANCE: number = 0.1

const PERCENT: number = 100

// An image carrying more pixels than any screen draws is decoded whole and mostly thrown away, and on a phone
// the decoded bitmap is what fills memory: four bytes a pixel, whatever the file's compression. The densest
// screens in use draw three device pixels per CSS pixel, so a source denser than that is waste on every
// device a page can reach. A small icon served a little large wastes nothing worth a finding, so only a
// source that decodes at least a mebipixel beyond what the densest screen shows is judged.

/** The densest screen in use, in device pixels per CSS pixel. */
const MAXIMUM_DEVICE_PIXEL_RATIO: number = 3

/** How far beyond that density an image may go before it is judged oversized. */
const OVERSIZE_ALLOWANCE: number = 0.1

/** The decoded pixels beyond what the densest screen shows that make an oversized image a finding. */
const OVERSIZE_FLOOR_PIXELS: number = 1_048_576

const PIXELS_PER_MEGAPIXEL: number = 1_000_000

/** The device pixel ratio every viewport is rendered at. */
export const DEVICE_SCALE_FACTOR: number = 1

/** The width of the ring around a focused element whose pixels the focus-visible rule compares. */
export const FOCUS_RING_WIDTH: number = 4

/** The element rules of the resting scan beyond geometry: T4, M1 to M5, and WCAG 1.4.8's text presentation. */
export const RESTING_ELEMENT_RULES: readonly string[] = [
  'small-text',
  'image-load',
  'image-distorted',
  'image-upscaled',
  'image-oversized',
  'media-space',
  'text-justified',
  'line-length',
]

/** The page rule of the resting scan: T6. */
export const FONT_LOAD_RULE: string = 'font-load'

const isRendered = (snapshot: LayoutSnapshot, node: LayoutNode): boolean =>
  node.visibility === 'visible' &&
  node.rects.some((rect: LayoutRect): boolean => hasArea(rect)) &&
  !isInClip(snapshot, node) &&
  !isInAriaHidden(snapshot, node)

/**
 * Text rendered smaller than 12 CSS pixels (rule `small-text`).
 * @param snapshot the measured page.
 * @returns one candidate per element whose own text is too small.
 */
const findSmallText = (snapshot: LayoutSnapshot): readonly CandidateFinding[] =>
  snapshot.nodes
    .filter(
      (node: LayoutNode): boolean =>
        node.textRects.some((rect: LayoutRect): boolean => hasArea(rect)) &&
        node.fontSize > 0 &&
        node.fontSize < MINIMUM_TEXT_SIZE &&
        isRendered(snapshot, node),
    )
    .map(
      (node: LayoutNode): CandidateFinding => ({
        rule: 'small-text',
        subjects: [node],
        message: `text rendered at ${String(node.fontSize)}px, below ${String(MINIMUM_TEXT_SIZE)}px: ${node.label}`,
      }),
    )

interface MediaNode {
  readonly node: LayoutNode
  readonly media: MediaFacts
}

const renderedMedia = (snapshot: LayoutSnapshot): readonly MediaNode[] =>
  snapshot.nodes.flatMap((node: LayoutNode): readonly MediaNode[] =>
    node.media !== undefined && isRendered(snapshot, node) ? [{ node, media: node.media }] : [],
  )

const isImage = (node: LayoutNode): boolean => node.tag === 'img'

/**
 * Visible images that did not load (rule `image-load`).
 * @param snapshot the measured page.
 * @returns one candidate per incomplete or empty image.
 */
const findUnloadedImages = (snapshot: LayoutSnapshot): readonly CandidateFinding[] =>
  renderedMedia(snapshot)
    .filter(
      ({ node, media }: MediaNode): boolean =>
        isImage(node) && (!media.isComplete || (!media.isVector && media.naturalWidth === 0)),
    )
    .map(
      ({ node, media }: MediaNode): CandidateFinding => ({
        rule: 'image-load',
        subjects: [node],
        message:
          `image did not load (complete ${String(media.isComplete)}, ` +
          `natural width ${String(media.naturalWidth)}): ${node.label}`,
      }),
    )

const hasRaster = (media: MediaFacts): boolean =>
  !media.isVector && media.naturalWidth > 0 && media.naturalHeight > 0 && media.contentWidth > 0

/**
 * Images stretched out of their natural aspect ratio (rule `image-distorted`).
 * @param snapshot the measured page.
 * @returns one candidate per image whose `object-fit: fill` rendering changes its proportions.
 */
const findDistortedImages = (snapshot: LayoutSnapshot): readonly CandidateFinding[] =>
  renderedMedia(snapshot).flatMap(({ node, media }: MediaNode): readonly CandidateFinding[] => {
    if (!(isImage(node) && hasRaster(media)) || media.objectFit !== 'fill') {
      return []
    }
    const expectedHeight: number = (media.contentWidth * media.naturalHeight) / media.naturalWidth
    return Math.abs(expectedHeight - media.contentHeight) > GEOMETRY_TOLERANCE
      ? [
          {
            rule: 'image-distorted',
            subjects: [node],
            message:
              `image rendered at ${String(media.contentWidth)}x${String(media.contentHeight)}px from a ` +
              `${String(media.naturalWidth)}x${String(media.naturalHeight)}px source, out of proportion: ${node.label}`,
          },
        ]
      : []
  })

const displayedScale = (media: MediaFacts): number => {
  const horizontal: number = media.contentWidth / media.naturalWidth
  const vertical: number = media.contentHeight / media.naturalHeight
  switch (media.objectFit) {
    case 'cover': {
      return Math.max(horizontal, vertical)
    }
    case 'contain': {
      return Math.min(horizontal, vertical)
    }
    case 'none': {
      return 1
    }
    case 'scale-down': {
      return Math.min(1, horizontal, vertical)
    }
    default: {
      return Math.max(horizontal, vertical)
    }
  }
}

/**
 * Images displayed larger than their source resolution allows (rule `image-upscaled`).
 * @param snapshot the measured page.
 * @returns one candidate per raster image enlarged by more than 10%.
 */
const findUpscaledImages = (snapshot: LayoutSnapshot): readonly CandidateFinding[] =>
  renderedMedia(snapshot).flatMap(({ node, media }: MediaNode): readonly CandidateFinding[] => {
    if (!(isImage(node) && hasRaster(media))) {
      return []
    }
    const scale: number = displayedScale(media) * DEVICE_SCALE_FACTOR
    return scale > 1 + UPSCALE_ALLOWANCE
      ? [
          {
            rule: 'image-upscaled',
            subjects: [node],
            message:
              `image source ${String(media.naturalWidth)}x${String(media.naturalHeight)}px displayed at ` +
              `${String(Math.round(scale * PERCENT))}% in a ` +
              `${String(media.contentWidth)}x${String(media.contentHeight)}px ` +
              `box; serve a larger source: ${node.label}`,
          },
        ]
      : []
  })

/**
 * Images whose source carries more pixels than the densest screen draws in their box (rule `image-oversized`).
 * @param snapshot the measured page.
 * @returns one candidate per raster image denser than three source pixels per CSS pixel by more than 10%,
 * whose surplus over that density is at least a mebipixel.
 */
const findOversizedImages = (snapshot: LayoutSnapshot): readonly CandidateFinding[] =>
  renderedMedia(snapshot).flatMap(({ node, media }: MediaNode): readonly CandidateFinding[] => {
    if (!(isImage(node) && hasRaster(media))) {
      return []
    }
    const density: number = 1 / displayedScale(media)
    const pixels: number = media.naturalWidth * media.naturalHeight
    const shown: number = MAXIMUM_DEVICE_PIXEL_RATIO / density
    const surplus: number = pixels * (1 - shown * shown)
    return density > MAXIMUM_DEVICE_PIXEL_RATIO * (1 + OVERSIZE_ALLOWANCE) && surplus >= OVERSIZE_FLOOR_PIXELS
      ? [
          {
            rule: 'image-oversized',
            subjects: [node],
            message:
              `image source ${String(media.naturalWidth)}x${String(media.naturalHeight)}px fills a ` +
              `${String(media.contentWidth)}x${String(media.contentHeight)}px box at ` +
              `${density.toFixed(1)} source pixels per CSS pixel, beyond the ` +
              `${String(MAXIMUM_DEVICE_PIXEL_RATIO)} any screen draws, so ` +
              `${(surplus / PIXELS_PER_MEGAPIXEL).toFixed(1)} megapixels are decoded and never shown; serve a ` +
              `smaller source, or a srcset whose sizes match the box: ${node.label}`,
          },
        ]
      : []
  })

const OUT_OF_FLOW: ReadonlySet<string> = new Set<string>(['absolute', 'fixed'])

/**
 * Media whose box changes once its source loads, shifting what follows it (rule `media-space`).
 * @param snapshot the measured page.
 * @returns one candidate per in-flow element that does not reserve its rendered size.
 */
const findUnreservedMedia = (snapshot: LayoutSnapshot): readonly CandidateFinding[] =>
  renderedMedia(snapshot).flatMap(({ node, media }: MediaNode): readonly CandidateFinding[] => {
    if (OUT_OF_FLOW.has(node.position)) {
      return []
    }
    const rendered: LayoutRect | undefined = node.rects[0]
    if (rendered === undefined) {
      return []
    }
    const width: number = rendered.right - rendered.left
    const height: number = rendered.bottom - rendered.top
    return Math.abs(media.reservedWidth - width) > GEOMETRY_TOLERANCE ||
      Math.abs(media.reservedHeight - height) > GEOMETRY_TOLERANCE
      ? [
          {
            rule: 'media-space',
            subjects: [node],
            message:
              `${node.tag} reserves ${String(media.reservedWidth)}x${String(media.reservedHeight)}px before loading ` +
              `and takes ${String(width)}x${String(height)}px after; give it width and height attributes or an ` +
              `aspect-ratio: ${node.label}`,
          },
        ]
      : []
  })

const GENERIC_FAMILIES: ReadonlySet<string> = new Set<string>([
  'serif',
  'sans-serif',
  'monospace',
  'cursive',
  'fantasy',
  'system-ui',
  'ui-serif',
  'ui-sans-serif',
  'ui-monospace',
  'ui-rounded',
  'math',
  'emoji',
  'fangsong',
])

const familyName = (family: string): string =>
  family
    .trim()
    .replaceAll(/^["']|["']$/gu, '')
    .toLowerCase()

const familiesOf = (stack: string): readonly string[] =>
  stack
    .split(',')
    .map((family: string): string => familyName(family))
    .filter((family: string): boolean => family.length > 0 && !GENERIC_FAMILIES.has(family))

/**
 * The declared font families the page's text asks for first.
 * @param snapshot the measured page.
 * @returns for each text element, the first family in its stack that has a `@font-face`.
 */
const usedFontFamilies = (snapshot: LayoutSnapshot): readonly string[] => {
  const declared: ReadonlySet<string> = new Set<string>(
    snapshot.fontFaces.map((face: FontFaceFacts): string => familyName(face.family)),
  )
  return [
    ...new Set<string>(
      snapshot.nodes
        .filter((node: LayoutNode): boolean => node.textRects.length > 0)
        .flatMap((node: LayoutNode): readonly string[] => {
          const first: string | undefined = familiesOf(node.fontFamily).find((family: string): boolean =>
            declared.has(family),
          )
          return first === undefined ? [] : [first]
        }),
    ),
  ]
}

/**
 * The `@font-face` families the page's text uses that did not load (rule `font-load`).
 *
 * A face the browser never needed stays `unloaded`, which is not a defect. A used family with no face
 * loaded, or with a face that failed or is still loading after the page settled, is one. This proves the
 * face loaded, not that every glyph avoided a fallback.
 * @param snapshot the measured page.
 * @returns one message per family.
 */
export const findUnloadedFonts = (snapshot: LayoutSnapshot): readonly string[] =>
  usedFontFamilies(snapshot).flatMap((family: string): readonly string[] => {
    const faces: readonly FontFaceFacts[] = snapshot.fontFaces.filter(
      (face: FontFaceFacts): boolean => familyName(face.family) === family,
    )
    const failed: readonly FontFaceFacts[] = faces.filter(
      (face: FontFaceFacts): boolean => face.status === 'error' || face.status === 'loading',
    )
    if (failed.length > 0) {
      return failed.map(
        (face: FontFaceFacts): string =>
          `@font-face "${face.family}" ${face.weight} ${face.style} is ${face.status} after the page settled`,
      )
    }
    return faces.some((face: FontFaceFacts): boolean => face.status === 'loaded')
      ? []
      : [`@font-face "${family}" is used by the page's text and none of its faces loaded`]
  })

/** One point of a focused element, and whether fixed or sticky content covers it. */
export interface FocusSample {
  readonly isCovered: boolean
  /** The label of what covers it, empty when nothing does. */
  readonly coveredBy: string
}

// A lattice no coarser than 12px between points sees any cover a reader would notice: a sticky header over
// the top of a card, a cookie banner over its foot, a badge over a corner. The outer points sit 2px inside
// the edges, where rounding cannot carry a neighbour's pixel in. A very large element is sampled more coarsely
// rather than at thousands of points.
const SAMPLE_SPACING: number = 12
const MAXIMUM_SAMPLE_STEPS: number = 32
const MAXIMUM_SAMPLE_INSET: number = 2
// A box too small for the inset keeps its points inside its middle half.
const SAMPLE_INSET_SHARE: number = 0.25

const samplesAlong = (start: number, end: number): readonly number[] => {
  const steps: number = Math.min(MAXIMUM_SAMPLE_STEPS, Math.max(1, Math.ceil((end - start) / SAMPLE_SPACING)))
  return Array.from(
    { length: steps + 1 },
    (_unused: unknown, step: number): number => start + ((end - start) * step) / steps,
  )
}

/**
 * The points of a focused element at which to ask what is drawn on top of it (rule `focus-obscured`).
 * @param box its box in the viewport: left, top, right and bottom, in CSS pixels.
 * @param viewport the viewport's size.
 * @param viewport.width its width in CSS pixels.
 * @param viewport.height its height in CSS pixels.
 * @returns a lattice over the part inside the viewport, or nothing when less than a pixel of it is there.
 */
export const focusSamplePoints = (
  box: readonly [number, number, number, number],
  viewport: { readonly width: number; readonly height: number },
): readonly (readonly [number, number])[] => {
  const [boxLeft, boxTop, boxRight, boxBottom] = box
  const left: number = Math.max(0, boxLeft)
  const top: number = Math.max(0, boxTop)
  const right: number = Math.min(viewport.width, boxRight)
  const bottom: number = Math.min(viewport.height, boxBottom)
  if (right - left < 1 || bottom - top < 1) {
    return []
  }
  const inset: number = Math.min(
    MAXIMUM_SAMPLE_INSET,
    (right - left) * SAMPLE_INSET_SHARE,
    (bottom - top) * SAMPLE_INSET_SHARE,
  )
  return samplesAlong(top + inset, bottom - inset).flatMap((pointY: number): readonly (readonly [number, number])[] =>
    samplesAlong(left + inset, right - inset).map((pointX: number): readonly [number, number] => [pointX, pointY]),
  )
}

/** What hides part of a focused element. */
export interface FocusObscuring {
  /** The labels of the fixed or sticky content covering it. */
  readonly coveredBy: string
  /** Whether every sample is covered, so none of the element shows. */
  readonly isEntire: boolean
}

/**
 * Whether any part of a focused element is hidden by fixed or sticky content (rule `focus-obscured`). WCAG
 * 2.4.12 Focus Not Obscured (Enhanced) allows no part of it hidden, where 2.4.11 allows all but the whole.
 * @param samples points spread over the part of the element inside the viewport.
 * @returns what covers it, or undefined when every sample of the element shows.
 */
export const obscuringContent = (samples: readonly FocusSample[]): FocusObscuring | undefined => {
  const covered: readonly FocusSample[] = samples.filter((sample: FocusSample): boolean => sample.isCovered)
  return covered.length === 0
    ? undefined
    : {
        coveredBy: [...new Set<string>(covered.map((sample: FocusSample): string => sample.coveredBy))].join(', '),
        isEntire: covered.length === samples.length,
      }
}

/**
 * Whether two renderings of the same region differ, which is what a visible focus indicator does.
 * @param unfocused the region's pixels with the element blurred.
 * @param focused the same region with the element focused by keyboard.
 * @returns true when at least one byte differs.
 */
export const hasVisibleFocus = (unfocused: ArrayLike<number>, focused: ArrayLike<number>): boolean =>
  unfocused.length !== focused.length ||
  Array.from(unfocused).some((value: number, index: number): boolean => value !== focused[index])

/** The contrast a focus indicator's changed pixels need against what they replaced (WCAG 2.4.13). */
export const FOCUS_CONTRAST_RATIO: number = 3

const RGBA_BYTES: number = 4
const CHANNEL_MAX: number = 255
const LINEAR_KNEE: number = 0.04045
const LINEAR_SLOPE: number = 12.92
const GAMMA_OFFSET: number = 0.055
const GAMMA_SCALE: number = 1.055
const GAMMA: number = 2.4
const RED_WEIGHT: number = 0.2126
const GREEN_WEIGHT: number = 0.7152
const BLUE_WEIGHT: number = 0.0722
const FLARE: number = 0.05
const BLUE_OFFSET: number = 2
// The share of the element's longer side a contrasting one-pixel line of the browser's own ring must cover.
const LINE_SHARE: number = 0.5

const linearChannel = (value: number): number => {
  const fraction: number = value / CHANNEL_MAX
  return fraction <= LINEAR_KNEE ? fraction / LINEAR_SLOPE : ((fraction + GAMMA_OFFSET) / GAMMA_SCALE) ** GAMMA
}

const luminanceAt = (pixels: ArrayLike<number>, offset: number): number =>
  RED_WEIGHT * linearChannel(pixels[offset] ?? 0) +
  GREEN_WEIGHT * linearChannel(pixels[offset + 1] ?? 0) +
  BLUE_WEIGHT * linearChannel(pixels[offset + BLUE_OFFSET] ?? 0)

/**
 * The WCAG contrast ratio between two relative luminances.
 * @param first one luminance.
 * @param second the other.
 * @returns the ratio, from 1 to 21.
 */
const contrastRatio = (first: number, second: number): number =>
  (Math.max(first, second) + FLARE) / (Math.min(first, second) + FLARE)

/** How large a focused element's indicator must be, measured on the element's visible part in CSS pixels. */
export interface FocusArea {
  /** The area of a 2 CSS pixel thick perimeter of the element (`perimeterArea`), less its curves' antialiasing. */
  readonly perimeter: number
  /** The longer side of the element's visible box. */
  readonly extent: number
}

/**
 * The area a focus indicator's contrasting pixels must cover. WCAG 2.4.13 Focus Appearance asks for the area of
 * a 2 CSS pixel thick perimeter of the unfocused element, so a 2px solid outline or a ring of that width
 * passes and a 1px outline, an underline or a thin ring does not. It exempts an indicator the browser draws
 * and the author cannot adjust: the browser's own ring, `outline-style: auto`, which is held only to a
 * contrasting one-pixel line along half the element's longer side, as a visible focus requires.
 * @param area the element's perimeter area and longer side.
 * @param outlineStyle the focused element's computed `outline-style`.
 * @returns the area in CSS pixels.
 */
export const requiredFocusArea = (area: FocusArea, outlineStyle: string): number =>
  outlineStyle === 'auto' ? area.extent * LINE_SHARE : area.perimeter

/** A focused element's region rendered focused and unfocused, decoded to RGBA. */
export interface FocusRenderings {
  readonly unfocused: ArrayLike<number>
  readonly focused: ArrayLike<number>
  /** Device pixels per CSS pixel along one side. */
  readonly scale: number
  /** The area, in CSS pixels, the contrasting pixels must cover (`requiredFocusArea`). */
  readonly required: number
}

/**
 * Whether a focus indicator contrasts with what it replaced over enough of the page (rule `focus-contrast`):
 * the pixels whose focused colour contrasts at least 3:1 with their unfocused colour must cover the required
 * area, so a faint tint or glow never passes, however large.
 * @param renderings the two renderings, their scale, and the area required.
 * @returns true when enough of the change contrasts.
 */
export const hasContrastingFocus = (renderings: FocusRenderings): boolean => {
  const { unfocused, focused, scale, required }: FocusRenderings = renderings
  const pixels: number = Math.floor(Math.min(unfocused.length, focused.length) / RGBA_BYTES)
  const contrasting: number = Array.from({ length: pixels }, (_unused: unknown, pixel: number): number => pixel).filter(
    (pixel: number): boolean =>
      contrastRatio(luminanceAt(unfocused, pixel * RGBA_BYTES), luminanceAt(focused, pixel * RGBA_BYTES)) >=
      FOCUS_CONTRAST_RATIO,
  ).length
  return contrasting / (scale * scale) >= required
}

/** Where a keyboard-focused element sits, for the order focus moves in (rule `focus-order`). */
export interface FocusPosition {
  /** Its box in document coordinates, in CSS pixels. */
  readonly top: number
  readonly bottom: number
  readonly left: number
  readonly right: number
  /** The landmark it sits in; positions are compared only within one. */
  readonly landmark: string
  /**
   * Whether its position says nothing about order: it is in a dialog, in fixed or sticky content whose
   * place moves with scrolling, or visually hidden.
   */
  readonly isUnplaced: boolean
  readonly isRightToLeft: boolean
}

/**
 * Whether focus moved backwards (rule `focus-order`): the next element sits entirely above the previous
 * one in the same landmark. A next element in a column further along the line is not backwards, because
 * that is where a reader goes after the foot of a column.
 * @param previous where the previously focused element sits.
 * @param next where the next focused element sits.
 * @returns true when the move goes back up the page.
 */
export const isFocusBackwards = (previous: FocusPosition, next: FocusPosition): boolean => {
  if (previous.isUnplaced || next.isUnplaced || previous.landmark !== next.landmark) {
    return false
  }
  const isFurtherAlong: boolean = previous.isRightToLeft ? next.right <= previous.left : next.left >= previous.right
  return next.bottom <= previous.top && !isFurtherAlong
}

/** What driving one opened disclosure with the keyboard showed. */
export interface DisclosureProbe {
  /** Whether activation actually opened the disclosure. */
  readonly wasOpened: boolean
  /** Whether the control declares a popup, which the keyboard pattern says `Escape` dismisses. */
  readonly isPopup: boolean
  /** Whether `Escape` closed it. */
  readonly closedOnEscape: boolean
  /** Whether focus was on the control after it closed. */
  readonly focusReturned: boolean
}

const escapeDefects = (probe: DisclosureProbe): readonly string[] => {
  if (!probe.isPopup) {
    return []
  }
  if (!probe.closedOnEscape) {
    return ['Escape does not close it']
  }
  return probe.focusReturned ? [] : ['focus does not return to its control after Escape']
}

/**
 * What is wrong with how a disclosure answers the keyboard (rule `disclosure-keyboard`).
 *
 * Only a popup is required to close on `Escape`: the disclosure pattern for an accordion or a native
 * `<details>` has no such key, and judging one by it would report the platform. A dialog the disclosure
 * opened is judged by the dialog pattern instead (rule `dialog-keyboard`), which closes it on `Escape` too.
 * @param probe what the keyboard showed.
 * @returns one message per defect.
 */
export const disclosureDefects = (probe: DisclosureProbe): readonly string[] =>
  probe.wasOpened ? escapeDefects(probe) : ['activation did not open the disclosure']

const isSameRect = (left: LayoutRect, right: LayoutRect): boolean =>
  Math.abs(left.left - right.left) <= GEOMETRY_TOLERANCE &&
  Math.abs(left.top - right.top) <= GEOMETRY_TOLERANCE &&
  Math.abs(left.right - right.right) <= GEOMETRY_TOLERANCE &&
  Math.abs(left.bottom - right.bottom) <= GEOMETRY_TOLERANCE

type GeometryNode = Pick<LayoutNode, 'label' | 'rects'> & Partial<Pick<LayoutNode, 'textRects'>>
interface GeometrySnapshot {
  readonly nodes: readonly GeometryNode[]
}

const isSameNode = (left: GeometryNode, right: GeometryNode): boolean =>
  left.label === right.label &&
  left.rects.length === right.rects.length &&
  left.rects.every((rect: LayoutRect, index: number): boolean => {
    const other: LayoutRect | undefined = right.rects[index]
    return other !== undefined && isSameRect(rect, other)
  })

/**
 * Where two measurements of a settled page disagree (rule `page-not-stable`).
 * @param first the first measurement.
 * @param second the second, taken after it.
 * @returns what moved, or undefined when the geometry held within the tolerance.
 */
export const instability = (first: GeometrySnapshot, second: GeometrySnapshot): string | undefined => {
  // Streaming frameworks remove hidden transport containers and templates after revealing content.
  // Their disappearance changes no layout; a node with a box or text still participates in the comparison.
  const hasLayout = (node: GeometryNode): boolean => node.rects.length > 0 || (node.textRects?.length ?? 0) > 0
  const before: readonly GeometryNode[] = first.nodes.filter((node: GeometryNode): boolean => hasLayout(node))
  const after: readonly GeometryNode[] = second.nodes.filter((node: GeometryNode): boolean => hasLayout(node))
  if (before.length !== after.length) {
    return `the element count changed from ${String(before.length)} to ${String(after.length)}`
  }
  const moved: GeometryNode | undefined = before.find((node: GeometryNode, index: number): boolean => {
    const other: GeometryNode | undefined = after[index]
    return other === undefined || !isSameNode(node, other)
  })
  return moved === undefined ? undefined : `${moved.label} moved between two measurements`
}

/**
 * Every element candidate the resting scan's text and media rules find on one page.
 * @param snapshot the measured page.
 * @returns the candidates of T4, M1 to M5, `text-justified` and `line-length`.
 */
export const restingCandidates = (snapshot: LayoutSnapshot): readonly CandidateFinding[] => [
  ...findSmallText(snapshot),
  ...findUnloadedImages(snapshot),
  ...findDistortedImages(snapshot),
  ...findUpscaledImages(snapshot),
  ...findOversizedImages(snapshot),
  ...findUnreservedMedia(snapshot),
  ...findJustifiedText(snapshot),
  ...findLongLines(snapshot),
]
