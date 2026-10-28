import { describe, expect, it } from 'vitest'
import { rewriteFindings } from '@/install-config.js'
import type { Lockfile } from '@/lockfile.js'
import { SECURITY_OVERRIDES, securityOverrideFindings, securityOverrideLockFindings } from '@/security-overrides.js'

const SELECTOR: string = 'eslint-plugin-functional@10.0.1>deepmerge-ts'
const DECLARED: Readonly<Record<string, string>> = { [SELECTOR]: '8.0.2' }

const locked = (overrides: unknown, edge: string = 'deepmerge-ts@8.0.2'): Lockfile => ({
  importers: new Map(),
  packages: [],
  roots: [],
  edges: new Map([['eslint-plugin-functional@10.0.1(eslint@10.13.0)', [edge]]]),
  rewrites: [],
  overrides,
})

describe('approved security replacements', () => {
  it('permits only the exact approved edge and verifies the resolved graph', () => {
    expect(securityOverrideFindings(DECLARED, SECURITY_OVERRIDES, 'workspace')).toEqual([])
    expect(securityOverrideLockFindings(DECLARED, locked(DECLARED), SECURITY_OVERRIDES)).toEqual([])
    expect(
      rewriteFindings(
        { workspace: { overrides: DECLARED }, manifest: {}, npmrc: new Map(), files: [] },
        SECURITY_OVERRIDES,
      ),
    ).toEqual([])
  })

  it('rejects a broader selector, a version range, another version and a package alias', () => {
    for (const overrides of [
      { 'deepmerge-ts': '8.0.2' },
      { [SELECTOR]: '^8.0.2' },
      { [SELECTOR]: '8.0.1' },
      { [SELECTOR]: 'npm:empty-npm-package@1.0.0' },
    ]) {
      expect(securityOverrideFindings(overrides, SECURITY_OVERRIDES, 'workspace')).toHaveLength(1)
    }
  })

  it('refuses malformed override containers in either declaration or lockfile', () => {
    expect(securityOverrideFindings([], SECURITY_OVERRIDES, 'workspace')).toHaveLength(1)
    expect(securityOverrideLockFindings(undefined, locked([]), SECURITY_OVERRIDES)).toHaveLength(1)
    expect(securityOverrideFindings(undefined, SECURITY_OVERRIDES, 'workspace')).toEqual([])
    expect(securityOverrideLockFindings(undefined, undefined, SECURITY_OVERRIDES)).toEqual([])
  })

  it('refuses stale lock metadata and an edge that still resolves the affected release', () => {
    expect(securityOverrideLockFindings(DECLARED, locked({}), SECURITY_OVERRIDES).join(',')).toContain('differ')
    expect(securityOverrideLockFindings({}, locked(DECLARED), SECURITY_OVERRIDES).join(',')).toContain('differ')
    expect(
      securityOverrideLockFindings(DECLARED, locked(DECLARED, 'deepmerge-ts@7.1.6'), SECURITY_OVERRIDES).join(','),
    ).toContain('does not resolve the approved')
    expect(
      securityOverrideLockFindings(DECLARED, locked(DECLARED, 'unrelated@8.0.2'), SECURITY_OVERRIDES).join(','),
    ).toContain('does not resolve the approved')
  })

  it('refuses an unused approval and an unapproved rewrite recorded only in the lockfile', () => {
    const stale: Lockfile = { ...locked(DECLARED), edges: new Map([['unrelated@1.0.0', []]]) }
    expect(securityOverrideLockFindings(DECLARED, stale, SECURITY_OVERRIDES).join(',')).toContain(
      'reaches no resolved parent',
    )
    expect(securityOverrideLockFindings({}, locked({ knip: 'file:fake' }), SECURITY_OVERRIDES).join(',')).toContain(
      'approves no such security replacement',
    )
  })

  it('does not authorize patches, hooks or manifest-level rewrites beside an approved replacement', () => {
    const findings: readonly string[] = rewriteFindings(
      {
        workspace: { overrides: DECLARED, patchedDependencies: {} },
        manifest: { overrides: DECLARED, pnpm: { overrides: DECLARED } },
        npmrc: new Map([['pnpmfile', 'hook.cjs']]),
        files: ['.pnpmfile.cjs'],
      },
      SECURITY_OVERRIDES,
    )
    expect(findings).toHaveLength(5)
    expect(findings.join(',')).toContain('patchedDependencies')
  })
})
