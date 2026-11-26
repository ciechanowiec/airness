// What the rendering pass holds a page to: what it draws stays cheap to draw again as it moves. Two rules judge
// it, each from facts the browser reports rather than from a frame rate, which changes with the machine and
// the moment: a filter or mask redrawn on every frame (rule `filter-in-motion`), and composited layers that
// hold more of the device's graphics memory than the page's content needs (rule `layer-area`). The page is
// loaded with normal motion and nothing held still, at a phone's width and a desktop's, since a page may turn
// its effects off on a small screen; it is sampled still and across two scroll steps. Everything that decides
// a verdict is here.
//
// WHY A MOVING FILTER IS A DEFECT. A blur, a drop shadow, a backdrop filter or a mask is drawn from the pixels
// of everything under it. While those pixels stay put, the browser keeps the result and moves it whole; once
// anything inside the filtered element moves, or the filter itself is animated, it is computed again on every
// frame. A large blur that moves costs the same even with nothing inside it. On a slow graphics chip that
// work is what drops a page's frames and leaves a panel half drawn while it climbs over another.
//
// WHY LAYERS ARE COUNTED BY AREA. A layer of its own lets the compositor move an element without painting it
// again, and costs a texture the size of the element. A few small ones cost nothing a device notices; many, or
// one far larger than the screen, hold so much memory that a phone's browser may discard the page. Only the
// part of each layer within a viewport of what is shown counts, since a browser draws no further ahead; the
// document's own layer, its scrolling areas and the viewport are what every page has, and are not counted.

import type { Viewport } from './browser-findings.js'
import { MOTION_MIN_AREA } from './reduced-motion-policy.js'

/** The rules the rendering pass judges, on one route per template rather than on every route. */
export const RENDERING_RULES: readonly string[] = ['filter-in-motion', 'layer-area']

// A filtered property animated by the element's own keyframes, as CSS names it.
const FILTERED_PROPERTY: RegExp = /(?:^|-)(?:filter|mask)/u

/** What the browser reports about an element drawn through a filter, a backdrop filter or a mask. */
export interface FilterFacts {
  /** Its border box, in CSS pixels. */
  readonly width: number
  readonly height: number
  /** Whether its `filter` blurs or casts a drop shadow. */
  readonly hasBlur: boolean
  /** Whether it filters what lies behind it (`backdrop-filter`). */
  readonly hasBackdrop: boolean
  /** Whether it is masked (`mask-image`, or its prefixed name). */
  readonly hasMask: boolean
  /** Whether it was animated, or its placement changed, while the page was sampled. */
  readonly isMoving: boolean
  /** Whether an element inside it was animated, or its placement changed, while the page was sampled. */
  readonly hasMovingInside: boolean
  /** The CSS properties its own running animations change. */
  readonly animatedProperties: readonly string[]
}

const REPAIR: string = 'draw the effect once, as an image or a still gradient, or keep what moves outside it'

// The filter and mask properties the element's own animations change.
const animatedFilters = (facts: FilterFacts): readonly string[] =>
  facts.animatedProperties.filter((property: string): boolean => FILTERED_PROPERTY.test(property))

// How a finding names the effects an element is drawn through.
const effectsOf = (facts: FilterFacts): string =>
  [
    { name: 'filter', isSet: facts.hasBlur },
    { name: 'backdrop filter', isSet: facts.hasBackdrop },
    { name: 'mask', isSet: facts.hasMask },
  ]
    .filter((effect: { readonly name: string; readonly isSet: boolean }): boolean => effect.isSet)
    .map((effect: { readonly name: string; readonly isSet: boolean }): string => effect.name)
    .join(' and ') || 'filter'

// How many viewports a moving blur covers, or zero when it is no larger than one, does not blur or stays put.
const movingBlurViewports = (facts: FilterFacts, viewport: Viewport): number => {
  const screens: number = (facts.width * facts.height) / (viewport.width * viewport.height)
  return (facts.hasBlur || facts.hasBackdrop) && facts.isMoving && screens > 1 ? screens : 0
}

/**
 * What is wrong with an element drawn through a filter or a mask (rule `filter-in-motion`): its own animation
 * changes the filter or the mask; something inside it moves, so the filter is computed again on every frame;
 * or it blurs over more than the viewport and itself moves. An element smaller than a fingertip target is not
 * judged, nor one that stays still with everything inside it.
 * @param label the element, as a finding names it.
 * @param facts what the browser reports about it.
 * @param viewport the viewport it was sampled at.
 * @returns the message, or undefined when nothing about it is redrawn each frame.
 */
export const filterInMotionMessage = (label: string, facts: FilterFacts, viewport: Viewport): string | undefined => {
  if (facts.width * facts.height < MOTION_MIN_AREA) {
    return undefined
  }
  const animated: readonly string[] = animatedFilters(facts)
  if (animated.length > 0) {
    return `${label} animates its own ${animated.join(', ')}, which is computed again on every frame; ${REPAIR}`
  }
  if (facts.hasMovingInside) {
    return (
      `${label} is drawn through a ${effectsOf(facts)} over content that moves, so the effect is computed again ` +
      `on every frame; ${REPAIR}`
    )
  }
  const screens: number = movingBlurViewports(facts, viewport)
  return screens > 0
    ? `${label} blurs over ${String(Math.round(screens))} viewports and moves, so the blur is computed again on ` +
        `every frame; ${REPAIR}`
    : undefined
}

/** What the browser reports about one composited layer. */
export interface CompositedLayer {
  /** The layer's size, in CSS pixels. */
  readonly width: number
  readonly height: number
  /** The centre of the element it draws, in CSS pixels of the viewport; undefined for a layer of no element. */
  readonly centreX: number | undefined
  readonly centreY: number | undefined
  /** Whether it draws content of its own, which is what takes a texture. */
  readonly drawsContent: boolean
  /** Why the browser gave it a layer of its own, as Chromium names its reasons. */
  readonly reasons: readonly string[]
  /** Whether it draws the document itself. */
  readonly isDocument: boolean
  /** The element it draws, as a finding names it. */
  readonly label: string
}

// The reasons every page's own layers carry: the document's scrolling, a scrolling area's, the viewport's and
// their scrollbars'. A layer with nothing beyond these is not one the author promoted.
const STRUCTURAL_REASONS: ReadonlySet<string> = new Set<string>([
  'Root',
  'RootScroller',
  'OverflowScrolling',
  'Viewport',
  'Scrollbar',
  'ScrollbarLayer',
  'ViewportScrollbar',
])

const isPromoted = (layer: CompositedLayer): boolean =>
  layer.drawsContent &&
  !layer.isDocument &&
  layer.centreX !== undefined &&
  layer.reasons.some((reason: string): boolean => !STRUCTURAL_REASONS.has(reason))

// The part of a layer that lies within a viewport of what is shown on every side.
const nearArea = (layer: CompositedLayer, viewport: Viewport): number => {
  const halves: number = 2
  const centreX: number = layer.centreX ?? 0
  const centreY: number = layer.centreY ?? 0
  const left: number = Math.max(-viewport.width, centreX - layer.width / halves)
  const right: number = Math.min(viewport.width * halves, centreX + layer.width / halves)
  const top: number = Math.max(-viewport.height, centreY - layer.height / halves)
  const bottom: number = Math.min(viewport.height * halves, centreY + layer.height / halves)
  return Math.max(0, right - left) * Math.max(0, bottom - top)
}

/** One promoted layer and how many viewports of it count. */
export interface LayerShare {
  readonly label: string
  readonly viewports: number
}

/** How many viewports' worth of promoted layers a page keeps near what it shows. */
export interface LayerMeasure {
  readonly viewports: number
  /** The largest layers, largest first. */
  readonly largest: readonly LayerShare[]
}

// How many of the largest layers a finding names: enough to point at the cause, few enough to read.
const NAMED_LAYERS: number = 3

/**
 * How many viewports' worth of composited layers the author promoted, counting only what lies within a
 * viewport of what is shown.
 * @param layers every layer the browser composited.
 * @param viewport the viewport.
 * @returns the measure.
 */
export const layerMeasure = (layers: readonly CompositedLayer[], viewport: Viewport): LayerMeasure => {
  const screen: number = viewport.width * viewport.height
  const shares: readonly LayerShare[] = layers
    .filter((layer: CompositedLayer): boolean => isPromoted(layer))
    .map(
      (layer: CompositedLayer): LayerShare => ({ label: layer.label, viewports: nearArea(layer, viewport) / screen }),
    )
    .filter((share: LayerShare): boolean => share.viewports > 0)
  return {
    viewports: shares.reduce((sum: number, share: LayerShare): number => sum + share.viewports, 0),
    largest: shares
      .toSorted((first: LayerShare, second: LayerShare): number => second.viewports - first.viewports)
      .slice(0, NAMED_LAYERS),
  }
}

const HUNDREDTHS: number = 100

const viewportsOf = (count: number): string => String(Math.round(count * HUNDREDTHS) / HUNDREDTHS)

/**
 * What is wrong with a page whose promoted layers exceed the budget (rule `layer-area`).
 * @param measure the page's largest measure across its samples.
 * @param budget how many viewports' worth it may keep.
 * @returns the message, or undefined within the budget.
 */
export const layerAreaMessage = (measure: LayerMeasure, budget: number): string | undefined =>
  measure.viewports <= budget
    ? undefined
    : `composited layers cover ${viewportsOf(measure.viewports)} viewports near what is shown, beyond the ` +
      `budget of ${String(budget)}; the largest: ` +
      measure.largest
        .map((share: LayerShare): string => `${share.label} (${viewportsOf(share.viewports)})`)
        .join(', ') +
      '; drop will-change and promoting transforms from what does not move, and keep layers no larger than they show'
