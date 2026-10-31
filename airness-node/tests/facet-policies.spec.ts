// The pure halves of the facets: the bundle budget, the architecture layers, the SBOM, the Biome domains and
// the not-applicable verdict.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { Ajv, type ValidateFunction } from 'ajv'
import { describe, expect, it } from 'vitest'
import { APPLICATION_LAYER_RULE, layerRules, pureLogicRule } from '@/architecture-layers.js'
import { BUILD_DIRECTORY_MARKER, evaluateBundle, parseBuildDirectory } from '@/bundle-budget.js'
import { notApplicable, passed } from '@/exec.js'
import { scriptSourcesOf } from '@/facets.js'
import { nextBiomeConfig } from '@/gate-biome.js'
import { resultOf } from '@/gate-result.js'
import { asRecord } from '@/json-shapes.js'
import type { Lockfile } from '@/lockfile.js'
import type { ResolvedSet } from '@/resolved-set.js'
import { npmPackageUrl, type SbomSubject, sbomOf } from '@/sbom.js'
import { readSettings } from '@/settings.js'
import { PROCESS_TIME } from './helpers/process-time.js'

describe('evaluateBundle', PROCESS_TIME, () => {
  it('sums the gzipped sizes against the budget', () => {
    expect(
      evaluateBundle(
        [
          { path: 'a.js', gzipBytes: 6 },
          { path: 'b.js', gzipBytes: 5 },
        ],
        10,
      ),
    ).toEqual({
      totalGzipBytes: 11,
      budgetBytes: 10,
      isWithinBudget: false,
      fileCount: 2,
    })
  })
})

describe('parseBuildDirectory', PROCESS_TIME, () => {
  it('reads the one reported directory among other output', () => {
    expect(parseBuildDirectory(`loading\n${BUILD_DIRECTORY_MARKER}"/app/.next"\n`)).toBe('/app/.next')
  })

  it('selects nothing from missing, repeated or malformed evidence', () => {
    expect(parseBuildDirectory('nothing')).toBeUndefined()
    expect(parseBuildDirectory(`${BUILD_DIRECTORY_MARKER}"/a"\n${BUILD_DIRECTORY_MARKER}"/b"`)).toBeUndefined()
    expect(parseBuildDirectory(`${BUILD_DIRECTORY_MARKER}{`)).toBeUndefined()
    expect(parseBuildDirectory(`${BUILD_DIRECTORY_MARKER}"  "`)).toBeUndefined()
  })
})

describe('architecture layers', PROCESS_TIME, () => {
  it('adds the application layer only to a Next.js application', () => {
    expect(layerRules(true, [], ['src'])).toEqual([APPLICATION_LAYER_RULE])
    expect(layerRules(false, [], ['src'])).toEqual([])
  })

  it('escapes a declared floor into the pure-logic rule', () => {
    expect(pureLogicRule(['src/(core)', 'src/lib'], ['src', 'tests'])).toMatchObject({
      name: 'pure-logic-stays-pure',
      from: { path: String.raw`^(src/\(core\)/|src/lib/)` },
      to: { path: '^(src/|tests/)', pathNot: String.raw`^(src/\(core\)/|src/lib/)` },
    })
    expect(pureLogicRule([])).toBeUndefined()
  })
})

describe('nextBiomeConfig', PROCESS_TIME, () => {
  it('adds the React and Next.js domains and keeps the shipped ones', () => {
    expect(nextBiomeConfig('{"linter":{"domains":{"project":"all"}}}')).toEqual({
      linter: { domains: { project: 'all', next: 'all', react: 'all' } },
    })
  })
})

describe('resultOf', PROCESS_TIME, () => {
  it('reads as not applicable only when no member had anything to judge', () => {
    expect(resultOf('css', 'style sheets', [{ member: '.', result: notApplicable('none') }]).status).toBe(
      'not-applicable',
    )
    expect(
      resultOf('css', 'style sheets', [
        { member: '.', result: notApplicable('none') },
        { member: 'web', result: passed('fine') },
      ]).status,
    ).toBe('passed')
  })
})

const LOCKFILE: Lockfile = {
  importers: new Map([['.', [{ name: '@scope/tool', specifier: '1.0.0', version: '1.0.0' }]]]),
  packages: [],
  roots: ['@scope/tool@1.0.0'],
  edges: new Map([
    ['@scope/tool@1.0.0', ['left-pad@1.3.0']],
    ['left-pad@1.3.0', []],
  ]),
  rewrites: [],
}

const SET: ResolvedSet = {
  lockfile: LOCKFILE,
  installed: [
    { name: '@scope/tool', version: '1.0.0', manifest: { license: 'MIT OR Apache-2.0' }, directory: '/x' },
    { name: 'left-pad', version: '1.3.0', manifest: { license: 'SEE LICENSE IN LICENSE' }, directory: '/y' },
  ],
  elsewhere: [{ name: 'fsevents', version: '2.3.3', source: 'registry', tarball: '', isPlatformLimited: true }],
  missing: [],
}

describe('sbomOf', PROCESS_TIME, () => {
  const subject: SbomSubject = { name: 'web', version: '1.0.0', harnessVersion: '2.0.0' }

  it('writes a scoped package URL with its encoded namespace', () => {
    expect(npmPackageUrl('@scope/tool', '1.0.0')).toBe('pkg:npm/%40scope/tool@1.0.0')
    expect(npmPackageUrl('left-pad', '1.3.0')).toBe('pkg:npm/left-pad@1.3.0')
  })

  it('describes every resolved package, its license and the dependency graph', () => {
    const document: Readonly<Record<string, unknown>> = sbomOf(SET, subject)
    expect(document).toMatchObject({ bomFormat: 'CycloneDX', specVersion: '1.6', version: 1 })
    expect(document['components']).toEqual([
      expect.objectContaining({
        group: '@scope',
        name: 'tool',
        purl: 'pkg:npm/%40scope/tool@1.0.0',
        licenses: [{ expression: 'MIT OR Apache-2.0' }],
        scope: 'required',
      }),
      expect.objectContaining({ name: 'fsevents', licenses: [], scope: 'optional' }),
      expect.objectContaining({ name: 'left-pad', licenses: [{ license: { name: 'SEE LICENSE IN LICENSE' } }] }),
    ])
    expect(document['dependencies']).toEqual([
      { ref: 'pkg:npm/web@1.0.0', dependsOn: ['pkg:npm/%40scope/tool@1.0.0'] },
      { ref: 'pkg:npm/%40scope/tool@1.0.0', dependsOn: ['pkg:npm/left-pad@1.3.0'] },
      { ref: 'pkg:npm/left-pad@1.3.0', dependsOn: [] },
    ])
  })

  it('derives the serial number from the content, so an unchanged project writes the same document', () => {
    const first: unknown = sbomOf(SET, subject)['serialNumber']
    expect(first).toMatch(/^urn:uuid:[\da-f]{8}-[\da-f]{4}-5[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/u)
    expect(sbomOf(SET, subject)['serialNumber']).toBe(first)
    expect(sbomOf(SET, { ...subject, version: '1.0.1' })['serialNumber']).not.toBe(first)
  })
})

const SCHEMA_DIRECTORY: string = path.join(path.dirname(fileURLToPath(import.meta.url)), 'fixtures', 'cyclonedx')

const readSchema = (file: string): Record<string, unknown> =>
  asRecord(JSON.parse(readFileSync(path.join(SCHEMA_DIRECTORY, file), 'utf8')))

describe('sbomOf against the CycloneDX 1.6 schema', PROCESS_TIME, () => {
  it('writes a document the vendored schema accepts', () => {
    const ajv: Ajv = new Ajv({ strict: false, validateFormats: false })
    ajv.addSchema(readSchema('spdx.schema.json'), 'spdx.schema.json')
    ajv.addSchema(readSchema('jsf-0.82.schema.json'), 'jsf-0.82.schema.json')
    const validate: ValidateFunction = ajv.compile(readSchema('bom-1.6.schema.json'))
    const document: unknown = sbomOf(SET, { name: 'web', version: '', harnessVersion: '2.0.0' })
    expect(validate(document), JSON.stringify(validate.errors)).toBe(true)
  })

  it('names a versionless root without a dangling `@`, joins peer variants and leaves workspace links out', () => {
    const variants: ResolvedSet = {
      ...SET,
      lockfile: {
        ...LOCKFILE,
        roots: ['@scope/tool@1.0.0', 'member@link:member'],
        edges: new Map([
          ['@scope/tool@1.0.0(react@19.0.0)', ['left-pad@1.3.0']],
          ['@scope/tool@1.0.0(react@18.0.0)', ['fsevents@2.3.3', 'member@link:member']],
          ['left-pad@1.3.0', []],
        ]),
      },
    }
    const document: Readonly<Record<string, unknown>> = sbomOf(variants, {
      name: 'web',
      version: '',
      harnessVersion: '2',
    })
    expect(document['dependencies']).toEqual([
      { ref: 'pkg:npm/web', dependsOn: ['pkg:npm/%40scope/tool@1.0.0'] },
      { ref: 'pkg:npm/%40scope/tool@1.0.0', dependsOn: ['pkg:npm/fsevents@2.3.3', 'pkg:npm/left-pad@1.3.0'] },
      { ref: 'pkg:npm/left-pad@1.3.0', dependsOn: [] },
    ])
  })
})

describe('scriptSourcesOf', PROCESS_TIME, () => {
  it('counts no script in a stylesheet-only member that carries the pinned configurations', () => {
    expect(
      scriptSourcesOf(
        ['eslint.config.mjs', 'vitest.config.mts', 'package.json', 'src/main/resources/static/css/site.css'],
        readSettings({}),
        [],
      ),
    ).toEqual([])
  })

  it('counts every other script, whatever its directory, but not a generated or a nested member one', () => {
    expect(
      scriptSourcesOf(
        [
          'tests/e2e/home.e2e.spec.ts',
          'public/menu.js',
          'src/generated/api.ts',
          'ui/src/main.ts',
          'nested/eslint.config.mjs',
        ],
        readSettings({
          airness: { generatedArtefacts: [{ pattern: 'src/generated/**', reason: 'a client generator writes it' }] },
        }),
        ['ui'],
      ),
    ).toEqual(['tests/e2e/home.e2e.spec.ts', 'public/menu.js', 'nested/eslint.config.mjs'])
  })
})
