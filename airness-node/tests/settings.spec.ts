import { describe, expect, it } from 'vitest'
import {
  DEFAULT_SOURCE_ROOTS,
  findConvenienceExclusions,
  findUnreachedExclusions,
  isHonouredExclusion,
  readSettings,
  type Settings,
} from '@/settings.js'

const REASON: string = 'written by the API client generator'

describe('reading the airness block of package.json', () => {
  it('gives a project that declares nothing the strict defaults', () => {
    const settings: Settings = readSettings({ name: 'shop' })
    expect(settings.sourceRoots).toEqual(DEFAULT_SOURCE_ROOTS)
    expect(settings.generatedArtefacts).toEqual([])
    expect(settings.frameworkGlue).toEqual([])
    expect(settings.declaredExclusions).toEqual([])
  })

  it('adds declared source roots to the defaults rather than replacing them', () => {
    expect(readSettings({ airness: { sourceRoots: ['lib', 'src'] } }).sourceRoots).toEqual([
      ...DEFAULT_SOURCE_ROOTS,
      'lib',
    ])
  })

  it('falls back to the defaults on a malformed value rather than widening a rule', () => {
    const settings: Settings = readSettings({ airness: { sourceRoots: 'lib', generatedArtefacts: [1] } })
    expect(settings.sourceRoots).toEqual(DEFAULT_SOURCE_ROOTS)
    expect(settings.generatedArtefacts).toEqual([])
  })

  it('reads no other key than airness', () => {
    expect(readSettings({ ploaness: { generatedArtefacts: [{ pattern: 'src/api/**', reason: REASON }] } })).toEqual(
      readSettings({}),
    )
  })

  it('honours a generated-file role only with a reason', () => {
    const settings: Settings = readSettings({
      airness: { generatedArtefacts: [{ pattern: 'src/api/**', reason: REASON }, 'src/other/**'] },
    })
    expect(settings.generatedArtefacts).toEqual(['src/api/**'])
    expect(findConvenienceExclusions(settings.declaredExclusions)).toEqual([
      'generatedArtefacts entry "src/other/**" states no reason; an exclusion is granted by file role, ' +
        'so the role must be written down',
    ])
  })

  it('honours a JavaScript allowlist entry only with the engine that runs it', () => {
    const declared: Settings = readSettings({
      airness: { javascriptAllowlist: [{ pattern: '^edge/', reason: REASON, runtime: 'node' }] },
    })
    const undeclared: Settings = readSettings({
      airness: { javascriptAllowlist: [{ pattern: '^edge/', reason: REASON }] },
    })
    expect(declared.javascriptAllowlist).toContain('^edge/')
    expect(undeclared.javascriptAllowlist).not.toContain('^edge/')
    expect(undeclared.declaredExclusions.every(isHonouredExclusion)).toBe(false)
  })
})

describe('an exclusion that reaches nothing', () => {
  it('is reported by the setting and pattern it was declared under', () => {
    const settings: Settings = readSettings({ airness: { frameworkGlue: [{ pattern: 'src/app/**', reason: REASON }] } })
    expect(findUnreachedExclusions(settings.declaredExclusions, ['src/lib/read.ts'])).toEqual([
      'frameworkGlue entry "src/app/**" excludes nothing; it matches no file the gate would have judged, ' +
        'so it records a decision with no effect',
    ])
    expect(findUnreachedExclusions(settings.declaredExclusions, ['src/app/page.ts'])).toEqual([])
  })
})
