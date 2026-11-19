import { randomUUID } from 'node:crypto'
import { existsSync } from 'node:fs'
import path from 'node:path'
import type { Payload } from 'payload'
import sharp, { type Metadata } from 'sharp'
import { describe, expect, it } from 'vitest'

import type { Media } from '@/payload-types'
import { callApi, NOT_FOUND, OK, PIXEL } from '../helpers/api'
import { payloadInstance } from '../helpers/payload'

// Uploads go to the run's own RustFS bucket through the S3 adapter, so each case uploads a file under a
// name of its own and reads it back the way a visitor does, through Payload's file route.
const uploadPixel = async (): Promise<Media> => {
  const payload: Payload = await payloadInstance
  return await payload.create({
    collection: 'media',
    data: { alt: 'A single pixel' },
    file: { data: Buffer.from(PIXEL), mimetype: 'image/png', name: `pixel-${randomUUID()}.png`, size: PIXEL.length },
  })
}

// A photograph as a phone stores it: on its side, with the tag that says so, and with the name of whoever took it,
// which stands for everything private a camera records, such as where the picture was taken.
const ARTIST: string = 'Somebody Private'
const STORED_WIDTH: number = 64
const STORED_HEIGHT: number = 32
const TURNED_A_QUARTER: number = 6

const photograph = async (): Promise<Buffer> =>
  await sharp({ create: { width: STORED_WIDTH, height: STORED_HEIGHT, channels: 3, background: '#4338ca' } })
    .jpeg()
    .withExif({ IFD0: { Artist: ARTIST } })
    .withMetadata({ orientation: TURNED_A_QUARTER })
    .toBuffer()

const uploadPhotograph = async (): Promise<Media> => {
  const payload: Payload = await payloadInstance
  const data: Buffer = await photograph()
  return await payload.create({
    collection: 'media',
    data: { alt: 'A photograph taken on a phone' },
    file: { data, mimetype: 'image/jpeg', name: `photograph-${randomUUID()}.jpg`, size: data.length },
  })
}

const fetchFile = async (filename: string): Promise<Response> =>
  await callApi({ method: 'GET', path: `/media/file/${filename}` })

const filenameOf = (media: Media): string => {
  if (typeof media.filename !== 'string') {
    throw new TypeError('Payload stored the upload without a filename.')
  }
  return media.filename
}

describe('media storage', () => {
  it('stores an upload in the bucket and serves the picture back to an anonymous visitor', async () => {
    const filename: string = filenameOf(await uploadPixel())
    const response: Response = await fetchFile(filename)
    expect(response.status).toBe(OK)
    const served: Metadata = await sharp(Buffer.from(await response.arrayBuffer())).metadata()
    expect([served.format, served.width, served.height]).toEqual(['png', 1, 1])
  })

  it('serves a photograph upright and without what the camera wrote into it', async () => {
    const media: Media = await uploadPhotograph()
    const response: Response = await fetchFile(filenameOf(media))
    const bytes: Buffer = Buffer.from(await response.arrayBuffer())
    const served: Metadata = await sharp(bytes).metadata()
    expect(bytes.includes(ARTIST)).toBe(false)
    expect(served.exif).toBeUndefined()
    expect([served.width, served.height]).toEqual([STORED_HEIGHT, STORED_WIDTH])
    expect([media.width, media.height]).toEqual([STORED_HEIGHT, STORED_WIDTH])
  })

  it('never writes an upload to the local disk', async () => {
    const filename: string = filenameOf(await uploadPixel())
    expect(existsSync(path.resolve('media', filename))).toBe(false)
  })

  it('removes the stored file together with its document', async () => {
    const media: Media = await uploadPixel()
    const payload: Payload = await payloadInstance
    await payload.delete({ collection: 'media', id: media.id })
    const response: Response = await fetchFile(filenameOf(media))
    expect(response.status).toBe(NOT_FOUND)
  })
})
