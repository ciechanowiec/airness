// Two screenshots of the same page, cut to one region and decoded to RGBA, for the rules that compare what a
// page draws in two states: focused and blurred, or with a control shown and hidden. The browser decodes the
// shots it took: PNG is its own format, and Node has no decoder for it. The page function is self-contained:
// Playwright ships it to the page as text.
import type { Page } from '@playwright/test'
import type { FocusRegion } from './focus-reach.js'

interface PageBitmap {
  readonly width: number
}

interface PageCanvasContext {
  drawImage: (image: PageBitmap, x: number, y: number) => void
  getImageData: (x: number, y: number, width: number, height: number) => { readonly data: Uint8ClampedArray }
}

declare const createImageBitmap: (image: Blob) => Promise<PageBitmap & { readonly height: number }>
declare const OffscreenCanvas: new (
  width: number,
  height: number,
) => { getContext: (kind: '2d') => PageCanvasContext | null }

/** The region of both shots, as base64 RGBA: an array of numbers crosses from the page far more slowly. */
interface EncodedRegions {
  readonly first: string
  readonly second: string
  /** Device pixels per CSS pixel along one side; zero when the browser could not decode the shots. */
  readonly scale: number
}

const regionsInPage = async ([images, region, viewportWidth]: readonly [
  readonly [string, string],
  FocusRegion,
  number,
]): Promise<EncodedRegions> => {
  const regionOf = async (image: string): Promise<{ readonly encoded: string; readonly scale: number }> => {
    const bytes: Uint8Array<ArrayBuffer> = Uint8Array.from(
      atob(image),
      (character: string): number => character.codePointAt(0) ?? 0,
    )
    const bitmap: PageBitmap & { readonly height: number } = await createImageBitmap(
      new Blob([bytes], { type: 'image/png' }),
    )
    const context: PageCanvasContext | null = new OffscreenCanvas(bitmap.width, bitmap.height).getContext('2d')
    if (context === null) {
      return { encoded: '', scale: 0 }
    }
    context.drawImage(bitmap, 0, 0)
    const scale: number = bitmap.width / viewportWidth
    const { data }: { readonly data: Uint8ClampedArray } = context.getImageData(
      Math.round(region.x * scale),
      Math.round(region.y * scale),
      Math.round(region.width * scale),
      Math.round(region.height * scale),
    )
    // Converted in chunks: one call can take only so many arguments.
    const chunk: number = 32_768
    const binary: string = Array.from(
      { length: Math.ceil(data.length / chunk) },
      (_unused: unknown, index: number): string =>
        String.fromCodePoint(...data.subarray(index * chunk, (index + 1) * chunk)),
    ).join('')
    return { encoded: btoa(binary), scale }
  }
  const [first, second]: readonly [
    { readonly encoded: string; readonly scale: number },
    { readonly encoded: string; readonly scale: number },
  ] = await Promise.all([regionOf(images[0]), regionOf(images[1])])
  return { first: first.encoded, second: second.encoded, scale: Math.min(first.scale, second.scale) }
}

/** One region of two screenshots, decoded to RGBA. */
export interface RegionPixels {
  readonly first: Uint8Array
  readonly second: Uint8Array
  /** Device pixels per CSS pixel along one side; zero when the browser could not decode the shots. */
  readonly scale: number
}

const BASE64: BufferEncoding = 'base64'

/**
 * Cut one region out of two screenshots of the page and decode both to RGBA.
 * @param page the page that took the shots, which decodes them.
 * @param shots the two screenshots, as PNG.
 * @param region the region, in CSS pixels of the viewport.
 * @param viewportWidth the viewport's width, which gives the shots' scale.
 * @returns the region's pixels in each shot, and their scale.
 */
export const regionPixels = async (
  page: Page,
  shots: readonly [Uint8Array, Uint8Array],
  region: FocusRegion,
  viewportWidth: number,
): Promise<RegionPixels> => {
  const encoded: EncodedRegions = await page.evaluate(regionsInPage, [
    [Buffer.from(shots[0]).toString(BASE64), Buffer.from(shots[1]).toString(BASE64)],
    region,
    viewportWidth,
  ] as const)
  return {
    first: Buffer.from(encoded.first, BASE64),
    second: Buffer.from(encoded.second, BASE64),
    scale: encoded.scale,
  }
}
