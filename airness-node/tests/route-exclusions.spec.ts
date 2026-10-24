import { describe, expect, it } from 'vitest'
import { isSkippedRoute, isSubtreePrefix, skipRouteFindings } from '@/route-exclusions.js'
import type { DeclaredRoute } from '@/route-map.js'
import { type LayerDefaults, readSettings, type Settings } from '@/settings.js'

// A layer above the node layer, contributing the subtrees its framework serves outside the public site.
const FRAMEWORK_LAYER: LayerDefaults = {
  generatedArtefacts: [],
  frameworkGlue: [],
  javascriptRuntimes: [],
  accessibilitySkipRoutes: ['/admin', '/api'],
}

const isExcluded = (route: string, settings: Settings): boolean =>
  isSkippedRoute(route, settings.accessibilitySkipRoutes, settings.accessibilityCustomSkipRoutes)

const declaring = (...prefixes: readonly string[]): Settings =>
  readSettings({
    airness: {
      accessibilitySkipRoutes: prefixes.map((pattern: string) => ({ pattern, reason: 'unpublished pages' })),
    },
  })

const page = (route: string): DeclaredRoute => ({ file: `src/app${route}/page.tsx`, route, isDynamic: false })

describe('route exclusion provenance', () => {
  it.each(['/api', '/admin', '/about', '/preview'])(
    'skips nothing by default, so %s stays in scope',
    (route: string) => {
      expect(isExcluded(route, readSettings({}))).toBe(false)
    },
  )

  it.each(['/api', '/api/posts', '/admin', '/admin/users'])(
    'excludes the subtree a layer contributes at %s',
    (route: string) => {
      expect(isExcluded(route, readSettings({}, [FRAMEWORK_LAYER]))).toBe(true)
    },
  )

  it.each(['/apiary', '/api-documentation', '/administration-help', '/about'])(
    'keeps the public route %s in scope beside a layer prefix',
    (route: string) => {
      expect(isExcluded(route, readSettings({}, [FRAMEWORK_LAYER]))).toBe(false)
    },
  )

  it('matches a project prefix at a segment boundary too, so a stem takes no sibling with it', () => {
    const settings: Settings = declaring('/p', '/drafts/')
    expect(isExcluded('/products', settings)).toBe(false)
    expect(isExcluded('/p/one', settings)).toBe(true)
    expect(isExcluded('/drafts', settings)).toBe(true)
    expect(isExcluded('/drafts/one', settings)).toBe(true)
  })

  it('never honours a prefix that covers the whole site, or one that states no reason', () => {
    expect(isExcluded('/about', declaring('/', '//', ''))).toBe(false)
    expect(isExcluded('/apiary', readSettings({ airness: { accessibilitySkipRoutes: ['/api'] } }))).toBe(false)
    expect(isSubtreePrefix('/')).toBe(false)
    expect(isSubtreePrefix('drafts')).toBe(false)
    expect(isSubtreePrefix('/drafts')).toBe(true)
  })

  it('honours an empty exclusion list', () => {
    expect(isSkippedRoute('/api/posts', [])).toBe(false)
  })
})

describe('skipRouteFindings', () => {
  const pages: readonly DeclaredRoute[] = [page('/'), page('/drafts'), page('/drafts/one'), page('/about')]

  it('refuses a prefix that names the whole site, whatever the member declares', () => {
    expect(skipRouteFindings(declaring('/').declaredExclusions, undefined)).toEqual([
      'accessibilitySkipRoutes entry "/" names no subtree; write a prefix such as /drafts, never one that covers ' +
        'the whole site',
    ])
  })

  it('refuses a prefix that skips no declared page or every declared page, and keeps one that narrows', () => {
    expect(skipRouteFindings(declaring('/drafts').declaredExclusions, pages)).toEqual([])
    expect(skipRouteFindings(declaring('/gone').declaredExclusions, pages)).toEqual([
      'accessibilitySkipRoutes entry "/gone" skips no page the member declares, so it records a decision with no effect',
    ])
    expect(skipRouteFindings(declaring('/drafts').declaredExclusions, [page('/drafts')])).toEqual([
      'accessibilitySkipRoutes entry "/drafts" skips every page the member declares, which switches the ' +
        'accessibility sweep off',
    ])
    expect(skipRouteFindings(declaring('/gone').declaredExclusions, undefined)).toEqual([])
  })
})
