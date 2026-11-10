import { describe, expect, it } from 'vitest'
import { matchesRole } from '@/file-roles.js'
import { isJavascriptRuntime, type RuntimeRole, rolesFor } from '@/javascript-runtimes.js'
import { findUndeclaredRuntimes, readSettings, type Settings } from '@/settings.js'

const EDGE: string = String.raw`^edge/viewer-request\.cjs$`
const REASON: string = 'the CloudFront Function of the website, which CloudFront runs without a build step'

const allowlisted = (entry: Readonly<Record<string, unknown>>): Settings =>
  readSettings({ airness: { javascriptAllowlist: [{ pattern: EDGE, reason: REASON, ...entry }] } })

describe('isJavascriptRuntime', () => {
  it('accepts the engines the harness lints for and nothing else', () => {
    expect(isJavascriptRuntime('node')).toBe(true)
    expect(isJavascriptRuntime('cloudfront-js-2.0')).toBe(true)
    expect(isJavascriptRuntime('cloudfront-js-1.0')).toBe(false)
    expect(isJavascriptRuntime(undefined)).toBe(false)
  })
})

describe('rolesFor', () => {
  const roles: readonly RuntimeRole[] = [
    { role: '^edge/', runtime: 'cloudfront-js-2.0' },
    { role: { memberPath: 'web', pattern: '^tools/' }, runtime: 'node' },
  ]

  it('selects the roles of one engine', () => {
    expect(rolesFor(roles, 'cloudfront-js-2.0')).toEqual(['^edge/'])
    expect(rolesFor(roles, 'node')).toEqual([{ memberPath: 'web', pattern: '^tools/' }])
  })

  it('keeps each selected role scoped as its entry is', () => {
    expect(matchesRole('edge/viewer-request.cjs', rolesFor(roles, 'cloudfront-js-2.0'))).toBe(true)
    expect(matchesRole('edge/viewer-request.cjs', rolesFor(roles, 'node'))).toBe(false)
    expect(matchesRole('web/tools/build.mjs', rolesFor(roles, 'node'))).toBe(true)
    expect(matchesRole('tools/build.mjs', rolesFor(roles, 'node'))).toBe(false)
  })
})

describe('the runtime an allowlist entry declares', () => {
  it('honours an entry that declares a known runtime', () => {
    const settings: Settings = allowlisted({ runtime: 'cloudfront-js-2.0' })
    expect(settings.javascriptAllowlist).toContain(EDGE)
    expect(rolesFor(settings.javascriptRuntimes, 'cloudfront-js-2.0')).toEqual([EDGE])
    expect(findUndeclaredRuntimes(settings.declaredExclusions)).toEqual([])
  })

  it('keeps the same file plain Node.js when it is declared so', () => {
    const settings: Settings = allowlisted({ runtime: 'node' })
    expect(rolesFor(settings.javascriptRuntimes, 'node')).toContain(EDGE)
    expect(rolesFor(settings.javascriptRuntimes, 'cloudfront-js-2.0')).toEqual([])
  })

  it('refuses an entry without a runtime, naming the values it may declare', () => {
    const settings: Settings = allowlisted({})
    expect(settings.javascriptAllowlist).not.toContain(EDGE)
    expect(findUndeclaredRuntimes(settings.declaredExclusions)).toEqual([
      `javascriptAllowlist entry "${EDGE}" declares no runtime; ` +
        'declare the engine its file runs in, one of: node, cloudfront-js-2.0',
    ])
  })

  it('refuses an entry declaring a runtime the harness does not lint for', () => {
    const settings: Settings = allowlisted({ runtime: 'cloudfront-js-1.0' })
    expect(settings.javascriptAllowlist).not.toContain(EDGE)
    expect(findUndeclaredRuntimes(settings.declaredExclusions)[0]).toContain(
      'declares unknown runtime "cloudfront-js-1.0"',
    )
  })

  it.each([
    ['a bare pattern', EDGE],
    ['a value that is not an entry', 0],
  ])('refuses %s, which can state no runtime', (_: string, entry: unknown) => {
    const settings: Settings = readSettings({ airness: { javascriptAllowlist: [entry] } })
    expect(findUndeclaredRuntimes(settings.declaredExclusions)[0]).toContain('declares no runtime')
  })

  it('asks no other setting for a runtime', () => {
    const settings: Settings = readSettings({
      airness: { typographyExclusions: [{ pattern: '^generated/', reason: 'generated' }] },
    })
    expect(findUndeclaredRuntimes(settings.declaredExclusions)).toEqual([])
  })

  it('declares the built-in allowlist as Node.js', () => {
    const roles: readonly RuntimeRole[] = readSettings({}).javascriptRuntimes
    expect(matchesRole('eslint.config.mjs', rolesFor(roles, 'node'))).toBe(true)
    expect(rolesFor(roles, 'cloudfront-js-2.0')).toEqual([])
  })
})
