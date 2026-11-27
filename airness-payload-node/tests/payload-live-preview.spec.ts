import { describe, expect, it } from 'vitest'
import { INHERITED_ACCESS_REPORT_MARKER } from '@/payload-defaults.js'
import {
  findMissingLivePreview,
  type LivePreviewReport,
  livePreviewOf,
  parseLivePreviewReport,
} from '@/payload-live-preview.js'

const DRAFTS: { readonly drafts: true } = { drafts: true }

const pageUrl = (): string => '/preview/posts/a-post'

const printed = (report: unknown): string =>
  `plugin chatter\n${INHERITED_ACCESS_REPORT_MARKER}${JSON.stringify(report)}\n`

const findingsOf = (config: unknown): readonly string[] => findMissingLivePreview(livePreviewOf(config))

describe('livePreviewOf', () => {
  it('reads Live Preview enabled on the entity itself, with a url function or a url string', () => {
    const report: LivePreviewReport = livePreviewOf({
      collections: [
        { slug: 'posts', versions: DRAFTS, admin: { livePreview: { url: pageUrl } } },
        { slug: 'pages', versions: { drafts: { autosave: true } }, admin: { livePreview: { url: '/preview/pages' } } },
      ],
      globals: [{ slug: 'home', versions: DRAFTS, admin: { livePreview: { url: '/' } } }],
    })
    expect(report.livePreview).toStrictEqual([
      { kind: 'collection', slug: 'posts', enabled: true, hasUrl: true },
      { kind: 'collection', slug: 'pages', enabled: true, hasUrl: true },
      { kind: 'global', slug: 'home', enabled: true, hasUrl: true },
    ])
  })

  it('reads Live Preview the root configuration enables, for collections and for globals, with its url', () => {
    const report: LivePreviewReport = livePreviewOf({
      admin: { livePreview: { url: pageUrl, collections: ['posts'], globals: ['home'] } },
      collections: [{ slug: 'posts', versions: DRAFTS }],
      globals: [{ slug: 'home', versions: DRAFTS }],
    })
    expect(report.livePreview).toStrictEqual([
      { kind: 'collection', slug: 'posts', enabled: true, hasUrl: true },
      { kind: 'global', slug: 'home', enabled: true, hasUrl: true },
    ])
  })

  it('takes the url from whichever level has it, as the admin panel merges the two', () => {
    expect(
      findingsOf({
        admin: { livePreview: { url: pageUrl, collections: ['listed'] } },
        collections: [
          { slug: 'own', versions: DRAFTS, admin: { livePreview: { breakpoints: [] } } },
          { slug: 'listed', versions: DRAFTS },
        ],
        globals: [],
      }),
    ).toStrictEqual([])
    expect(
      findingsOf({
        admin: { livePreview: { collections: ['listed'] } },
        collections: [{ slug: 'listed', versions: DRAFTS, admin: { livePreview: { url: pageUrl } } }],
        globals: [],
      }),
    ).toStrictEqual([])
  })

  it('passes over an entity that keeps no drafts', () => {
    expect(
      livePreviewOf({
        collections: [
          { slug: 'plain' },
          { slug: 'versioned', versions: { maxPerDoc: 10 } },
          { slug: 'off', versions: { drafts: false } },
        ],
        globals: [{ slug: 'settings' }],
      }).livePreview,
    ).toStrictEqual([])
  })

  it('refuses a configuration it cannot read rather than reporting nothing', () => {
    expect(() => livePreviewOf({ collections: [] })).toThrow('has no collections or globals array')
    expect(() => livePreviewOf({ collections: [{ versions: DRAFTS }], globals: [] })).toThrow(
      'a collection that keeps drafts has no resolved slug',
    )
  })
})

describe('findMissingLivePreview', () => {
  it('names an entity that keeps drafts with no Live Preview, by its kind and the root list that would name it', () => {
    expect(
      findingsOf({
        collections: [{ slug: 'posts', versions: DRAFTS, admin: { preview: pageUrl } }],
        globals: [{ slug: 'home', versions: DRAFTS, admin: { livePreview: false } }],
      }),
    ).toStrictEqual([
      expect.stringMatching(
        /^collection "posts" keeps drafts but has no Live Preview.*admin\.livePreview\.collections$/u,
      ),
      expect.stringMatching(/^global "home" keeps drafts but has no Live Preview.*admin\.livePreview\.globals$/u),
    ])
  })

  it('does not count an entity the root lists for the other kind', () => {
    expect(
      findingsOf({
        admin: { livePreview: { url: pageUrl, globals: ['posts'] } },
        collections: [{ slug: 'posts', versions: DRAFTS }],
        globals: [],
      }),
    ).toStrictEqual([expect.stringContaining('collection "posts" keeps drafts but has no Live Preview')])
  })

  it('names Live Preview enabled without a url, or with an empty one', () => {
    expect(
      findingsOf({
        admin: { livePreview: { collections: ['listed'] } },
        collections: [
          { slug: 'listed', versions: DRAFTS },
          { slug: 'empty', versions: DRAFTS, admin: { livePreview: { url: '' } } },
        ],
        globals: [],
      }),
    ).toStrictEqual([
      expect.stringContaining('collection "listed" enables Live Preview without a url'),
      expect.stringContaining('collection "empty" enables Live Preview without a url'),
    ])
  })
})

describe('parseLivePreviewReport', () => {
  const entry: Readonly<Record<string, unknown>> = { kind: 'collection', slug: 'posts', enabled: true, hasUrl: true }

  it('reads the Live Preview half of the report the probe printed', () => {
    expect(parseLivePreviewReport(printed({ livePreview: [entry] }))).toStrictEqual({ livePreview: [entry] })
  })

  it('reads missing or malformed evidence as no report, so the gate fails rather than passes', () => {
    expect(parseLivePreviewReport('no report at all')).toBeUndefined()
    expect(parseLivePreviewReport(printed({}))).toBeUndefined()
    expect(parseLivePreviewReport(printed({ livePreview: [{ ...entry, kind: 'block' }] }))).toBeUndefined()
    expect(parseLivePreviewReport(printed({ livePreview: [{ ...entry, hasUrl: 'yes' }] }))).toBeUndefined()
    expect(parseLivePreviewReport(printed({ livePreview: ['posts'] }))).toBeUndefined()
  })
})
