import { describe, expect, it } from 'vitest'
import type { InstalledPackage } from '@/installed-packages.js'
import { inventoryOf } from '@/inventory.js'
import type { Lockfile, ResolvedPackage } from '@/lockfile.js'
import { type ResolvedSet, resolvedSetOf } from '@/resolved-set.js'

const installed = (name: string, manifest: Readonly<Record<string, unknown>>): InstalledPackage => ({
  name,
  version: '1.0.0',
  directory: `/store/${name}`,
  manifest: { name, version: '1.0.0', ...manifest },
})

const resolved = (name: string, isPlatformLimited: boolean = false): ResolvedPackage => ({
  name,
  version: '1.0.0',
  source: 'registry',
  tarball: '',
  isPlatformLimited,
})

describe('the npm inventory', () => {
  it('records every resolved package with its license, whether a member declares it, and whether it is here', () => {
    const lockfile: Lockfile = {
      importers: new Map([['.', [{ name: 'spdx', specifier: '1.0.0', version: '1.0.0' }]]]),
      roots: ['spdx@1.0.0'],
      edges: new Map([['spdx@1.0.0', ['object@1.0.0', 'listed@1.0.0', 'none@1.0.0', 'native@1.0.0']]]),
      packages: ['spdx', 'object', 'listed', 'none']
        .map((name: string): ResolvedPackage => resolved(name))
        .concat([resolved('native', true)]),
      rewrites: [],
    }
    const set: ResolvedSet = resolvedSetOf(lockfile, [
      installed('spdx', { license: 'MIT OR Apache-2.0' }),
      installed('object', { license: { type: 'ISC' } }),
      installed('listed', { licenses: [{ type: 'MIT' }, { type: 'BSD-3-Clause' }, {}] }),
      installed('none', {}),
      installed('stale', { license: 'MIT' }),
    ])
    expect(inventoryOf(set)).toEqual({
      contract: 1,
      packages: [
        { name: 'spdx', version: '1.0.0', license: 'MIT OR Apache-2.0', direct: true, installed: true },
        { name: 'object', version: '1.0.0', license: 'ISC', direct: false, installed: true },
        { name: 'listed', version: '1.0.0', license: 'MIT OR BSD-3-Clause', direct: false, installed: true },
        { name: 'none', version: '1.0.0', license: '', direct: false, installed: true },
        { name: 'native', version: '1.0.0', license: '', direct: false, installed: false },
      ],
    })
  })

  it('tells a missing package this host should hold from one only a package of other systems needs', () => {
    const lockfile: Lockfile = {
      importers: new Map(),
      packages: [resolved('gone'), resolved('native', true), resolved('fallback')],
      roots: ['gone@1.0.0', 'native@1.0.0'],
      edges: new Map([['native@1.0.0', ['fallback@1.0.0']]]),
      rewrites: [],
    }
    const set: ResolvedSet = resolvedSetOf(lockfile, [])
    expect(set.missing.map((entry: ResolvedPackage): string => entry.name)).toEqual(['gone'])
    expect(set.elsewhere.map((entry: ResolvedPackage): string => entry.name)).toEqual(['native', 'fallback'])
  })
})
