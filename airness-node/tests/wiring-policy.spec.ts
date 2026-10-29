import { describe, expect, it } from 'vitest'
import type { DirectDependency, Lockfile, ResolvedPackage } from '@/lockfile.js'
import { type WiringInputs, wiringFindings } from '@/wiring-policy.js'

const PINS: Readonly<Record<string, string>> = { vitest: '5.0.5', '@vitest/coverage-v8': '5.0.5', yaml: '2.9.1' }

const SUITE: readonly DirectDependency[] = [
  { name: 'vitest', specifier: '5.0.5', version: '5.0.5' },
  { name: '@vitest/coverage-v8', specifier: '5.0.5', version: '5.0.5' },
]

const HARNESS: DirectDependency = { name: '@airness/node', specifier: '2.0.0', version: '2.0.0' }

const lockfileOf = (
  importers: Readonly<Record<string, readonly DirectDependency[]>>,
  packages: readonly ResolvedPackage[],
): Lockfile => ({ importers: new Map(Object.entries(importers)), packages, roots: [], edges: new Map(), rewrites: [] })

const resolved = (name: string, version: string): ResolvedPackage => ({
  name,
  version,
  source: 'registry',
  tarball: '',
  isPlatformLimited: false,
})

const CONFORMING: WiringInputs = {
  memberPath: '.',
  manifest: { name: 'shop' },
  lockfile: lockfileOf({ '.': [HARNESS, ...SUITE] }, [resolved('@airness/node', '2.0.0')]),
  expected: new Map([['@airness/node', '2.0.0']]),
  pins: PINS,
  hasSource: true,
  rewrites: [],
}

describe('wiring', () => {
  it('passes the harness at the parent version with the suite libraries at the harness versions', () => {
    expect(wiringFindings(CONFORMING)).toEqual([])
  })

  it('fails without a lockfile, or without the member in it', () => {
    expect(wiringFindings({ ...CONFORMING, lockfile: undefined })).toEqual([
      'pnpm-lock.yaml is missing or unreadable; the harness compares the versions a lockfile resolves',
    ])
    expect(wiringFindings({ ...CONFORMING, memberPath: 'gate' })).toEqual([
      'pnpm-lock.yaml has no importer for gate; run pnpm install to bring the lockfile up to date',
    ])
  })

  it('fails when the parent versions were not supplied', () => {
    expect(wiringFindings({ ...CONFORMING, expected: undefined }).join('\n')).toContain('the versions of the module')
  })

  it('fails a project that resolves no harness', () => {
    expect(wiringFindings({ ...CONFORMING, lockfile: lockfileOf({ '.': SUITE }, []) })).toEqual([
      'pnpm-lock.yaml resolves no @airness/node; declare it as a development dependency and run pnpm install',
    ])
  })

  it('fails a resolved harness whose version is not the parent version, transitive or direct', () => {
    const lockfile: Lockfile = lockfileOf({ '.': SUITE }, [
      resolved('@airness/node', '1.9.0'),
      resolved('@airness/payload', '1.4.0'),
    ])
    expect(wiringFindings({ ...CONFORMING, lockfile })).toEqual([
      "pnpm-lock.yaml resolves @airness/node 1.9.0, but the module's Airness parent names 2.0.0; install 2.0.0",
      "pnpm-lock.yaml resolves @airness/payload 1.4.0, but no parent in the module's chain names a version of it",
    ])
  })

  it('refuses a harness installed from a local path, but not the harness linking itself', () => {
    const linked: DirectDependency = { name: '@airness/node', specifier: 'link:../node', version: 'link:../node' }
    const local: WiringInputs = {
      ...CONFORMING,
      lockfile: lockfileOf({ '.': [linked, ...SUITE] }, [resolved('@airness/node', '2.0.0')]),
    }
    expect(wiringFindings(local)).toEqual([
      'package.json declares @airness/node as "link:../node"; install the released package by its version, ' +
        'which is what the gates are proven against',
    ])
    const self: DirectDependency = { name: '@airness/node', specifier: 'link:.', version: 'link:.' }
    const harness: WiringInputs = {
      ...CONFORMING,
      manifest: { name: '@airness/node', version: '2.0.0' },
      lockfile: lockfileOf({ '.': [self, ...SUITE] }, []),
      moduleVersion: '2.0.0',
      module: 'eu.ciechanowiec:airness-node',
    }
    expect(wiringFindings(harness)).toEqual([])
    expect(wiringFindings({ ...harness, manifest: { name: '@airness/node', version: '2.0.1' } })).toEqual([
      "package.json versions @airness/node 2.0.1, but the module that builds it is 2.0.0; release the package at the module's version",
      "pnpm-lock.yaml resolves @airness/node 2.0.1, but the module's Airness parent names 2.0.0; install 2.0.0",
    ])
  })

  it('holds a library the harness decides to the harness version, and leaves the others alone', () => {
    const drifted: readonly DirectDependency[] = [
      HARNESS,
      { name: 'vitest', specifier: '^5.0.0', version: '5.0.4' },
      { name: '@vitest/coverage-v8', specifier: '5.0.5', version: '5.0.5' },
      { name: 'yaml', specifier: '2.8.0', version: '2.8.0' },
    ]
    expect(
      wiringFindings({ ...CONFORMING, lockfile: lockfileOf({ '.': drifted }, [resolved('@airness/node', '2.0.0')]) }),
    ).toEqual(['vitest resolves to 5.0.4, but the harness runs 5.0.5; declare it at exactly 5.0.5'])
  })

  it('asks a member with source for the suite libraries, and a member without none', () => {
    const bare: Lockfile = lockfileOf({ '.': [HARNESS] }, [resolved('@airness/node', '2.0.0')])
    expect(wiringFindings({ ...CONFORMING, lockfile: bare })).toEqual([
      "vitest is not declared; the tests gate runs the member's own copy, so declare it as a development " +
        'dependency at 5.0.5',
      "@vitest/coverage-v8 is not declared; the tests gate runs the member's own copy, so declare it as a " +
        'development dependency at 5.0.5',
    ])
    expect(wiringFindings({ ...CONFORMING, lockfile: bare, hasSource: false })).toEqual([])
    expect(wiringFindings({ ...CONFORMING, lockfile: bare, pins: {} }).join('\n')).toContain('at the harness version')
  })

  it('judges a nested member by its own importer only', () => {
    const lockfile: Lockfile = lockfileOf({ '.': [HARNESS, ...SUITE], gate: [] }, [resolved('@airness/node', '2.0.0')])
    expect(
      wiringFindings({ ...CONFORMING, memberPath: 'gate', lockfile, hasSource: false, expected: undefined }),
    ).toEqual([])
  })

  it('reports every install-time rewrite at the project root, whatever versions resolve', () => {
    const rewrites: readonly string[] = ['pnpm-workspace.yaml overrides: it changes what a resolved package contains']
    expect(wiringFindings({ ...CONFORMING, rewrites })).toEqual(rewrites)
    expect(wiringFindings({ ...CONFORMING, rewrites, expected: undefined })[0]).toBe(rewrites[0])
  })

  it('lets any harness package link itself in its own repository, and no other', () => {
    const self: DirectDependency = { name: '@airness/payload', specifier: 'link:.', version: 'link:.' }
    const layerPackage: WiringInputs = {
      ...CONFORMING,
      manifest: { name: '@airness/payload', version: '1.4.0' },
      lockfile: lockfileOf({ '.': [self, HARNESS, ...SUITE] }, [resolved('@airness/node', '2.0.0')]),
      expected: new Map([
        ['@airness/node', '2.0.0'],
        ['@airness/payload', '1.4.0'],
      ]),
      moduleVersion: '1.4.0',
      module: 'eu.ciechanowiec:airness-payload-node',
    }
    expect(wiringFindings(layerPackage)).toEqual([])
    expect(wiringFindings({ ...layerPackage, manifest: { name: 'shop' } }).join('\n')).toContain(
      'package.json declares @airness/payload as "link:."',
    )
  })

  it('holds a harness package whose chain does not name it to the version of the module that builds it', () => {
    const self: DirectDependency = { name: '@airness/payload', specifier: 'link:.', version: 'link:.' }
    const library: WiringInputs = {
      ...CONFORMING,
      manifest: { name: '@airness/payload', version: '2.0.0-SNAPSHOT' },
      lockfile: lockfileOf({ '.': [self, HARNESS, ...SUITE] }, [resolved('@airness/node', '2.0.0')]),
      moduleVersion: '2.0.0-SNAPSHOT',
      module: 'eu.ciechanowiec:airness-payload-node',
    }
    expect(wiringFindings(library)).toEqual([])
    expect(wiringFindings({ ...library, moduleVersion: '2.0.1' })).toEqual([
      "package.json versions @airness/payload 2.0.0-SNAPSHOT, but the module that builds it is 2.0.1; release the package at the module's version",
    ])
    expect(wiringFindings({ ...library, moduleVersion: undefined }).join('\n')).toContain(
      "the module's own version was not supplied in AIRNESS_MODULE_VERSION",
    )
    // A consumer is judged as before: a harness package it resolves needs a parent that names its version.
    const consumer: Lockfile = lockfileOf({ '.': [HARNESS, ...SUITE] }, [
      resolved('@airness/node', '2.0.0'),
      resolved('@airness/payload', '2.0.0-SNAPSHOT'),
    ])
    expect(wiringFindings({ ...CONFORMING, lockfile: consumer, moduleVersion: '2.0.0-SNAPSHOT' })).toEqual([
      "pnpm-lock.yaml resolves @airness/payload 2.0.0-SNAPSHOT, but no parent in the module's chain names a version of it",
    ])
  })

  it('refuses a consumer posing as a harness package, at the root and in any member', () => {
    const self: DirectDependency = { name: '@airness/payload', specifier: 'link:.', version: 'link:.' }
    const poser: WiringInputs = {
      ...CONFORMING,
      manifest: { name: '@airness/payload', version: '1.0.0' },
      lockfile: lockfileOf({ '.': [self, HARNESS, ...SUITE] }, [resolved('@airness/node', '2.0.0')]),
      moduleVersion: '1.0.0',
      module: 'com.example:shop',
    }
    const posing: string =
      'package.json names the package @airness/payload, which only the module eu.ciechanowiec:airness-payload-node builds, and ' +
      'this module is com.example:shop; give the package a name of its own'
    const linked: string =
      'package.json declares @airness/payload as "link:."; install the released package by its version, which is what the ' +
      'gates are proven against'
    expect(wiringFindings(poser)).toEqual([posing, linked])
    const member: Lockfile = lockfileOf({ '.': [HARNESS, ...SUITE], gate: [self] }, [
      resolved('@airness/node', '2.0.0'),
    ])
    expect(wiringFindings({ ...poser, memberPath: 'gate', lockfile: member, hasSource: false })).toEqual([
      posing,
      linked,
    ])
    expect(wiringFindings({ ...poser, module: undefined })[0]).toContain('not named in AIRNESS_MODULE')
  })
})
