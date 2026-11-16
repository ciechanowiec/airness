// The steps of the layout measurement that read one element's computed style alone: its borders, its line
// height, how it is painted against the others, and the order its boxes are shown in.
//
// Each is handed to the page with the rest of the layout tools, by its source, so it reads nothing outside its
// own parameters.
import type { FlowStyle, LayoutBorder, LayoutBorders, LayoutNode } from './layout-snapshot.js'

/** An element's computed style, as the page reports it. */
export interface PageStyle {
  readonly getPropertyValue: (property: string) => string
}

/**
 * The four computed borders.
 * @param style the element's computed style.
 * @returns each side's width, style and colour.
 */
export const bordersOf = (style: PageStyle): LayoutBorders => {
  const side = (name: string): LayoutBorder => ({
    // A computed border width is always a pixel length, `0px` for a side drawn with no border.
    width: Number(style.getPropertyValue(`border-${name}-width`).replace('px', '')),
    style: style.getPropertyValue(`border-${name}-style`),
    color: style.getPropertyValue(`border-${name}-color`),
  })
  return {
    top: side('top'),
    right: side('right'),
    bottom: side('bottom'),
    left: side('left'),
  }
}

/**
 * The computed line height. `normal` reads as NaN, which the rules take as 0: no line height to shrink a text
 * rect to.
 * @param style the element's computed style.
 * @returns the line height in CSS pixels, or 0 for `normal`.
 */
export const lineHeightOf = (style: PageStyle): number => {
  const lineHeight: number = Number(style.getPropertyValue('line-height').replace('px', ''))
  return Number.isFinite(lineHeight) ? lineHeight : 0
}

/**
 * What decides how an element is painted against the others.
 * @param style the element's computed style.
 * @returns its stacking order, transform and rotation.
 */
export const paintOrderOf = (style: PageStyle): Pick<LayoutNode, 'rotate' | 'transform' | 'zIndex'> => ({
  zIndex: style.getPropertyValue('z-index'),
  transform: style.getPropertyValue('transform'),
  rotate: style.getPropertyValue('rotate'),
})

/**
 * What can show the element's boxes, or its children's, in an order other than the document's.
 * @param style the element's computed style.
 * @returns its flex and grid flow, its own order and placement, and the way its lines run.
 */
export const flowOf = (style: PageStyle): FlowStyle => ({
  order: style.getPropertyValue('order'),
  flexDirection: style.getPropertyValue('flex-direction'),
  flexWrap: style.getPropertyValue('flex-wrap'),
  gridAutoFlow: style.getPropertyValue('grid-auto-flow'),
  gridLines: ['grid-row-start', 'grid-row-end', 'grid-column-start', 'grid-column-end'].map(
    (property: string): string => style.getPropertyValue(property),
  ),
  direction: style.getPropertyValue('direction'),
  writingMode: style.getPropertyValue('writing-mode'),
})
