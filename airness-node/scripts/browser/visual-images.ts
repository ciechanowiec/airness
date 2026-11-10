// The raster sources of the visual fixture: real PNG files of a known natural size, written byte by byte so
// the fixture needs no image library.
import { deflateSync } from 'node:zlib'

// A raster source of a known natural size, so the image rules judge real decoding rather than a stub.
export const PHOTO_WIDTH: number = 160
export const PHOTO_HEIGHT: number = 120
// A source ten times denser than the photo's box, more than the densest screen draws.
export const POSTER_WIDTH: number = 1600
export const POSTER_HEIGHT: number = 1200
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

/**
 * A PNG of one shade at a size, so the image rules judge a real decoded source rather than a stub.
 * @param width its width in pixels.
 * @param height its height in pixels.
 * @returns the file's bytes.
 */
export const photo = (width: number, height: number): Buffer => {
  const header: Buffer = Buffer.alloc(HEADER_BYTES)
  header.writeUInt32BE(width, 0)
  header.writeUInt32BE(height, WORD_BYTES)
  header.writeUInt8(BIT_DEPTH, DEPTH_OFFSET)
  header.writeUInt8(TRUE_COLOUR, COLOUR_OFFSET)
  // Each row opens with filter type 0, then the same shade in every pixel.
  const row: Buffer = Buffer.concat([Buffer.alloc(1), ...Array.from({ length: width }, (): Buffer => PHOTO_SHADE)])
  const pixels: Buffer = Buffer.concat(Array.from({ length: height }, (): Buffer => row))
  return Buffer.concat([
    PNG_SIGNATURE,
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(pixels)),
    chunk('IEND', Buffer.alloc(0)),
  ])
}
