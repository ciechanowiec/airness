// The Payload stack's registration in the node layer: the one place its package and module are named as data, the
// ESLint relaxations the harness grants it, and the wiring and release-age rules it is held to. Kept apart from the
// generic specs, which hold no stack name and inject the layer names they read, so that removing the stack removes
// this file and leaves their coverage whole.
import { describe, expect, it } from 'vitest'
import { gateById } from '@/gates.js'
import { HARNESS_MODULES, HARNESS_PACKAGES, isHarnessPackage } from '@/harness-packages.js'
import { LAYER_NAMES, LAYER_PACKAGES } from '@/layer.js'
import { composeLayered } from '@/layer-lint-policy.js'
import type { DirectDependency, Lockfile, ResolvedPackage } from '@/lockfile.js'
import { releaseAgeSettingFindings } from '@/release-age.js'
import { type WiringInputs, wiringFindings } from '@/wiring-policy.js'

const LAYER: string = '@airness/payload'

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

const CONSUMER: WiringInputs = {
  memberPath: '.',
  manifest: { name: 'shop' },
  lockfile: lockfileOf({ '.': [HARNESS, ...SUITE] }, [resolved('@airness/node', '2.0.0')]),
  expected: new Map([['@airness/node', '2.0.0']]),
  pins: { vitest: '5.0.5', '@vitest/coverage-v8': '5.0.5' },
  hasSource: true,
  rewrites: [],
}

describe('the Payload registration', () => {
  it('registers @airness/payload as the one stack layer, built by airness-payload-node', () => {
    expect(LAYER_PACKAGES).toEqual({ [LAYER]: 'eu.ciechanowiec:airness-payload-node' })
    expect(HARNESS_MODULES.get(LAYER)).toBe('eu.ciechanowiec:airness-payload-node')
    expect(HARNESS_PACKAGES).toEqual(['@airness/node', LAYER])
    expect(isHarnessPackage(LAYER)).toBe(true)
  })

  it('leaves the Payload gates to the Payload package', () => {
    expect(gateById('payload-defaults')).toBeUndefined()
  })

  it('lets a Payload project exclude the Payload package from the release-age floor', () => {
    expect(
      releaseAgeSettingFindings({
        minimumReleaseAge: 1440,
        minimumReleaseAgeStrict: true,
        minimumReleaseAgeExclude: ['@airness/node', `${LAYER}@1.4.0`],
      }),
    ).toEqual([])
  })

  it('admits the relaxations the harness lists for the Payload layer, for exactly the files it lists', () => {
    const node: readonly unknown[] = [
      {
        rules: {
          'unicorn/filename-case': ['error', { cases: { kebabCase: true } }],
          'unicorn/no-top-level-side-effects': 'error',
        },
      },
      { files: ['**/*.ts'], rules: { 'functional/immutable-data': ['error', { ignoreAccessorPattern: [] }] } },
    ]
    const blocks: readonly unknown[] = [
      { files: ['src/payload.config.ts'], rules: { 'unicorn/no-top-level-side-effects': 'off' } },
      {
        files: ['src/migrations/**/*.ts'],
        rules: { 'unicorn/filename-case': ['error', { cases: { kebabCase: true, snakeCase: true } }] },
      },
      {
        files: ['src/hooks/**'],
        rules: { 'functional/immutable-data': ['error', { ignoreAccessorPattern: ['req.context.*.**'] }] },
      },
    ]
    expect(LAYER_NAMES).toContain(LAYER)
    expect(composeLayered(node, [{ name: LAYER, config: [...node, ...blocks] }])).toHaveLength(node.length + 3)
    for (const block of blocks) {
      expect(() => composeLayered(node, [{ name: 'other-layer', config: [...node, block] }])).toThrow(
        'stack layer other-layer weakens the node rule',
      )
    }
    expect(() =>
      composeLayered(node, [
        {
          name: LAYER,
          config: [...node, { files: ['src/**'], rules: { 'unicorn/no-top-level-side-effects': 'off' } }],
        },
      ]),
    ).toThrow('unicorn/no-top-level-side-effects')
  })
})

describe('the wiring of the Payload package', () => {
  it('holds it to the version of airness-parent-payload, which only a Payload module names', () => {
    const lockfile: Lockfile = lockfileOf({ '.': SUITE }, [
      resolved('@airness/node', '2.0.0'),
      resolved(LAYER, '1.4.0'),
    ])
    expect(wiringFindings({ ...CONSUMER, lockfile })).toEqual([
      `pnpm-lock.yaml resolves ${LAYER} 1.4.0, but no parent in the module's chain names a version of it`,
    ])
    expect(
      wiringFindings({
        ...CONSUMER,
        lockfile,
        expected: new Map([
          ['@airness/node', '2.0.0'],
          [LAYER, '1.4.0'],
        ]),
      }),
    ).toEqual([])
  })

  it('lets airness-payload-node link the package to itself at its own version, and refuses a poser', () => {
    const self: DirectDependency = { name: LAYER, specifier: 'link:.', version: 'link:.' }
    const library: WiringInputs = {
      ...CONSUMER,
      manifest: { name: LAYER, version: '2.0.0-SNAPSHOT' },
      lockfile: lockfileOf({ '.': [self, HARNESS, ...SUITE] }, [resolved('@airness/node', '2.0.0')]),
      moduleVersion: '2.0.0-SNAPSHOT',
      module: 'eu.ciechanowiec:airness-payload-node',
    }
    expect(wiringFindings(library)).toEqual([])
    expect(wiringFindings({ ...library, module: 'com.example:shop' }).join('\n')).toContain(
      `package.json names the package ${LAYER}, which only the module eu.ciechanowiec:airness-payload-node builds`,
    )
  })
})
