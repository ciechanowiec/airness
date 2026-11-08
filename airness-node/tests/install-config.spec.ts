import { describe, expect, it } from 'vitest'
import { type InstallConfigInputs, layoutFindings, rewriteFindings } from '@/install-config.js'

const NONE: InstallConfigInputs = { workspace: undefined, manifest: {}, npmrc: new Map(), files: [] }

describe('the install configuration', () => {
  it('leaves the store where it belongs by default and with the allowed values', () => {
    expect(layoutFindings(NONE)).toEqual([])
    expect(
      layoutFindings({
        ...NONE,
        workspace: { nodeLinker: 'isolated', enableGlobalVirtualStore: false },
        npmrc: new Map([['enable-global-virtual-store', 'false']]),
      }),
    ).toEqual([])
  })

  it('refuses every setting that moves or replaces the store, in either file', () => {
    expect(
      layoutFindings({
        ...NONE,
        workspace: { nodeLinker: 'hoisted', virtualStoreDir: '.store', modulesDir: 1, enableGlobalVirtualStore: true },
        npmrc: new Map([['virtual-store-dir', '.store']]),
      }),
    ).toEqual([
      'pnpm-workspace.yaml nodeLinker: moves the store the gates read; remove it',
      'pnpm-workspace.yaml virtualStoreDir: moves the store the gates read; remove it',
      '.npmrc virtual-store-dir: moves the store the gates read; remove it',
      'pnpm-workspace.yaml modulesDir: moves the store the gates read; remove it',
      'pnpm-workspace.yaml enableGlobalVirtualStore: moves the store the gates read; remove it',
    ])
  })

  it('names no rewrite in a project that configures none', () => {
    expect(rewriteFindings({ ...NONE, files: ['package.json', 'pnpm-lock.yaml'] })).toEqual([])
  })

  it('refuses config dependencies, which pnpm installs outside the resolved set', () => {
    expect(rewriteFindings({ ...NONE, workspace: { configDependencies: { 'is-number': '7.0.0' } } })).toEqual([
      'pnpm-workspace.yaml configDependencies: pnpm installs these packages outside the resolved set every ' +
        'supply-chain gate judges, and may load pnpmfile hooks from them; remove it',
    ])
  })
})
