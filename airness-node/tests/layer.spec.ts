import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { layerDefaultsFor, layerDefaultsOf } from '@/layer.js'
import { readJson } from '@/project.js'
import { readSettings, type Settings } from '@/settings.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

const LAYER_MANIFEST: Readonly<Record<string, unknown>> = {
  name: 'ploaness',
  airness: {
    layer: {
      generatedArtefacts: ['src/payload-types.ts'],
      frameworkGlue: ['src/app/(payload)/**'],
      javascriptAllowlist: [
        { pattern: String.raw`importMap\.js$`, runtime: 'node' },
        { pattern: String.raw`^nothing\.js$`, runtime: 'deno' },
      ],
    },
  },
}

describe('the defaults a layer package contributes', () => {
  it('applies a layer default to the settings without making it the project exclusion', () => {
    const settings: Settings = readSettings({}, [layerDefaultsOf(LAYER_MANIFEST)])
    expect(settings.generatedArtefacts).toEqual(['src/payload-types.ts'])
    expect(settings.frameworkGlue).toEqual(['src/app/(payload)/**'])
    expect(settings.javascriptAllowlist).toContain(String.raw`importMap\.js$`)
    expect(settings.javascriptAllowlist).not.toContain(String.raw`^nothing\.js$`)
    expect(settings.declaredExclusions).toEqual([])
  })

  it('reads the defaults of a declared layer package from the copy the member resolves, and of no other package', () => {
    const root: string = temporaryProject({})
    try {
      for (const name of ['ploaness', 'impostor']) {
        mkdirSync(path.join(root, 'node_modules', name), { recursive: true })
        writeFileSync(
          path.join(root, 'node_modules', name, 'package.json'),
          JSON.stringify({ ...LAYER_MANIFEST, name }),
        )
      }
      const declared: Readonly<Record<string, unknown>> = { devDependencies: { ploaness: '1.0.0', impostor: '1.0.0' } }
      expect(layerDefaultsFor(root, declared, readJson).map((layer) => layer.generatedArtefacts)).toEqual([
        ['src/payload-types.ts'],
      ])
      expect(layerDefaultsFor(root, { devDependencies: { impostor: '1.0.0' } }, readJson)).toEqual([])
    } finally {
      removeProject(root)
    }
  })
})
