// How the rendering pass judges what a page costs to draw again: an element drawn through a filter or a mask
// while it or what is inside it moves, and how many viewports' worth of composited layers a page keeps near
// what it shows. Each element is stated as the facts the browser reads off it, each layer as the layer tree
// reports it.
import { describe, expect, it } from 'vitest'
import type { Viewport } from '@/browser-findings'
import {
  type CompositedLayer,
  type FilterFacts,
  filterInMotionMessage,
  type LayerMeasure,
  layerAreaMessage,
  layerMeasure,
} from '@/render-cost-policy'

const PHONE: Viewport = { width: 390, height: 844 }

const filtered = (fields: Partial<FilterFacts>): FilterFacts => ({
  width: 300,
  height: 200,
  hasBlur: true,
  hasBackdrop: false,
  hasMask: false,
  isMoving: false,
  hasMovingInside: false,
  animatedProperties: [],
  ...fields,
})

const REPAIR: string = 'draw the effect once, as an image or a still gradient, or keep what moves outside it'

describe('a filter or a mask in motion', () => {
  it('fails an element whose own animation changes its filter or its mask, in every spelling', () => {
    expect([
      filterInMotionMessage('div.glow', filtered({ animatedProperties: ['filter', 'transform'] }), PHONE),
      filterInMotionMessage('div.pane', filtered({ animatedProperties: ['backdrop-filter'] }), PHONE),
      filterInMotionMessage('div.fade', filtered({ animatedProperties: ['mask-position'] }), PHONE),
      filterInMotionMessage('div.fade', filtered({ animatedProperties: ['webkit-mask-size'] }), PHONE),
    ]).toEqual([
      `div.glow animates its own filter, which is computed again on every frame; ${REPAIR}`,
      `div.pane animates its own backdrop-filter, which is computed again on every frame; ${REPAIR}`,
      `div.fade animates its own mask-position, which is computed again on every frame; ${REPAIR}`,
      `div.fade animates its own webkit-mask-size, which is computed again on every frame; ${REPAIR}`,
    ])
  })

  it('fails an element drawn through a filter, a backdrop filter or a mask over content that moves', () => {
    expect([
      filterInMotionMessage('div.glow', filtered({ hasMovingInside: true }), PHONE),
      filterInMotionMessage('div.pane', filtered({ hasBlur: false, hasBackdrop: true, hasMovingInside: true }), PHONE),
      filterInMotionMessage('div.fade', filtered({ hasBlur: false, hasMask: true, hasMovingInside: true }), PHONE),
      filterInMotionMessage('div.tint', filtered({ hasBlur: false, hasMovingInside: true }), PHONE),
    ]).toEqual([
      `div.glow is drawn through a filter over content that moves, so the effect is computed again on every frame; ${REPAIR}`,
      `div.pane is drawn through a backdrop filter over content that moves, so the effect is computed again on every frame; ${REPAIR}`,
      `div.fade is drawn through a mask over content that moves, so the effect is computed again on every frame; ${REPAIR}`,
      `div.tint is drawn through a filter over content that moves, so the effect is computed again on every frame; ${REPAIR}`,
    ])
  })

  it('fails a blur larger than the viewport that moves, and passes one that fits or stays put', () => {
    const large: Partial<FilterFacts> = { width: 1200, height: 900, isMoving: true }
    expect([
      filterInMotionMessage('div.haze', filtered(large), PHONE),
      filterInMotionMessage('div.pane', filtered({ ...large, hasBlur: false, hasBackdrop: true }), PHONE),
      filterInMotionMessage('div.haze', filtered({ ...large, isMoving: false }), PHONE),
      filterInMotionMessage('div.haze', filtered({ isMoving: true }), PHONE),
      filterInMotionMessage('div.fade', filtered({ ...large, hasBlur: false, hasMask: true }), PHONE),
    ]).toEqual([
      `div.haze blurs over 3 viewports and moves, so the blur is computed again on every frame; ${REPAIR}`,
      `div.pane blurs over 3 viewports and moves, so the blur is computed again on every frame; ${REPAIR}`,
      undefined,
      undefined,
      undefined,
    ])
  })

  it('passes a still element, and leaves alone one smaller than a fingertip target', () => {
    expect([
      filterInMotionMessage('div.glow', filtered({}), PHONE),
      filterInMotionMessage('svg.icon', filtered({ width: 24, height: 24, hasMovingInside: true }), PHONE),
    ]).toEqual([undefined, undefined])
  })
})

const layer = (fields: Partial<CompositedLayer>): CompositedLayer => ({
  width: 390,
  height: 844,
  centreX: 195,
  centreY: 422,
  drawsContent: true,
  reasons: ['WillChangeTransform'],
  isDocument: false,
  label: 'div.panel',
  ...fields,
})

describe('the area of composited layers', () => {
  it('counts the layers the author promoted, by the viewports they cover', () => {
    const measure: LayerMeasure = layerMeasure(
      [
        layer({}),
        layer({ width: 195, height: 422, label: 'div.card', reasons: ['ActiveTransformAnimation'] }),
        layer({ width: 390, height: 60, label: 'header', reasons: ['Overlap'] }),
      ],
      PHONE,
    )
    expect([measure.viewports.toFixed(3), measure.largest.map((share) => share.label)]).toEqual([
      '1.321',
      ['div.panel', 'div.card', 'header'],
    ])
  })

  it('leaves out the document, its scrolling areas, the viewport and layers that draw nothing', () => {
    expect(
      layerMeasure(
        [
          layer({ reasons: ['RootScroller', 'OverflowScrolling'], height: 4000 }),
          layer({ reasons: ['OverflowScrolling'] }),
          layer({ reasons: ['Viewport'], drawsContent: false }),
          layer({ isDocument: true }),
          layer({ drawsContent: false }),
          layer({ centreX: undefined, centreY: undefined }),
          layer({ reasons: [] }),
        ],
        PHONE,
      ),
    ).toEqual({ viewports: 0, largest: [] })
  })

  it('counts of a layer only what lies within a viewport of what is shown', () => {
    expect([
      layerMeasure([layer({ height: 844 * 10, centreY: 422 })], PHONE).viewports,
      layerMeasure([layer({ centreY: 422 + 844 * 3 })], PHONE).viewports,
      layerMeasure([layer({ width: 390 * 5, centreX: 195 })], PHONE).viewports,
    ]).toEqual([3, 0, 3])
  })

  it('fails a page beyond the budget, naming its largest layers, and passes one within it', () => {
    const measure: LayerMeasure = layerMeasure(
      [layer({ height: 844 * 3, centreY: 844 }), layer({ label: 'div.field' }), layer({ label: 'div.lantern' })],
      PHONE,
    )
    expect([layerAreaMessage(measure, 4), layerAreaMessage(measure, 5)]).toEqual([
      'composited layers cover 4.5 viewports near what is shown, beyond the budget of 4; the largest: div.panel ' +
        '(2.5), div.field (1), div.lantern (1); drop will-change and promoting transforms from what does not move, ' +
        'and keep layers no larger than they show',
      undefined,
    ])
  })
})
