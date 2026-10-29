import { describe, expect, it } from 'vitest'
import { findBlockedPackages, resolvedPackagesIn } from '@/payload-blocklist.js'

const LOCKFILE: string = [
  "lockfileVersion: '9.0'",
  'importers:',
  '  .:',
  '    dependencies:',
  '      payload:',
  '        specifier: 3.91.0',
  '        version: 3.91.0',
  'packages:',
  "  '@payloadcms/db-mongodb@3.91.0':",
  '    resolution: {integrity: sha512-a}',
  '  payload@3.91.0:',
  '    resolution: {integrity: sha512-b}',
  "  'tedious@19.0.0(peer@1.0.0)':",
  '    resolution: {integrity: sha512-c}',
  '',
].join('\n')

describe('resolvedPackagesIn', () => {
  it('reads every resolved package name once, scoped and peer-qualified ones included', () => {
    expect(resolvedPackagesIn(LOCKFILE)).toStrictEqual(['@payloadcms/db-mongodb', 'payload', 'tedious'])
  })

  it('reads a key that carries no version as the name itself', () => {
    expect(resolvedPackagesIn('packages:\n  local-tool:\n    resolution: {}\n')).toStrictEqual(['local-tool'])
  })

  it('reads a lockfile without packages as resolving nothing', () => {
    expect(resolvedPackagesIn("lockfileVersion: '9.0'\n")).toStrictEqual([])
  })
})

describe('findBlockedPackages', () => {
  it('names every refused package with its reason and replacement', () => {
    const findings: readonly string[] = findBlockedPackages(resolvedPackagesIn(LOCKFILE))
    expect(findings).toHaveLength(2)
    expect(findings[0]).toContain('@payloadcms/db-mongodb is refused by the Payload layer')
    expect(findings[0]).toContain('Use @payloadcms/db-postgres instead')
    expect(findings[1]).toContain('Payload has no adapter')
  })

  it('passes a resolved set without a refused package', () => {
    expect(findBlockedPackages(['payload', '@payloadcms/db-postgres', 'pg'])).toStrictEqual([])
  })
})
