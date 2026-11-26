// How the interaction pass judges a control that carries no text: which controls it shoots, the region it
// cuts from the shots, and how much of what the control paints must contrast 3:1 with what it covers. Each
// rendering is stated as RGBA pixels, the ground in one colour and the control's strokes in others.
import { describe, expect, it } from 'vitest'
import {
  type GraphicFacts,
  type GraphicMeasure,
  type GraphicRenderings,
  graphicContrastMessage,
  graphicRegion,
  isGraphicCandidate,
  measureGraphic,
} from '@/graphic-contrast-policy'

type Colour = readonly [number, number, number]

const WHITE: Colour = [255, 255, 255]
const PALE_GREY: Colour = [243, 244, 246]
const SILVER: Colour = [203, 213, 225]
const INK: Colour = [31, 41, 55]
const MINT: Colour = [220, 240, 220]
const SAGE: Colour = [134, 196, 140]
const FOREST: Colour = [20, 83, 45]
const MID_GREY: Colour = [150, 150, 150]

/** Which colour, if any, the control paints at one pixel of the region. */
type Painter = (column: number, row: number) => Colour | undefined

const SIZE: number = 24

const rendered = (ground: Colour, painter: Painter | undefined, scale: number = 1): readonly number[] =>
  Array.from({ length: SIZE * scale * SIZE * scale }, (_unused: unknown, pixel: number): readonly number[] => {
    const column: number = Math.floor((pixel % (SIZE * scale)) / scale)
    const row: number = Math.floor(Math.floor(pixel / (SIZE * scale)) / scale)
    return [...(painter?.(column, row) ?? ground), 255]
  }).flat()

const renderingsOf = (ground: Colour, painter: Painter, scale: number = 1): GraphicRenderings => ({
  shown: rendered(ground, painter, scale),
  hidden: rendered(ground, undefined, scale),
  width: SIZE * scale,
  scale,
})

// Whether a pixel lies in the band two pixels thick just inside a box from `from` to `to` on both axes.
const isInBand = (column: number, row: number, from: number, to: number): boolean => {
  const depth: number = Math.min(column - from, row - from, to - 1 - column, to - 1 - row)
  return depth >= 0 && depth < 2
}

// A square outline two pixels thick, from 4 to 20 on both axes, as an icon's stroke draws it.
const outline =
  (colour: Colour): Painter =>
  (column: number, row: number): Colour | undefined =>
    isInBand(column, row, 4, 20) ? colour : undefined

// A disc drawn as a square: a ring two pixels thick in one colour around a white middle.
const ringed =
  (ring: Colour): Painter =>
  (column: number, row: number): Colour =>
    isInBand(column, row, 0, SIZE) ? ring : WHITE

const measured = (renderings: GraphicRenderings): GraphicMeasure => {
  const measure: GraphicMeasure | undefined = measureGraphic(renderings)
  if (measure === undefined) {
    throw new Error('the control painted nothing')
  }
  return measure
}

describe('measuring what a control draws', () => {
  it('fails a pale icon on a pale ground, naming how much contrasts and how much must', () => {
    const measure: GraphicMeasure = measured(renderingsOf(PALE_GREY, outline(SILVER)))
    expect([measure, graphicContrastMessage('button.next', measure)]).toEqual([
      { painted: 112, contrasting: 0, required: 56 },
      'button.next carries no text, and too little of what it draws stands out from what lies beneath it: 0 px² ' +
        'of the 112 px² it paints contrast 3:1 with what they cover, and at least 56 px² must (WCAG 1.4.11)',
    ])
  })

  it('passes the same icon drawn dark', () => {
    const measure: GraphicMeasure = measured(renderingsOf(PALE_GREY, outline(INK)))
    expect([measure, graphicContrastMessage('button.next', measure)]).toEqual([
      { painted: 112, contrasting: 112, required: 56 },
      undefined,
    ])
  })

  it('fails a marker whose ring is too pale against its fill, and passes it with a dark ring', () => {
    const pale: GraphicMeasure = measured(renderingsOf(MINT, ringed(SAGE)))
    const dark: GraphicMeasure = measured(renderingsOf(MINT, ringed(FOREST)))
    expect([
      graphicContrastMessage('button.marker', pale) === undefined,
      graphicContrastMessage('button.marker', dark),
      dark,
    ]).toEqual([false, undefined, { painted: 576, contrasting: 176, required: 92 }])
  })

  it('passes a thin antialiased stroke when half of what it paints contrasts', () => {
    const diagonal: Painter = (column: number, row: number): Colour | undefined => {
      if (row === column && column < 16) {
        return INK
      }
      return row === column + 1 && column < 16 ? MID_GREY : undefined
    }
    const measure: GraphicMeasure = measured(renderingsOf(WHITE, diagonal))
    expect([measure, graphicContrastMessage('button.close', measure)]).toEqual([
      { painted: 32, contrasting: 16, required: 16 },
      undefined,
    ])
  })

  it('measures in CSS pixels, whatever the device scale', () => {
    expect(measured(renderingsOf(PALE_GREY, outline(SILVER), 2))).toEqual({
      painted: 112,
      contrasting: 0,
      required: 56,
    })
  })

  it('measures nothing for a control that paints nothing, such as a stretched link', () => {
    expect(measureGraphic(renderingsOf(WHITE, (): undefined => undefined))).toBeUndefined()
  })
})

const facts = (fields: Partial<GraphicFacts>): GraphicFacts => ({
  tag: 'button',
  type: '',
  appearance: 'auto',
  hasVisibleText: false,
  isDisabled: false,
  isInert: false,
  isRendered: true,
  ...fields,
})

describe('which controls are judged', () => {
  it('judges an available control that shows no text, a text field drawn by its author included', () => {
    expect([
      isGraphicCandidate(facts({})),
      isGraphicCandidate(facts({ tag: 'a' })),
      isGraphicCandidate(facts({ tag: 'input', type: 'text', appearance: 'none' })),
      isGraphicCandidate(facts({ tag: 'input', type: 'checkbox', appearance: 'none' })),
    ]).toEqual([true, true, true, true])
  })

  it('leaves a control showing text to text contrast, and an inactive or unrendered one alone', () => {
    expect([
      isGraphicCandidate(facts({ hasVisibleText: true })),
      isGraphicCandidate(facts({ isDisabled: true })),
      isGraphicCandidate(facts({ isInert: true })),
      isGraphicCandidate(facts({ isRendered: false })),
    ]).toEqual([false, false, false, false])
  })

  it('leaves alone a control whose look the user agent draws', () => {
    expect(
      [
        facts({ tag: 'input', type: 'checkbox' }),
        facts({ tag: 'input', type: 'radio' }),
        facts({ tag: 'input', type: 'range' }),
        facts({ tag: 'input', type: 'color' }),
        facts({ tag: 'input', type: 'file' }),
        facts({ tag: 'select' }),
      ].map((candidate: GraphicFacts): boolean => isGraphicCandidate(candidate)),
    ).toEqual([false, false, false, false, false, false])
  })
})

describe('the region a control draws in', () => {
  const viewport: { readonly width: number; readonly height: number } = { width: 390, height: 844 }

  it('joins the control and its descendants, widened by its reach, in whole pixels', () => {
    expect(
      graphicRegion(
        [
          { left: 10.5, top: 20, right: 34, bottom: 44 },
          { left: 30, top: 18.2, right: 40.4, bottom: 30 },
        ],
        4,
        viewport,
      ),
    ).toEqual({ x: 6, y: 14, width: 39, height: 34 })
  })

  it('clips to the viewport, ignores empty boxes, and finds none outside the viewport', () => {
    expect([
      graphicRegion(
        [
          { left: -10, top: 830, right: 20, bottom: 860 },
          { left: 100, top: 100, right: 100, bottom: 100 },
        ],
        4,
        viewport,
      ),
      graphicRegion([{ left: 10, top: 900, right: 40, bottom: 930 }], 4, viewport),
      graphicRegion([{ left: 10, top: 10, right: 10, bottom: 40 }], 4, viewport),
    ]).toEqual([{ x: 0, y: 826, width: 24, height: 18 }, undefined, undefined])
  })
})
