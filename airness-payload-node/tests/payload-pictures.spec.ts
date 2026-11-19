import { describe, expect, it } from 'vitest'
import { INHERITED_ACCESS_REPORT_MARKER } from '@/payload-defaults.js'
import {
  findKeptPictureMetadata,
  type PictureHandlingReport,
  parsePictureHandlingReport,
  pictureHandlingOf,
} from '@/payload-pictures.js'

const reportOf = (collections: readonly unknown[]): PictureHandlingReport => pictureHandlingOf({ collections })

const printed = (report: unknown): string =>
  `plugin chatter\n${INHERITED_ACCESS_REPORT_MARKER}${JSON.stringify(report)}\n`

const PIXELS: { readonly limitInputPixels: number } = { limitInputPixels: 100_000_000 }

describe('pictureHandlingOf', () => {
  it('reads every spelling of an upload collection that admits a picture', () => {
    const admitted: readonly boolean[] = reportOf([
      { slug: 'flag', upload: true },
      { slug: 'untyped', upload: {} },
      { slug: 'empty', upload: { mimeTypes: [] } },
      { slug: 'wildcard', upload: { mimeTypes: ['image/*'] } },
      { slug: 'anything', upload: { mimeTypes: ['*/*'] } },
      { slug: 'listed', upload: { mimeTypes: ['application/pdf', 'image/jpeg'] } },
      { slug: 'shouted', upload: { mimeTypes: ['IMAGE/PNG'] } },
    ]).pictureHandling.map((entry): boolean => entry.pictures)
    expect(admitted).toStrictEqual([true, true, true, true, true, true, true])
  })

  it('reads a collection of documents alone, or of pictures sharp does not touch, as admitting no picture', () => {
    const admitted: readonly boolean[] = reportOf([
      { slug: 'documents', upload: { mimeTypes: ['application/pdf', 'text/plain'] } },
      { slug: 'vectors', upload: { mimeTypes: ['image/svg+xml'] } },
    ]).pictureHandling.map((entry): boolean => entry.pictures)
    expect(admitted).toStrictEqual([false, false])
  })

  it('reads each option Payload re-encodes an original with, and their absence', () => {
    const reencoded: readonly boolean[] = reportOf([
      { slug: 'constructor', upload: { constructorOptions: PIXELS } },
      { slug: 'format', upload: { formatOptions: { format: 'webp' } } },
      { slug: 'resize', upload: { resizeOptions: { width: 2000 } } },
      { slug: 'trim', upload: { trimOptions: { threshold: 10 } } },
      { slug: 'sizes', upload: { imageSizes: [{ name: 'card', width: 640 }] } },
      { slug: 'plain', upload: true },
    ]).pictureHandling.map((entry): boolean => entry.reencoded)
    expect(reencoded).toStrictEqual([true, true, true, true, false, false])
  })

  it('reads a collection that keeps the metadata always or by request, and one that keeps none', () => {
    const keeps: readonly boolean[] = reportOf([
      { slug: 'always', upload: { constructorOptions: PIXELS, withMetadata: true } },
      { slug: 'request', upload: { constructorOptions: PIXELS, withMetadata: (): boolean => true } },
      { slug: 'never', upload: { constructorOptions: PIXELS, withMetadata: false } },
      { slug: 'unset', upload: { constructorOptions: PIXELS } },
    ]).pictureHandling.map((entry): boolean => entry.keepsMetadata)
    expect(keeps).toStrictEqual([true, true, false, false])
  })

  it('passes over a collection that stores no uploads', () => {
    expect(reportOf([{ slug: 'posts' }, { slug: 'pages', upload: false }]).pictureHandling).toStrictEqual([])
  })

  it('refuses a configuration it cannot read rather than reporting nothing', () => {
    expect(() => pictureHandlingOf({})).toThrow('has no collections array')
    expect(() => reportOf([{ upload: true }])).toThrow('an upload collection has no resolved slug')
  })
})

describe('parsePictureHandlingReport', () => {
  const entry: Readonly<Record<string, unknown>> = {
    slug: 'media',
    pictures: true,
    reencoded: true,
    keepsMetadata: false,
  }

  it('reads the picture half of the report the probe printed', () => {
    expect(parsePictureHandlingReport(printed({ pictureHandling: [entry] }))).toStrictEqual({
      pictureHandling: [entry],
    })
  })

  it('reads missing or malformed evidence as no report, so the gate fails rather than passes', () => {
    expect(parsePictureHandlingReport(printed({ uploadStorage: [] }))).toBeUndefined()
    expect(parsePictureHandlingReport(printed({ pictureHandling: [{ ...entry, reencoded: 'yes' }] }))).toBeUndefined()
    expect(parsePictureHandlingReport(printed({ pictureHandling: [{ ...entry, slug: 1 }] }))).toBeUndefined()
    expect(parsePictureHandlingReport('no report at all')).toBeUndefined()
  })
})

describe('findKeptPictureMetadata', () => {
  const report: PictureHandlingReport = reportOf([
    { slug: 'media', upload: { mimeTypes: ['image/jpeg'] } },
    { slug: 'archive', upload: { mimeTypes: ['image/jpeg'], constructorOptions: PIXELS, withMetadata: true } },
    { slug: 'covers', upload: { mimeTypes: ['image/png'], constructorOptions: PIXELS } },
    { slug: 'documents', upload: { mimeTypes: ['application/pdf'] } },
  ])

  it('names a collection that stores a picture as it arrived, and points at an option and the declaration', () => {
    const findings: readonly string[] = findKeptPictureMetadata(report, [])
    expect(findings[0]).toContain('upload collection "media" stores an uploaded picture as it arrived')
    expect(findings[0]).toContain('constructorOptions: { limitInputPixels }')
    expect(findings[0]).toContain('airnessPayload.keptMetadata')
  })

  it('names a collection that re-encodes a picture but keeps its metadata', () => {
    const findings: readonly string[] = findKeptPictureMetadata(report, [])
    expect(findings).toHaveLength(2)
    expect(findings[1]).toContain('upload collection "archive" keeps the metadata of an uploaded picture')
    expect(findings[1]).toContain('upload.withMetadata')
  })

  it('accepts a collection the project keeps the metadata of on purpose', () => {
    expect(
      findKeptPictureMetadata(report, [
        { collection: 'media', reason: 'press photographs keep their credit' },
        { collection: 'archive', reason: 'the capture data is the point of the archive' },
      ]),
    ).toStrictEqual([])
  })

  it('reports a declaration that excuses no collection keeping a picture as it arrived', () => {
    const findings: readonly string[] = findKeptPictureMetadata(
      reportOf([{ slug: 'covers', upload: { constructorOptions: PIXELS } }]),
      [{ collection: 'covers', reason: 'no longer true' }],
    )
    expect(findings).toStrictEqual([
      'airnessPayload.keptMetadata declares "covers", which is no upload collection keeping the metadata of a ' +
        'picture; remove the declaration',
    ])
  })
})
