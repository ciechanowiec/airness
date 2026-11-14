import { randomUUID } from 'node:crypto'
import { existsSync } from 'node:fs'
import path from 'node:path'
import type { Payload } from 'payload'
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

const fetchFile = async (filename: string): Promise<Response> =>
  await callApi({ method: 'GET', path: `/media/file/${filename}` })

const filenameOf = (media: Media): string => {
  if (typeof media.filename !== 'string') {
    throw new TypeError('Payload stored the upload without a filename.')
  }
  return media.filename
}

describe('media storage', () => {
  it('stores an upload in the bucket and serves its bytes back to an anonymous visitor', async () => {
    const filename: string = filenameOf(await uploadPixel())
    const response: Response = await fetchFile(filename)
    expect(response.status).toBe(OK)
    expect(Buffer.from(await response.arrayBuffer())).toEqual(Buffer.from(PIXEL))
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
