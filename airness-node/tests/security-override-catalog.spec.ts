import { describe, expect, it } from 'vitest'
import type { LayerPackage } from '@/layer.js'
import { securityOverrideCatalog } from '@/security-override-catalog.js'
import { SECURITY_OVERRIDES, type SecurityOverride } from '@/security-overrides.js'

const APPROVAL: SecurityOverride = {
  parent: 'payload@3.91.0',
  dependency: 'undici',
  version: '7.30.1',
  advisories: ['GHSA-2m5v-pch6-74hx', 'CVE-2026-12345'],
  reason: 'The patched HTTP client stays in the declared major.',
  addedOn: '2026-10-28',
}

const layer = (securityOverrides: unknown): LayerPackage => ({
  name: '@airness/payload',
  directory: '/installed/@airness/payload',
  manifest: { airness: { layer: { securityOverrides } } },
})

describe('the installed security-override catalogue', () => {
  it('combines generic approvals with the registered stack layer', () => {
    expect(securityOverrideCatalog([])).toEqual(SECURITY_OVERRIDES)
    expect(securityOverrideCatalog([layer(undefined)])).toEqual(SECURITY_OVERRIDES)
    expect(securityOverrideCatalog([layer([APPROVAL])])).toEqual([...SECURITY_OVERRIDES, APPROVAL])
  })

  it('refuses malformed layer policy rather than silently granting an exception', () => {
    expect(() => securityOverrideCatalog([layer({})])).toThrow('must be an array')
    for (const entry of [
      {},
      { ...APPROVAL, parent: 'payload@^3.91.0' },
      { ...APPROVAL, parent: 'payload@3.91.0(peer@1)' },
      { ...APPROVAL, dependency: '../elsewhere' },
      { ...APPROVAL, dependency: '@airness/node' },
      { ...APPROVAL, version: '^7.30.1' },
      { ...APPROVAL, advisories: [] },
      { ...APPROVAL, advisories: ['.*'] },
      { ...APPROVAL, advisories: false },
      { ...APPROVAL, reason: ' ' },
      { ...APPROVAL, addedOn: 'yesterday' },
    ]) {
      expect(() => securityOverrideCatalog([layer([entry])])).toThrow('invalid airness.layer.securityOverrides')
    }
  })

  it('does not let a layer replace the generic approval with another version', () => {
    expect(() => securityOverrideCatalog([layer([{ ...SECURITY_OVERRIDES[0], version: '7.1.6' }])])).toThrow(
      'disagree about the security replacement',
    )
  })
})
