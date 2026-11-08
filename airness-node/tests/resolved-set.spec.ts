import { describe, expect, it } from 'vitest'
import type { InstalledPackage } from '@/installed-packages.js'
import type { Lockfile, ResolvedPackage } from '@/lockfile.js'
import { type ResolvedSet, resolvedSetOf } from '@/resolved-set.js'

const PACKAGES: number = 2000

const FAN_OUT: number = 5

const nameOf = (index: number): string => `package-${String(index)}`

const keyOf = (index: number): string => `${nameOf(index)}@1.0.0`

const resolved = (index: number): ResolvedPackage => ({
  name: nameOf(index),
  version: '1.0.0',
  source: 'registry',
  tarball: '',
  isPlatformLimited: false,
})

// Each package depends on the next five, wrapping around, so the edges outnumber the packages fivefold and many
// edges reach a package another edge reached first.
const widelyShared: Lockfile = {
  importers: new Map(),
  packages: Array.from({ length: PACKAGES }, (_unused: unknown, index: number): ResolvedPackage => resolved(index)),
  roots: [keyOf(0), keyOf(1)],
  edges: new Map(
    Array.from({ length: PACKAGES }, (_unused: unknown, index: number): readonly [string, readonly string[]] => [
      keyOf(index),
      Array.from({ length: FAN_OUT }, (_edge: unknown, step: number): string =>
        keyOf((index * FAN_OUT + step + 1) % PACKAGES),
      ),
    ]),
  ),
  rewrites: [],
}

describe('the resolved set', () => {
  it('walks the ten thousand edges of a large monorepo lockfile and expects every package it reaches', () => {
    const set: ResolvedSet = resolvedSetOf(widelyShared, [])
    expect(set.missing).toHaveLength(PACKAGES)
    expect(set.elsewhere).toEqual([])
  })

  it('reaches each package once through a shared dependency and a cycle', () => {
    const installed: readonly InstalledPackage[] = [
      { name: nameOf(0), version: '1.0.0', directory: '/store/0', manifest: {} },
    ]
    const cyclic: Lockfile = {
      ...widelyShared,
      packages: [resolved(0), resolved(1), resolved(2), resolved(3)],
      roots: [keyOf(0)],
      edges: new Map([
        [keyOf(0), [keyOf(1), keyOf(2)]],
        [keyOf(1), [keyOf(2), keyOf(0)]],
        [keyOf(2), [keyOf(1)]],
      ]),
    }
    const set: ResolvedSet = resolvedSetOf(cyclic, installed)
    expect(set.installed.map((entry: InstalledPackage): string => entry.name)).toEqual([nameOf(0)])
    expect(set.missing.map((entry: ResolvedPackage): string => entry.name)).toEqual([nameOf(1), nameOf(2)])
    expect(set.elsewhere.map((entry: ResolvedPackage): string => entry.name)).toEqual([nameOf(3)])
  })
})
