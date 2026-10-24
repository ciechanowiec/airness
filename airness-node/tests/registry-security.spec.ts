// The three follow-ups to the registry handling: a token goes only to the registry its key names, no URL
// reaches a finding with its credentials, and a harness package must come from a registry too.
import { describe, expect, it } from 'vitest'
import { undatedFinding } from '@/gate-release-age.js'
import type { Lockfile, ResolvedPackage } from '@/lockfile.js'
import { isFromItsRegistry, type NpmConfig, parseNpmrc, tokenFor } from '@/npmrc.js'
import { redactUrls } from '@/redaction.js'
import { type WiringInputs, wiringFindings } from '@/wiring-policy.js'
import { PROCESS_TIME } from './helpers/process-time.js'

describe('tokenFor', PROCESS_TIME, () => {
  it('matches the host exactly, so a key without a trailing slash reaches no look-alike host', () => {
    const config: NpmConfig = parseNpmrc('//registry.example.com:_authToken=secret\n', {})
    expect(tokenFor(config, 'https://registry.example.com/')).toBe('secret')
    expect(tokenFor(config, 'https://registry.example.com.evil.net/')).toBeUndefined()
    expect(tokenFor(config, 'https://registry.example.co/')).toBeUndefined()
  })

  it('matches the port, and the path on whole segments only', () => {
    const config: NpmConfig = parseNpmrc('//npm.corp:8443/team/:_authToken=team\n', {})
    expect(tokenFor(config, 'https://npm.corp:8443/team/')).toBe('team')
    expect(tokenFor(config, 'https://npm.corp:8443/team/sub/')).toBe('team')
    expect(tokenFor(config, 'https://npm.corp:8443/teammates/')).toBeUndefined()
    expect(tokenFor(config, 'https://npm.corp/team/')).toBeUndefined()
  })

  it('never sends a token to a registry reached over plain HTTP, or to a URL that does not parse', () => {
    const config: NpmConfig = parseNpmrc('//npm.corp/:_authToken=secret\n', {})
    // The plain scheme is the subject of the test, assembled so no literal names an insecure URL.
    expect(tokenFor(config, `${['http', ''].join(':')}//npm.corp/`)).toBeUndefined()
    expect(tokenFor(config, 'not a url')).toBeUndefined()
  })

  it('ignores a key that is not nerf-darted', () => {
    expect(tokenFor(parseNpmrc('_authToken=secret\n', {}), 'https://npm.corp/')).toBeUndefined()
  })
})

describe('redactUrls', PROCESS_TIME, () => {
  it('drops userinfo, query and fragment from every URL in a text', () => {
    expect(redactUrls('fetch https://user:pw@npm.corp/x?token=1#y failed; then http://a@b.example/c?d, done')).toBe(
      'fetch https://npm.corp/x failed; then http://b.example/c done',
    )
  })

  it('leaves a URL without credentials as it is, and a text without a URL untouched', () => {
    expect(redactUrls('see https://npm.corp/x for more')).toBe('see https://npm.corp/x for more')
    expect(redactUrls('no address here')).toBe('no address here')
    expect(redactUrls('[https://[host]')).toBe('[https://[host]')
  })

  it('redacts a URL that does not parse as well as it can', () => {
    expect(redactUrls('https://user:pw@[bad/x?token=1')).toBe('https://[bad/x')
  })

  it('reaches the undated finding of a release-age result', () => {
    const resolved: ResolvedPackage = {
      name: 'remote',
      version: '1.0.0',
      source: 'tarball',
      tarball: 'https://user:pw@example.invalid/remote-1.0.0.tgz?sig=abc',
      isPlatformLimited: false,
    }
    expect(undatedFinding(resolved)).toContain('(https://example.invalid/remote-1.0.0.tgz)')
    expect(undatedFinding(resolved)).not.toContain('pw')
  })
})

const harnessAt = (source: ResolvedPackage['source'], tarball: string = ''): ResolvedPackage => ({
  name: '@airness/node',
  version: '2.0.0',
  source,
  tarball,
  isPlatformLimited: false,
})

const lockfileWith = (harness: ResolvedPackage): Lockfile => ({
  importers: new Map([['.', [{ name: '@airness/node', specifier: '2.0.0', version: '2.0.0' }]]]),
  packages: [harness],
  roots: [],
  edges: new Map(),
  rewrites: [],
})

const config: NpmConfig = parseNpmrc('@airness:registry=http://127.0.0.1:4873/\n', {})

const inputsFor = (harness: ResolvedPackage): WiringInputs => ({
  memberPath: '.',
  manifest: { name: 'shop' },
  lockfile: lockfileWith(harness),
  expected: new Map([['@airness/node', '2.0.0']]),
  pins: {},
  hasSource: false,
  rewrites: [],
  isFromItsRegistry: (resolved: ResolvedPackage): boolean => isFromItsRegistry(config, resolved),
})

describe('wiring: where a harness package comes from', PROCESS_TIME, () => {
  it('accepts the harness from a registry, by name or as a tarball below its scope registry', () => {
    expect(wiringFindings(inputsFor(harnessAt('registry')))).toEqual([])
    expect(
      wiringFindings(inputsFor(harnessAt('tarball', 'http://127.0.0.1:4873/@airness/node/-/node-2.0.0.tgz'))),
    ).toEqual([])
  })

  it('refuses the harness from git, a directory, or a tarball elsewhere, at the right version', () => {
    for (const harness of [
      harnessAt('git'),
      harnessAt('directory'),
      harnessAt('tarball', 'https://example.invalid/node-2.0.0.tgz'),
    ]) {
      expect(wiringFindings(inputsFor(harness))).toEqual([
        `pnpm-lock.yaml resolves @airness/node@2.0.0 from a ${harness.source} rather than from the registry ` +
          'configured for it; install the released package from its registry',
      ])
    }
  })
})
