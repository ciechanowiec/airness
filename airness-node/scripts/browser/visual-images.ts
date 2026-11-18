// The raster sources of the visual fixture: real PNG files of a known natural size, written byte by byte so
// the fixture needs no image library.
import { deflateSync } from 'node:zlib'

// A raster source of a known natural size, so the image rules judge real decoding rather than a stub.
const PHOTO_WIDTH: number = 160
const PHOTO_HEIGHT: number = 120
// A source ten times denser than the photo's box, more than the densest screen draws.
const POSTER_WIDTH: number = 1600
const POSTER_HEIGHT: number = 1200
// A source drawn at its own size whose every pixel differs, so no compression shrinks it below its raw bytes.
const GRAIN_WIDTH: number = 240
const GRAIN_HEIGHT: number = 180
const CHANNELS: number = 3
// An integer hash of each byte's position, so the grain is the same on every run and no two neighbours repeat.
const GRAIN_MIXER: number = 0x04_5d_9f_3b
const HALF_WORD: number = 16
const BYTE: number = 256
const CRC_POLYNOMIAL: number = 0xed_b8_83_20
const CRC_BITS: number = 8
const CRC_INITIAL: number = 0xff_ff_ff_ff
const BIT_DEPTH: number = 8
const TRUE_COLOUR: number = 2
const WORD_BYTES: number = 4
const HEADER_BYTES: number = 13
const DEPTH_OFFSET: number = 8
const COLOUR_OFFSET: number = 9
const PNG_SIGNATURE: Buffer = Buffer.from('89504e470d0a1a0a', 'hex')
const PHOTO_SHADE: Buffer = Buffer.from('2563eb', 'hex')

const CRC_TABLE: readonly number[] = Array.from({ length: BYTE }, (_unused: unknown, index: number): number =>
  Array.from({ length: CRC_BITS }).reduce<number>(
    (value: number): number => ((value & 1) === 1 ? CRC_POLYNOMIAL ^ (value >>> 1) : value >>> 1),
    index,
  ),
)

const crc32 = (bytes: Buffer): number =>
  (bytes.reduce(
    (crc: number, byte: number): number => (CRC_TABLE[(crc ^ byte) & (BYTE - 1)] ?? 0) ^ (crc >>> CRC_BITS),
    CRC_INITIAL,
  ) ^
    CRC_INITIAL) >>>
  0

const chunk = (type: string, data: Buffer): Buffer => {
  const typed: Buffer = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const length: Buffer = Buffer.alloc(WORD_BYTES)
  length.writeUInt32BE(data.length)
  const checksum: Buffer = Buffer.alloc(WORD_BYTES)
  checksum.writeUInt32BE(crc32(typed))
  return Buffer.concat([length, typed, checksum])
}

const png = (width: number, height: number, pixels: Buffer): Buffer => {
  const header: Buffer = Buffer.alloc(HEADER_BYTES)
  header.writeUInt32BE(width, 0)
  header.writeUInt32BE(height, WORD_BYTES)
  header.writeUInt8(BIT_DEPTH, DEPTH_OFFSET)
  header.writeUInt8(TRUE_COLOUR, COLOUR_OFFSET)
  return Buffer.concat([
    PNG_SIGNATURE,
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(pixels)),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

/**
 * A PNG of one shade at a size, so the image rules judge a real decoded source rather than a stub.
 * @param width its width in pixels.
 * @param height its height in pixels.
 * @returns the file's bytes.
 */
export const photo = (width: number, height: number): Buffer => {
  // Each row opens with filter type 0, then the same shade in every pixel.
  const row: Buffer = Buffer.concat([Buffer.alloc(1), ...Array.from({ length: width }, (): Buffer => PHOTO_SHADE)])
  return png(width, height, Buffer.concat(Array.from({ length: height }, (): Buffer => row)))
}

const shade = (position: number): number => {
  const mixed: number = Math.imul(position ^ (position >>> HALF_WORD), GRAIN_MIXER)
  const again: number = Math.imul(mixed ^ (mixed >>> HALF_WORD), GRAIN_MIXER)
  return (again ^ (again >>> HALF_WORD)) & (BYTE - 1)
}

/**
 * A PNG at a size whose every pixel is a different shade, which weighs about as much as its raw pixels.
 * @param width its width in pixels.
 * @param height its height in pixels.
 * @returns the file's bytes.
 */
export const grain = (width: number, height: number): Buffer => {
  const rowBytes: number = 1 + width * CHANNELS
  // Each row opens with filter type 0, then a shade of its own for every channel of every pixel.
  const pixels: Buffer = Buffer.from(
    Array.from({ length: rowBytes * height }, (_unused: unknown, index: number): number =>
      index % rowBytes === 0 ? 0 : shade(index),
    ),
  )
  return png(width, height, pixels)
}

/**
 * Every raster file of the visual fixture, under the name its pages ask for it by.
 * @returns each file's bytes, by its name.
 */
export const visualImages = (): Readonly<Record<string, Buffer>> => ({
  'photo.png': photo(PHOTO_WIDTH, PHOTO_HEIGHT),
  'poster.png': photo(POSTER_WIDTH, POSTER_HEIGHT),
  'grain.png': grain(GRAIN_WIDTH, GRAIN_HEIGHT),
  'calm.png': photo(GRAIN_WIDTH, GRAIN_HEIGHT),
})

/**
 * The pages of the image rules, each body as a defect and the same markup repaired, which the visual fixture
 * serves beside its other pages.
 */
export const IMAGE_PAGES: Readonly<Record<string, { readonly defect: string; readonly valid: string }>> = {
  'image-load': {
    defect: '<img src="/missing.png" alt="Missing photo" width={40} height={40} />',
    valid: '<img src="/photo.png" alt="Photo" width={160} height={120} />',
  },
  'image-distorted': {
    defect: '<img src="/photo.png" alt="Stretched photo" width={160} height={60} />',
    valid: '<img src="/photo.png" alt="Cropped photo" width={160} height={60} className="cover" />',
  },
  'image-upscaled': {
    defect: '<img src="/photo.png" alt="Enlarged photo" width={240} height={180} />',
    valid:
      '<div data-airness-layout="image-upscaled" data-airness-layout-reason="the fixture shows its only source">' +
      '<img src="/photo.png" alt="Enlarged photo" width={240} height={180} /></div>',
  },
  // The repair keeps the large source for screens that need it and lets the browser choose by the box.
  'image-oversized': {
    defect: '<img src="/poster.png" alt="Oversized photo" width={160} height={120} />',
    valid:
      '<img src="/poster.png" srcSet="/photo.png 160w, /poster.png 1600w" sizes="160px" alt="Fitted photo" ' +
      'width={160} height={120} />',
  },
  // A file at the size of its box whose pixels no compression shrinks weighs about 24 bits a pixel; the same
  // size in a file that compresses well weighs a fraction of one.
  'image-heavy': {
    defect: '<img src="/grain.png" alt="Heavy photo" width={240} height={180} />',
    valid: '<img src="/calm.png" alt="Light photo" width={240} height={180} />',
  },
  'media-space': {
    defect: '<img src="/photo.png" alt="Unsized photo" className="fluid" />',
    valid: '<img src="/photo.png" alt="Sized photo" className="fluid" width={160} height={120} />',
  },
  // A source chosen from a srcset whose declared width is larger than the file, as an image server that
  // will not enlarge a small original answers: the file's own pixels decide, not the declared width.
  'srcset-image': {
    defect:
      '<img src="/photo.png" srcSet="/photo.png 160w" sizes="240px" alt="Enlarged photo" width={240} height={180} />',
    valid: '<img src="/photo.png" srcSet="/photo.png 320w" sizes="160px" alt="Photo" width={160} height={120} />',
  },
}
