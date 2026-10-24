import { describe, expect, it } from 'vitest'
import { installScriptFindings } from '@/install-policy.js'

const ESBUILD: { readonly name: string; readonly version: string } = { name: 'esbuild', version: '0.25.0' }

describe('install scripts', () => {
  it('passes a record that decides exactly the packages with an install script', () => {
    expect(
      installScriptFindings({
        workspace: { allowBuilds: { esbuild: true } },
        manifest: {},
        scripted: [ESBUILD],
        elsewhere: [],
      }),
    ).toEqual([])
    expect(
      installScriptFindings({
        workspace: { allowBuilds: { 'esbuild@0.25.0': false } },
        manifest: {},
        scripted: [ESBUILD],
        elsewhere: [],
      }),
    ).toEqual([])
    expect(
      installScriptFindings({ workspace: { allowBuilds: {} }, manifest: {}, scripted: [], elsewhere: [] }),
    ).toEqual([])
  })

  it('fails a project with no record', () => {
    expect(
      installScriptFindings({ workspace: undefined, manifest: {}, scripted: [], elsewhere: [] }).join('\n'),
    ).toContain('pnpm-workspace.yaml declares no allowBuilds mapping')
  })

  it('fails an undecided package, a decision about nothing, and a decision that is not a boolean', () => {
    expect(
      installScriptFindings({
        workspace: { allowBuilds: { sharp: true, esbuild: 'yes' } },
        manifest: {},
        scripted: [ESBUILD, { name: 'protobufjs', version: '7.0.0' }],
        elsewhere: [],
      }),
    ).toEqual([
      'allowBuilds.esbuild must be true or false',
      'protobufjs@7.0.0 runs an install script that allowBuilds does not decide; add "protobufjs: true" to run ' +
        'it, or "protobufjs: false" to install it without running it',
      'allowBuilds.sharp names no installed dependency that runs an install script; remove it',
    ])
  })

  it('refuses every other setting that decides install scripts', () => {
    expect(
      installScriptFindings({
        workspace: { allowBuilds: {}, dangerouslyAllowAllBuilds: true },
        manifest: { pnpm: { onlyBuiltDependencies: ['esbuild'] } },
        scripted: [],
        elsewhere: [],
      }),
    ).toEqual([
      'pnpm-workspace.yaml dangerouslyAllowAllBuilds: decides install scripts beside allowBuilds; remove it, ' +
        'allowBuilds is the one record',
      'package.json pnpm.onlyBuiltDependencies: install-script decisions belong in pnpm-workspace.yaml ' +
        'allowBuilds; remove it',
    ])
  })

  it('keeps a decision about a package this host leaves out, so the record passes on every system', () => {
    const elsewhere: { readonly name: string; readonly version: string } = {
      name: '@esbuild/linux-x64',
      version: '0.25.0',
    }
    expect(
      installScriptFindings({
        workspace: { allowBuilds: { '@esbuild/linux-x64': true } },
        manifest: {},
        scripted: [],
        elsewhere: [elsewhere],
      }),
    ).toEqual([])
  })
})
