import { describe, expect, it } from 'vitest'
import { type FocusShape, type PerimeterBox, perimeterArea } from '@/focus-perimeter'

const VIEWPORT: { readonly width: number; readonly height: number } = { width: 390, height: 844 }

const box = (left: number, top: number, right: number, bottom: number): PerimeterBox => ({ left, top, right, bottom })

const SQUARE: readonly string[] = ['0px', '0px', '0px', '0px']

const shape = (fragments: readonly PerimeterBox[], radius: string | readonly string[] = SQUARE): FocusShape => ({
  fragments,
  radii: typeof radius === 'string' ? [radius, radius, radius, radius] : radius,
})

// The 120x40 button the rounded cases share, placed well inside the viewport.
const BUTTON: PerimeterBox = box(20, 20, 140, 60)

// The share of a corner's square its arc leaves out, and one pixel along a quarter arc of the given radii.
const CUT: number = 1 - Math.PI / 4
const arc = (across: number, down: number): number => (Math.PI * (across + down)) / 4

describe('the area of a 2px thick perimeter of a focused element', () => {
  it('is 4w + 4h - 16 for a square-cornered box', () => {
    expect([perimeterArea(shape([BUTTON]), VIEWPORT), perimeterArea(shape([box(0, 0, 358, 21)]), VIEWPORT)]).toEqual([
      624, 1500,
    ])
  })

  it('is the whole box for one too small to hold the band', () => {
    expect([
      perimeterArea(shape([box(10, 10, 13, 13)]), VIEWPORT),
      perimeterArea(shape([box(10, 10, 12, 20)]), VIEWPORT),
    ]).toEqual([9, 20])
  })

  it('follows round corners, however the radius is written, less one pixel along each arc', () => {
    // A pill: a radius far beyond the box is scaled down until the two on a side meet, 20px here.
    const pill: number = 4800 - CUT * 4 * 400 - (116 * 36 - CUT * 4 * 324) - 4 * arc(20, 20)
    // An ellipse: 50% of each side.
    const ellipse: number = 4800 - CUT * 4 * 1200 - (116 * 36 - CUT * 4 * 58 * 18) - 4 * arc(60, 20)
    // An elliptical corner written as two lengths.
    const elliptical: number = 4800 - CUT * 4 * 200 - (116 * 36 - CUT * 4 * 18 * 8) - 4 * arc(20, 10)
    // One rounded corner of 8px, the others square.
    const oneCorner: number = 624 - CUT * 64 + CUT * 36 - arc(8, 8)
    expect([
      perimeterArea(shape([BUTTON], '9999px'), VIEWPORT),
      perimeterArea(shape([BUTTON], '50%'), VIEWPORT),
      perimeterArea(shape([BUTTON], '20px 10px'), VIEWPORT),
      perimeterArea(shape([BUTTON], ['8px', '0px', '0px', '0px']), VIEWPORT),
    ]).toEqual([
      expect.closeTo(pill, 6),
      expect.closeTo(ellipse, 6),
      expect.closeTo(elliptical, 6),
      expect.closeTo(oneCorner, 6),
    ])
  })

  it('reads an unreadable radius as a square corner', () => {
    expect(perimeterArea(shape([BUTTON], ['', 'auto', '0', 'none']), VIEWPORT)).toBe(624)
  })

  it('measures a wrapped inline element as the boxes it draws on each line', () => {
    expect(perimeterArea(shape([box(300, 100, 350, 118), box(0, 118, 50, 136)]), VIEWPORT)).toBe(512)
  })

  it('measures only what is in the viewport, with no band along a side the viewport cuts off', () => {
    expect([
      // Cut at the top: the bottom and both sides keep their band.
      perimeterArea(shape([box(0, -10, 120, 30)]), VIEWPORT),
      // Cut on the right and at the bottom.
      perimeterArea(shape([box(300, 824, 420, 864)]), VIEWPORT),
      // A rounded corner the viewport cuts off is no longer rounded.
      perimeterArea(shape([box(0, -10, 120, 30)], '8px'), VIEWPORT),
      // Entirely outside.
      perimeterArea(shape([box(0, 900, 120, 940)]), VIEWPORT),
      perimeterArea(shape([]), VIEWPORT),
    ]).toEqual([
      120 * 30 - 116 * 28,
      90 * 20 - 88 * 18,
      expect.closeTo(120 * 30 - 116 * 28 - 2 * CUT * 64 + 2 * CUT * 36 - 2 * arc(8, 8), 6),
      0,
      0,
    ])
  })
})
