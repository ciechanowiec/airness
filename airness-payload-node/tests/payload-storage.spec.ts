import { describe, expect, it } from 'vitest'
import { INHERITED_ACCESS_REPORT_MARKER } from '@/payload-defaults.js'
import {
  findLocalUploadStorage,
  parseUploadStorageReport,
  type UploadStorageReport,
  uploadStorageOf,
} from '@/payload-storage.js'

const reportOf = (collections: readonly unknown[]): UploadStorageReport => uploadStorageOf({ collections })

const printed = (report: unknown): string =>
  `plugin chatter\n${INHERITED_ACCESS_REPORT_MARKER}${JSON.stringify(report)}\n`

describe('uploadStorageOf', () => {
  it('reads every spelling of an upload collection that keeps Payload storing on the local disk', () => {
    expect(
      reportOf([
        { slug: 'flag', upload: true },
        { slug: 'object', upload: { mimeTypes: ['image/png'] } },
        { slug: 'directory', upload: { staticDir: 'media' } },
        { slug: 'kept', upload: { disableLocalStorage: false } },
      ]).uploadStorage,
    ).toStrictEqual([
      { slug: 'flag', local: true },
      { slug: 'object', local: true },
      { slug: 'directory', local: true },
      { slug: 'kept', local: true },
    ])
  })

  it('reads an upload collection a storage adapter took over, or one switched off by hand, as remote', () => {
    expect(
      reportOf([
        { slug: 'adapter', upload: { disableLocalStorage: true, adapter: 's3' } },
        { slug: 'manual', upload: { staticDir: 'media', disableLocalStorage: true } },
      ]).uploadStorage,
    ).toStrictEqual([
      { slug: 'adapter', local: false },
      { slug: 'manual', local: false },
    ])
  })

  it('passes over a collection that stores no uploads', () => {
    expect(reportOf([{ slug: 'posts' }, { slug: 'pages', upload: false }]).uploadStorage).toStrictEqual([])
  })

  it('refuses a configuration it cannot read rather than reporting nothing', () => {
    expect(() => uploadStorageOf({})).toThrow('has no collections array')
    expect(() => reportOf([{ upload: true }])).toThrow('an upload collection has no resolved slug')
  })
})

describe('parseUploadStorageReport', () => {
  it('reads the storage half of the report the probe printed', () => {
    expect(parseUploadStorageReport(printed({ uploadStorage: [{ slug: 'media', local: false }] }))).toStrictEqual({
      uploadStorage: [{ slug: 'media', local: false }],
    })
  })

  it('reads missing or malformed evidence as no report, so the gate fails rather than passes', () => {
    expect(parseUploadStorageReport(printed({ collections: [] }))).toBeUndefined()
    expect(parseUploadStorageReport(printed({ uploadStorage: [{ slug: 'media', local: 'yes' }] }))).toBeUndefined()
    expect(parseUploadStorageReport(printed({ uploadStorage: [{ local: true }] }))).toBeUndefined()
    expect(parseUploadStorageReport('no report at all')).toBeUndefined()
  })
})

describe('findLocalUploadStorage', () => {
  const report: UploadStorageReport = {
    uploadStorage: [
      { slug: 'media', local: true },
      { slug: 'documents', local: false },
    ],
  }

  it('names an upload collection on the local disk, and points at a storage adapter or the declaration', () => {
    const findings: readonly string[] = findLocalUploadStorage(report, [])
    expect(findings).toHaveLength(1)
    expect(findings[0]).toContain('upload collection "media" stores its files on the server\'s local disk')
    expect(findings[0]).toContain('@payloadcms/storage-s3')
    expect(findings[0]).toContain('airnessPayload.localStorage')
  })

  it('accepts a collection the project keeps on the local disk on purpose', () => {
    expect(findLocalUploadStorage(report, [{ collection: 'media', reason: 'one server, backed-up volume' }])).toEqual(
      [],
    )
  })

  it('names a declaration that excuses no collection on the local disk, whether remote or absent', () => {
    expect(
      findLocalUploadStorage({ uploadStorage: [{ slug: 'documents', local: false }] }, [
        { collection: 'documents', reason: 'stale' },
        { collection: 'gone', reason: 'stale' },
      ]),
    ).toStrictEqual([
      'airnessPayload.localStorage declares "documents", which is no upload collection storing on the local disk; ' +
        'remove the declaration',
      'airnessPayload.localStorage declares "gone", which is no upload collection storing on the local disk; ' +
        'remove the declaration',
    ])
  })

  it('passes a configuration whose every upload collection stores remotely', () => {
    expect(findLocalUploadStorage({ uploadStorage: [{ slug: 'documents', local: false }] }, [])).toEqual([])
  })
})
