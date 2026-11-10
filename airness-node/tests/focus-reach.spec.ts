import { describe, expect, it } from 'vitest'
import { FOCUS_RING_WIDTH } from '@/browser-rules'
import { focusRegion, type IndicatorElement, type IndicatorStyle } from '@/focus-reach'
import { focusGeometry } from '@/focus-reach.js'

const style = (overrides: Partial<IndicatorStyle>): IndicatorStyle => ({
  outlineStyle: 'none',
  outlineWidth: '0px',
  outlineOffset: '0px',
  outlineColor: 'rgb(0, 0, 0)',
  boxShadow: 'none',
  borderColor: 'rgb(0, 0, 0)',
  backgroundColor: 'rgba(0, 0, 0, 0)',
  color: 'rgb(0, 0, 0)',
  textDecorationLine: 'none',
  ...overrides,
})

const VIEWPORT: { readonly width: number; readonly height: number } = {
  width: 1280,
  height: 800,
}

const element = (
  box: readonly [number, number, number, number],
  focused: Partial<IndicatorStyle>,
  unfocused: Partial<IndicatorStyle> = focused,
): IndicatorElement => ({
  box: { left: box[0], top: box[1], right: box[2], bottom: box[3] },
  focused: style(focused),
  unfocused: style(unfocused),
})

// The focused element of the reach cases: far enough inside the viewport that its region is never clipped,
// so the region's left edge lies exactly as far beyond the box as the indicator reaches.
const REACH_BOX: readonly [number, number, number, number] = [100, 100, 200, 140]

const reachOf = (focused: Partial<IndicatorStyle>): number | undefined => {
  const region: ReturnType<typeof focusRegion> = focusRegion(element(REACH_BOX, focused), [], VIEWPORT)
  return region === undefined ? undefined : REACH_BOX[0] - region.x
}

describe('how far the indicator of a focused element reaches beyond its box', () => {
  it('reaches a ring drawn as a shadow beyond an offset gap, as a Tailwind ring with an offset is', () => {
    expect(
      reachOf({
        boxShadow:
          'rgb(15, 23, 42) 0px 0px 0px 4px, rgb(250, 204, 21) 0px 0px 0px 6px, rgba(0, 0, 0, 0) 0px 0px 0px 0px',
      }),
    ).toBe(7)
  })

  it('reaches an outline drawn at an offset', () => {
    expect(
      reachOf({
        outlineStyle: 'solid',
        outlineWidth: '2px',
        outlineOffset: '6px',
      }),
    ).toBe(9)
  })

  it('takes the farther of an outline and a shadow', () => {
    expect(
      reachOf({
        outlineStyle: 'solid',
        outlineWidth: '2px',
        outlineOffset: '2px',
        boxShadow: 'rgb(0, 0, 0) 0px 4px 10px 2px',
      }),
    ).toBe(17)
  })

  it('ignores a shadow drawn inside the box, and an outline that is not drawn', () => {
    expect(
      reachOf({
        outlineStyle: 'none',
        outlineWidth: '12px',
        boxShadow: 'oklch(0.5 0.1 200) 0px 0px 0px 12px inset',
      }),
    ).toBe(FOCUS_RING_WIDTH)
  })

  it('takes the farther of the focused and the unfocused indicator', () => {
    const region: ReturnType<typeof focusRegion> = focusRegion(
      element(REACH_BOX, {}, { outlineStyle: 'solid', outlineWidth: '2px', outlineOffset: '6px' }),
      [],
      VIEWPORT,
    )
    expect(region?.x).toBe(REACH_BOX[0] - 9)
  })

  it('keeps the fixed margin for an indicator inside it or none at all', () => {
    expect(reachOf({})).toBe(FOCUS_RING_WIDTH)
    expect(
      reachOf({
        outlineStyle: 'solid',
        outlineWidth: '1px',
        outlineOffset: '-3px',
      }),
    ).toBe(FOCUS_RING_WIDTH)
  })
})

describe('the region a focus indicator can show in', () => {
  it('holds the focused element and the margin around it when nothing else changes', () => {
    expect(focusRegion(element([100, 100, 200, 140], {}), [element([0, 0, 600, 600], {})], VIEWPORT)).toEqual({
      x: 96,
      y: 96,
      width: 108,
      height: 48,
    })
  })

  it('holds the ring of the card around a stretched link, which the card draws when its link has focus', () => {
    const card: IndicatorElement = element(
      [40, 300, 640, 700],
      { boxShadow: 'rgb(250, 204, 21) 0px 0px 0px 2px' },
      { boxShadow: 'none' },
    )
    expect(focusRegion(element([60, 620, 620, 680], {}), [card], VIEWPORT)).toEqual({
      x: 36,
      y: 296,
      width: 608,
      height: 408,
    })
  })

  it('holds an icon inside the focused element whose background changes beyond the element', () => {
    const icon: IndicatorElement = element(
      [190, 100, 230, 140],
      { backgroundColor: 'rgb(255, 255, 255)' },
      { backgroundColor: 'rgba(0, 0, 0, 0)' },
    )
    expect(focusRegion(element([100, 100, 200, 140], {}), [icon], VIEWPORT)?.width).toBe(138)
  })

  it('keeps the region inside the viewport, and has none for an element outside it', () => {
    expect(focusRegion(element([1270, 790, 1300, 820], {}), [], VIEWPORT)).toEqual({
      x: 1266,
      y: 786,
      width: 14,
      height: 14,
    })
    expect(focusRegion(element([1400, 900, 1500, 950], {}), [], VIEWPORT)).toBeUndefined()
  })
})

describe('the focus screenshot region', () => {
  it('clips the indicator to the viewport while retaining its visible extent', () => {
    expect(focusGeometry([-10, 20, 90, 70], { width: 80, height: 100 }, 4)).toEqual({
      extent: 80,
      clip: { x: 0, y: 16, width: 80, height: 58 },
    })
  })
})
