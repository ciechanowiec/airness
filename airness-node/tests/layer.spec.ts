import { mkdirSync, symlinkSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  type LayerPackage,
  layerBrowserConfigOf,
  layerDefaultsFor,
  layerDefaultsOf,
  layerEslintConfigOf,
  layerPackagesFor,
  layerProjectsFor,
  layerSpecificationsOf,
  layerTestDependenciesFor,
} from '@/layer.js'
import { readJson } from '@/project.js'
import { readSettings, type Settings } from '@/settings.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

const LAYER_MANIFEST: Readonly<Record<string, unknown>> = {
  name: '@airness/payload',
  airness: {
    layer: {
      generatedArtefacts: ['src/payload-types.ts'],
      frameworkGlue: ['src/app/(payload)/**'],
      vitestInlineDependencies: ['@payloadcms/next', '@payloadcms/ui', 'react-image-crop'],
      javascriptAllowlist: [
        { pattern: String.raw`importMap\.js$`, runtime: 'node' },
        { pattern: String.raw`^nothing\.js$`, runtime: 'deno' },
      ],
    },
  },
}

describe('the defaults a layer package contributes', PROCESS_TIME, () => {
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
      for (const name of ['@airness/payload', 'impostor']) {
        mkdirSync(path.join(root, 'node_modules', name), { recursive: true })
        writeFileSync(
          path.join(root, 'node_modules', name, 'package.json'),
          JSON.stringify({ ...LAYER_MANIFEST, name }),
        )
      }
      const declared: Readonly<Record<string, unknown>> = {
        devDependencies: { '@airness/payload': '1.0.0', impostor: '1.0.0' },
      }
      expect(layerDefaultsFor(root, declared, readJson).map((layer) => layer.generatedArtefacts)).toEqual([
        ['src/payload-types.ts'],
      ])
      expect(layerDefaultsFor(root, { devDependencies: { impostor: '1.0.0' } }, readJson)).toEqual([])
      expect(layerTestDependenciesFor(root, declared, readJson)).toEqual([
        '@payloadcms/next',
        '@payloadcms/ui',
        'react-image-crop',
      ])
      expect(
        layerTestDependenciesFor(
          root,
          {
            devDependencies: { impostor: '1.0.0' },
            airness: { vitestInlineDependencies: ['untrusted'] },
          },
          readJson,
        ),
      ).toEqual([])
      writeFileSync(
        path.join(root, 'node_modules/@airness/payload/package.json'),
        JSON.stringify({
          name: '@airness/payload',
          airness: { layer: { vitestInlineDependencies: 'not-an-array' } },
        }),
      )
      expect(() => layerTestDependenciesFor(root, declared, readJson)).toThrow('array of dependency names')
      writeFileSync(
        path.join(root, 'node_modules/@airness/payload/package.json'),
        JSON.stringify({ name: '@airness/payload' }),
      )
      expect(layerTestDependenciesFor(root, declared, readJson)).toEqual([])
    } finally {
      removeProject(root)
    }
  })
})

const layerAt = (directory: string, layer: Readonly<Record<string, unknown>>): LayerPackage => ({
  name: '@airness/payload',
  directory,
  manifest: { name: '@airness/payload', airness: { layer } },
})

describe('the seams a layer package joins the node gates through', PROCESS_TIME, () => {
  it('resolves a browser adapter only through the installed layer package exports', () => {
    const root: string = temporaryProject({
      'node_modules/@airness/payload/package.json': JSON.stringify({
        name: '@airness/payload',
        exports: { './browser': './browser.js' },
      }),
      'node_modules/@airness/payload/browser.js': 'export const scanRootSelectors = [".owned-view"]\n',
    })
    try {
      const layer: LayerPackage = layerAt(path.join(root, 'node_modules/@airness/payload'), { browser: './browser' })
      expect(layerBrowserConfigOf(layer, root)).toBe(path.join(root, 'node_modules/@airness/payload/browser.js'))
      expect(layerBrowserConfigOf(layerAt(layer.directory, {}), root)).toBeUndefined()
      expect(() => layerBrowserConfigOf(layerAt(layer.directory, { browser: '../project.js' }), root)).toThrow(
        'package export',
      )
      expect(() => layerBrowserConfigOf(layerAt(layer.directory, { browser: './missing' }), root)).toThrow()
    } finally {
      removeProject(root)
    }
  })
  it('runs the specifications a layer ships from a directory inside the package, and from nowhere else', () => {
    expect(layerSpecificationsOf(layerAt('/store/@airness/payload', { e2eSpecs: 'dist' }))).toEqual({
      name: '@airness/payload',
      directory: path.join('/store/@airness/payload', 'dist'),
    })
    expect(layerSpecificationsOf(layerAt('/store/@airness/payload', {}))).toBeUndefined()
    for (const e2eSpecs of ['../../project/tests', '/project/tests', ['dist'], '']) {
      expect(() => layerSpecificationsOf(layerAt('/store/@airness/payload', { e2eSpecs }))).toThrow(
        'which is not a directory inside the package',
      )
    }
  })

  it('resolves the ESLint configuration a declared layer exports from the member, and nothing it does not export', () => {
    const root: string = temporaryProject({})
    try {
      const layerDirectory: string = path.join(root, 'node_modules', '@airness/payload')
      mkdirSync(path.join(layerDirectory, 'dist'), { recursive: true })
      writeFileSync(path.join(layerDirectory, 'dist', 'eslint.js'), 'export default []\n')
      writeFileSync(
        path.join(layerDirectory, 'package.json'),
        JSON.stringify({
          name: '@airness/payload',
          type: 'module',
          exports: {
            './eslint': { types: './dist/eslint.d.ts', default: './dist/eslint.js' },
            './package.json': './package.json',
          },
          airness: { layer: { eslint: './eslint', e2eSpecs: 'dist' } },
        }),
      )
      const [layer]: readonly LayerPackage[] = layerPackagesFor(
        root,
        { devDependencies: { '@airness/payload': '1.0.0' } },
        readJson,
      )
      expect(layer?.directory).toBe(layerDirectory)
      expect(layer === undefined ? undefined : layerEslintConfigOf(layer, root)).toBe(
        path.join(layerDirectory, 'dist', 'eslint.js'),
      )
      expect(layerEslintConfigOf(layerAt(layerDirectory, {}), root)).toBeUndefined()
      for (const eslint of ['eslint', '../other/eslint', './../eslint', 'other/eslint', ['./eslint']]) {
        expect(() => layerEslintConfigOf(layerAt(layerDirectory, { eslint }), root)).toThrow(
          "which is not a package export such as './eslint'",
        )
      }
      expect(() => layerEslintConfigOf(layerAt(layerDirectory, { eslint: './absent' }), root)).toThrow()
      expect(layerPackagesFor(root, { devDependencies: { impostor: '1.0.0' } }, readJson)).toEqual([])
    } finally {
      removeProject(root)
    }
  })

  it('never reads the member itself as its own layer, by name or by where it resolves', () => {
    const root: string = temporaryProject({})
    try {
      const posing: Readonly<Record<string, unknown>> = {
        name: '@airness/payload',
        exports: { './package.json': './package.json' },
        devDependencies: { '@airness/payload': 'link:.' },
        airness: { layer: { frameworkGlue: ['src/**'], eslint: './eslint' } },
      }
      writeFileSync(path.join(root, 'package.json'), JSON.stringify(posing))
      expect(layerPackagesFor(root, posing, readJson)).toEqual([])
      mkdirSync(path.join(root, 'node_modules', '@airness'), { recursive: true })
      symlinkSync(root, path.join(root, 'node_modules', '@airness/payload'))
      expect(layerPackagesFor(root, { ...posing, name: 'shop' }, readJson)).toEqual([])
      expect(layerDefaultsFor(root, { ...posing, name: 'shop' }, readJson)).toEqual([])
    } finally {
      removeProject(root)
    }
  })

  it('makes a Playwright project of the specifications a layer ships, and refuses a directory with none', () => {
    const root: string = temporaryProject({})
    try {
      const layerDirectory: string = path.join(root, 'node_modules', '@airness/payload')
      mkdirSync(path.join(layerDirectory, 'dist'), { recursive: true })
      const writeLayer = (layer: Readonly<Record<string, unknown>>): void => {
        writeFileSync(
          path.join(layerDirectory, 'package.json'),
          JSON.stringify({ name: '@airness/payload', airness: { layer } }),
        )
      }
      const declared: Readonly<Record<string, unknown>> = { devDependencies: { '@airness/payload': '1.0.0' } }
      const use: Readonly<Record<string, unknown>> = { channel: 'chromium' }
      writeLayer({})
      expect(layerProjectsFor(root, declared, readJson, use)).toEqual([])
      writeLayer({ e2eSpecs: 'dist' })
      expect(() => layerProjectsFor(root, declared, readJson, use)).toThrow('holds no *.e2e.spec.js file')
      writeLayer({ e2eSpecs: 'build' })
      expect(() => layerProjectsFor(root, declared, readJson, use)).toThrow('holds no *.e2e.spec.js file')
      writeFileSync(path.join(layerDirectory, 'dist', 'access-boundary.e2e.spec.js'), 'export {}\n')
      writeLayer({ e2eSpecs: 'dist' })
      expect(layerProjectsFor(root, declared, readJson, use)).toEqual([
        { name: '@airness/payload', testDir: path.join(layerDirectory, 'dist'), testMatch: /\.e2e\.spec\.js$/u, use },
      ])
      expect(layerProjectsFor(root, {}, readJson, use)).toEqual([])
    } finally {
      removeProject(root)
    }
  })
})
