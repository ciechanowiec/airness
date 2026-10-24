import { describe, expect, it } from 'vitest'
import {
  ACCESSIBILITY_ROUTE_BUDGET,
  BUNDLE_BUDGET_BYTES,
  DEFAULT_SOURCE_ROOTS,
  type DeclaredExclusion,
  findConvenienceExclusions,
  findUnreachedExclusions,
  findUnreachedExclusionsBySetting,
  isHonouredExclusion,
  LAYOUT_MINIMUM_GAP,
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

describe('the application settings', () => {
  it('defaults every application setting to the strict end', () => {
    expect(readSettings({})).toMatchObject({
      serve: undefined,
      serverUrl: 'http://localhost:3000',
      bundleBudgetBytes: BUNDLE_BUDGET_BYTES,
      accessibilitySkipRoutes: [],
      accessibilityRouteBudget: ACCESSIBILITY_ROUTE_BUDGET,
      layoutMinimumGap: LAYOUT_MINIMUM_GAP,
      layoutViewports: [],
      pureLogicRoots: [],
    })
  })

  it('lets a project lower a ceiling and raise a floor, never the reverse', () => {
    const lowered: Settings = readSettings({
      airness: { bundleBudgetBytes: 1024, accessibilityRouteBudget: 10, layoutMinimumGap: 8 },
    })
    expect(lowered).toMatchObject({ bundleBudgetBytes: 1024, accessibilityRouteBudget: 10, layoutMinimumGap: 8 })
    const raised: Settings = readSettings({
      airness: { bundleBudgetBytes: BUNDLE_BUDGET_BYTES * 2, accessibilityRouteBudget: 1e6, layoutMinimumGap: 1 },
    })
    expect(raised).toMatchObject({
      bundleBudgetBytes: BUNDLE_BUDGET_BYTES,
      accessibilityRouteBudget: ACCESSIBILITY_ROUTE_BUDGET,
      layoutMinimumGap: LAYOUT_MINIMUM_GAP,
    })
  })

  it('reads the serve script, the origin, the viewports, the routes and the floor a project declares', () => {
    const settings: Settings = readSettings({
      airness: {
        serve: 'preview',
        serverUrl: 'http://localhost:4173',
        layoutViewports: [
          { width: 768, height: 1024 },
          { width: 0, height: 1 },
        ],
        accessibilitySkipRoutes: [{ pattern: '/drafts', reason: 'unpublished pages' }, '/silent'],
        pureLogicRoots: [{ pattern: 'src/domain/', reason: 'the pricing rules' }],
      },
    })
    expect(settings).toMatchObject({
      serve: 'preview',
      serverUrl: 'http://localhost:4173',
      layoutViewports: [{ width: 768, height: 1024 }],
      accessibilitySkipRoutes: ['/drafts'],
      accessibilityCustomSkipRoutes: ['/drafts'],
      pureLogicRoots: ['src/domain'],
    })
  })

  it('takes the floor and the skipped routes of a layer package beside the project ones', () => {
    const settings: Settings = readSettings({}, [
      {
        generatedArtefacts: [],
        frameworkGlue: [],
        javascriptRuntimes: [],
        pureLogicRoots: ['src/access'],
        accessibilitySkipRoutes: ['/admin'],
      },
    ])
    expect(settings).toMatchObject({
      pureLogicRoots: ['src/access'],
      accessibilitySkipRoutes: ['/admin'],
      accessibilityCustomSkipRoutes: [],
    })
  })

  it('judges a declared floor by the files below it, and never a route prefix', () => {
    const entries: readonly DeclaredExclusion[] = readSettings({
      airness: {
        pureLogicRoots: [
          { pattern: 'src/domain', reason: 'the pricing rules' },
          { pattern: 'src/gone', reason: 'removed' },
        ],
        accessibilitySkipRoutes: [{ pattern: '/drafts', reason: 'unpublished pages' }],
      },
    }).declaredExclusions
    expect(findUnreachedExclusionsBySetting(entries, ['src/domain/price.ts'])).toEqual([
      'pureLogicRoots entry "src/gone" excludes nothing; it matches no file the gate would have judged, so it ' +
        'records a decision with no effect',
    ])
  })
})
