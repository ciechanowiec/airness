import { describe, expect, it } from 'vitest'
import type { InstalledPackage } from '@/installed-packages.js'
import { inventoryOf } from '@/inventory.js'
import type { Lockfile, ResolvedPackage } from '@/lockfile.js'
import { type ResolvedSet, resolvedSetOf } from '@/resolved-set.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

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

// An inventory entry of a package no member's dependencies reach, which therefore carries no notices.
const entry = (name: string, license: string, isDirect: boolean, isInstalled: boolean): Record<string, unknown> => ({
  name,
  version: '1.0.0',
  license,
  direct: isDirect,
  installed: isInstalled,
  production: false,
  notices: {},
})

describe('the npm inventory', () => {
  it('records every resolved package with its license, whether a member declares it, and whether it is here', () => {
    const lockfile: Lockfile = {
      importers: new Map([['.', [{ name: 'spdx', specifier: '1.0.0', version: '1.0.0', block: 'devDependencies' }]]]),
      roots: ['spdx@1.0.0'],
      productionRoots: [],
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
      contract: 2,
      packages: [
        entry('spdx', 'MIT OR Apache-2.0', true, true),
        entry('object', 'ISC', false, true),
        entry('listed', 'MIT OR BSD-3-Clause', false, true),
        entry('none', '', false, true),
        entry('native', '', false, false),
      ],
    })
  })

  it('tells a missing package this host should hold from one only a package of other systems needs', () => {
    const lockfile: Lockfile = {
      importers: new Map(),
      packages: [resolved('gone'), resolved('native', true), resolved('fallback')],
      roots: ['gone@1.0.0', 'native@1.0.0'],
      productionRoots: [],
      edges: new Map([['native@1.0.0', ['fallback@1.0.0']]]),
      rewrites: [],
    }
    const set: ResolvedSet = resolvedSetOf(lockfile, [])
    expect(set.missing.map((entry: ResolvedPackage): string => entry.name)).toEqual(['gone'])
    expect(set.elsewhere.map((entry: ResolvedPackage): string => entry.name)).toEqual(['native', 'fallback'])
  })

  it('marks what member dependencies reach as production and keeps the notice files of those alone', () => {
    const shipped: string = temporaryProject({ 'package.json': '{}\n', LICENSE: 'Shipped terms\n' })
    const tool: string = temporaryProject({ 'package.json': '{}\n', LICENSE: 'Tool terms\n' })
    try {
      const lockfile: Lockfile = {
        importers: new Map([
          [
            '.',
            [
              { name: 'shipped', specifier: '1.0.0', version: '1.0.0', block: 'dependencies' },
              { name: 'tool', specifier: '1.0.0', version: '1.0.0', block: 'devDependencies' },
            ],
          ],
        ]),
        packages: [resolved('shipped'), resolved('tool'), resolved('native', true)],
        roots: ['shipped@1.0.0', 'tool@1.0.0'],
        productionRoots: ['shipped@1.0.0'],
        edges: new Map([['shipped@1.0.0', ['native@1.0.0']]]),
        rewrites: [],
      }
      const set: ResolvedSet = resolvedSetOf(lockfile, [
        { ...installed('shipped', { license: 'MIT' }), directory: shipped },
        { ...installed('tool', { license: 'MIT' }), directory: tool },
      ])
      expect(
        inventoryOf(set).packages.map((written) => ({
          name: written.name,
          production: written.production,
          notices: written.notices,
        })),
      ).toEqual([
        { name: 'shipped', production: true, notices: { LICENSE: 'Shipped terms\n' } },
        { name: 'tool', production: false, notices: {} },
        { name: 'native', production: true, notices: {} },
      ])
    } finally {
      removeProject(shipped)
      removeProject(tool)
    }
  })
})
