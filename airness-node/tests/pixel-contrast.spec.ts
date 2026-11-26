// How the rules that judge drawn pixels read the contrast of RGBA data: the relative luminance of one pixel,
// on both sides of the linear knee, and the WCAG ratio between two luminances, whichever is the lighter.
import { describe, expect, it } from 'vitest'
import { contrastRatio, luminanceAt, RGBA_BYTES } from '@/pixel-contrast'

describe('the contrast of drawn pixels', () => {
  it('reads black, white and a dark channel below the linear knee', () => {
    const pixels: readonly number[] = [0, 0, 0, 255, 255, 255, 255, 255, 10, 10, 10, 255]
    expect([
      luminanceAt(pixels, 0),
      luminanceAt(pixels, RGBA_BYTES),
      luminanceAt(pixels, 2 * RGBA_BYTES).toFixed(5),
    ]).toEqual([0, 1, '0.00304'])
  })

  it('reads a pixel beyond the data as black', () => {
    expect(luminanceAt([255, 255, 255, 255], RGBA_BYTES)).toBe(0)
  })

  it('gives the WCAG ratio whichever luminance comes first', () => {
    expect([contrastRatio(1, 0), contrastRatio(0, 1), contrastRatio(0.5, 0.5)]).toEqual([21, 21, 1])
  })
})
