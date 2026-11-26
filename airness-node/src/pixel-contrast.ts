// The WCAG contrast of rendered pixels, read from RGBA bytes: the relative luminance of one pixel, and the
// ratio between two luminances. The rules that judge what a page draws - a focus indicator, a control that
// carries no text - compare pixels with these, never colours read from a stylesheet, which say nothing
// about what lies beneath a translucent layer or around an antialiased edge.

/** The bytes one pixel takes in RGBA data. */
export const RGBA_BYTES: number = 4

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

const linearChannel = (value: number): number => {
  const fraction: number = value / CHANNEL_MAX
  return fraction <= LINEAR_KNEE ? fraction / LINEAR_SLOPE : ((fraction + GAMMA_OFFSET) / GAMMA_SCALE) ** GAMMA
}

/**
 * The relative luminance of one pixel of RGBA data, as WCAG defines it; the alpha byte is not read, since a
 * screenshot is opaque.
 * @param pixels the RGBA data.
 * @param offset the index of the pixel's red byte.
 * @returns the luminance, from 0 to 1.
 */
export const luminanceAt = (pixels: ArrayLike<number>, offset: number): number =>
  RED_WEIGHT * linearChannel(pixels[offset] ?? 0) +
  GREEN_WEIGHT * linearChannel(pixels[offset + 1] ?? 0) +
  BLUE_WEIGHT * linearChannel(pixels[offset + BLUE_OFFSET] ?? 0)

/**
 * The WCAG contrast ratio between two relative luminances.
 * @param first one luminance.
 * @param second the other.
 * @returns the ratio, from 1 to 21.
 */
export const contrastRatio = (first: number, second: number): number =>
  (Math.max(first, second) + FLARE) / (Math.min(first, second) + FLARE)
