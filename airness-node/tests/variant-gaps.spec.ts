// Variants of the shared modules that the gate specs do not reach on their own.
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { biomeIncludes, biomeSelection } from '@/biome-selection.js'
import { biome } from '@/gate-biome.js'
import { configReferences } from '@/gate-config-references.js'
import type { GateResult } from '@/gate-result.js'
import { layerDefaultsOf } from '@/layer.js'
import { matchesAnyGlob } from '@/source-selection.js'
import { workingTreeFiles, workingTreeFingerprint } from '@/working-tree.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

describe('the Biome selection', PROCESS_TIME, () => {
  it('reads a member-wide source root and turns a generated directory into a directory exclusion', () => {
    const includes: readonly string[] = biomeIncludes(['.'], ['src/generated/**', 'src/client.ts'])
    expect(includes).toEqual(expect.arrayContaining(['**/*', '!src/generated', '!src/client.ts']))
    expect(
      biomeSelection(['src/total.ts', 'src/generated/api.ts', 'src/client.ts', 'apps/web/page.ts'], includes, [
        'apps/web',
      ]),
    ).toEqual(['src/total.ts'])
  })

  it('runs the Biome the shipped configuration was written for', () => {
    const root: string = temporaryProject({ 'notes.md': 'notes\n' })
    try {
      const result: GateResult = biome(memberAt(root))
      expect(result.findings.join('\n')).not.toContain('the Airness Biome configuration is written for Biome')
      expect(result.ok).toBe(true)
    } finally {
      removeProject(root)
    }
  })
})

describe('a layer block', PROCESS_TIME, () => {
  it('contributes nothing from fields of the wrong shape', () => {
    expect(
      layerDefaultsOf({
        airness: {
          layer: {
            generatedArtefacts: 'src/types.ts',
            javascriptAllowlist: 'all',
            productionEntries: 'src/app.config.ts',
            knipConfigPlugins: { app: false },
          },
        },
      }),
    ).toEqual({
      generatedArtefacts: [],
      frameworkGlue: [],
      javascriptRuntimes: [],
      pureLogicRoots: [],
      accessibilitySkipRoutes: [],
      productionEntries: [],
      knipConfigPlugins: [],
    })
  })
})

describe('glob roles', PROCESS_TIME, () => {
  it('matches a path against any of several globs', () => {
    expect(matchesAnyGlob('src/generated/api.ts', ['dist/**', 'src/generated/**'])).toBe(true)
    expect(matchesAnyGlob('src/total.ts', ['dist/**'])).toBe(false)
  })
})

describe('the working-tree fingerprint', PROCESS_TIME, () => {
  it('records an untracked nested repository as a directory rather than reading into it', () => {
    const root: string = temporaryProject({ 'source.ts': 'export const value = 1\n' })
    try {
      const before: string = workingTreeFingerprint(root)
      for (const directory of ['nested/.git/objects', 'nested/.git/refs']) {
        mkdirSync(path.join(root, directory), { recursive: true })
      }
      writeFileSync(path.join(root, 'nested/.git/HEAD'), 'ref: refs/heads/main\n')
      writeFileSync(path.join(root, 'nested/inner.ts'), 'export const inner = 1\n')
      expect(workingTreeFiles(root)).toContain('nested/')
      expect(workingTreeFingerprint(root)).not.toBe(before)
    } finally {
      removeProject(root)
    }
  })
})

describe('a coverage exclusion', PROCESS_TIME, () => {
  it('must reach a file the floor would otherwise measure, and must say why', () => {
    const manifest: Readonly<Record<string, unknown>> = {
      airness: {
        coverageExclude: [
          { pattern: 'src/entry.ts', reason: 'the entry point runs as a process of its own' },
          { pattern: 'src/gone.ts', reason: 'a file that was deleted' },
          { pattern: 'src/bare.ts' },
        ],
      },
    }
    const root: string = temporaryProject({
      'src/entry.ts': 'export const entry = 1\n',
      'src/total.ts': 'export const total = 1\n',
    })
    try {
      const findings: string = configReferences(memberAt(root, manifest)).findings.join('\n')
      expect(findings).toContain('coverageExclude entry "src/gone.ts" excludes nothing')
      expect(findings).toContain('coverageExclude entry "src/bare.ts" states no reason')
      expect(findings).not.toContain('"src/entry.ts"')
    } finally {
      removeProject(root)
    }
  })
})

describe('a blanket coverage exclusion', PROCESS_TIME, () => {
  it('is refused, because it switches the floor off rather than naming a role', () => {
    const manifest: Readonly<Record<string, unknown>> = {
      airness: { coverageExclude: [{ pattern: 'src/**', reason: 'every file' }] },
    }
    const root: string = temporaryProject({ 'src/total.ts': 'export const total = 1\n' })
    try {
      expect(configReferences(memberAt(root, manifest)).findings).toContain(
        'coverageExclude entry "src/**" excludes every file the floor would measure; name the files of one role instead',
      )
    } finally {
      removeProject(root)
    }
  })
  it('is refused when two entries switch it off between them', () => {
    const manifest: Readonly<Record<string, unknown>> = {
      airness: {
        coverageExclude: [
          { pattern: 'src/**/*.ts', reason: 'the logic' },
          { pattern: 'src/**/*.tsx', reason: 'the components' },
        ],
      },
    }
    const root: string = temporaryProject({
      'src/total.ts': 'export const total = 1\n',
      'src/view.tsx': 'export const view = 1\n',
    })
    try {
      expect(configReferences(memberAt(root, manifest)).findings.join('\n')).toContain(
        'coverageExclude entries "src/**/*.ts", "src/**/*.tsx" together exclude every file the floor would measure',
      )
    } finally {
      removeProject(root)
    }
  })
})
