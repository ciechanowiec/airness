// The pure halves of the facets: the bundle budget, the architecture layers, the SBOM, the Biome domains and
// the not-applicable verdict.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { Ajv, type ValidateFunction } from 'ajv'
import { describe, expect, it } from 'vitest'
import { type ArchitectureRule, layerRules, misplacedRouteDirectoryFindings } from '@/architecture-layers.js'
import { BUILD_DIRECTORY_MARKER, evaluateBundle, parseBuildDirectory } from '@/bundle-budget.js'
import { hasScriptSources } from '@/facets.js'
import { notApplicable, passed, resultOf } from '@/gate-result.js'
import { asRecord } from '@/json-shapes.js'
import type { Lockfile } from '@/lockfile.js'
import type { ResolvedSet } from '@/resolved-set.js'
import { type SbomSubject, sbomOf } from '@/sbom.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

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

const TEST_CODE_RULE: string = 'production-imports-no-test-code'
const DEVELOPMENT_DEPENDENCY_RULE: string = 'not-to-dev-dep'

const namesOf = (rules: readonly ArchitectureRule[]): readonly unknown[] =>
  rules.map((rule: ArchitectureRule): unknown => rule['name'])

// The rule of one name a member with these source roots is held to, if any.
const ruleNamed = (name: string, sourceRoots: readonly string[]): ArchitectureRule | undefined =>
  layerRules(false, [], sourceRoots).find((rule: ArchitectureRule): boolean => rule['name'] === name)

// Whether a member declared whole as a root exempts a file from the development-only dependency rule.
const isExemptFromDevelopmentDependencyRule = (file: string): boolean =>
  new RegExp(String(asRecord(ruleNamed(DEVELOPMENT_DEPENDENCY_RULE, ['.'])?.['from'])['pathNot'])).test(file)

describe('architecture layers', PROCESS_TIME, () => {
  it('adds the application layer only to a Next.js application', () => {
    const nextApp: readonly ArchitectureRule[] = layerRules(true, [], ['src'])
    expect(namesOf(nextApp)).toEqual(['nothing-imports-app', TEST_CODE_RULE, DEVELOPMENT_DEPENDENCY_RULE])
    expect(nextApp[0]).toMatchObject({ severity: 'error', from: { path: '^src/', pathNot: '^src/app/' } })
    expect(nextApp[0]).toMatchObject({ to: { path: '^src/app/' } })
    expect(nextApp.slice(1)).toEqual(layerRules(false, [], ['src']))
    expect(layerRules(false, [], ['tests', 'scripts'])).toEqual([])
  })

  it('splits the unresolvable-import rule only for a member that declares a CloudFront file', () => {
    expect(layerRules(false, [], ['tests'], [])).toEqual([])
    expect(layerRules(false, [], ['tests'], ['edge/function.cjs'])).toMatchObject([
      { name: 'not-to-unresolvable', from: { pathNot: String.raw`^(edge/function\.cjs)$` } },
      { name: 'not-to-unresolvable-cloudfront', from: { path: String.raw`^(edge/function\.cjs)$` } },
    ])
    expect(layerRules(false, [], ['tests'], ['edge/function.cjs'])[1]).toMatchObject({
      to: { couldNotResolve: true, pathNot: '^cloudfront$' },
    })
    expect(layerRules(false, [], ['src'], ['edge/function.cjs'])).toHaveLength(4)
  })

  it('refuses a Next.js routes directory at the member root, and only in a Next.js member', () => {
    expect(misplacedRouteDirectoryFindings(true, ['app/layout.tsx', 'pages/index.tsx', 'src/lib/a.ts'])).toEqual([
      expect.stringContaining('app/: move the Next.js routes directory under src/ (src/app/)'),
      expect.stringContaining('pages/: move the Next.js routes directory under src/ (src/pages/)'),
    ])
    expect(
      misplacedRouteDirectoryFindings(true, ['src/app/layout.tsx', 'src/pages/index.tsx', 'application/a.ts']),
    ).toEqual([])
    expect(misplacedRouteDirectoryFindings(false, ['app/layout.tsx', 'pages/index.tsx'])).toEqual([])
  })

  it('escapes a declared floor into the pure-logic rule', () => {
    expect(layerRules(false, ['src/(core)', 'src/lib'], ['src', 'tests'])[0]).toMatchObject({
      name: 'pure-logic-stays-pure',
      from: { path: String.raw`^(src/\(core\)/|src/lib/)` },
      to: { path: '^(src/|tests/)', pathNot: String.raw`^(src/\(core\)/|src/lib/)` },
    })
    expect(namesOf(layerRules(false, [], ['src', 'tests']))).not.toContain('pure-logic-stays-pure')
  })

  it('keeps every production root, the whole member included, from importing test code', () => {
    expect(ruleNamed(TEST_CODE_RULE, ['src', 'tests', 'scripts', 'lib'])).toMatchObject({
      from: { path: '^(src/|lib/)' },
      to: { path: '^tests/' },
    })
    expect(ruleNamed(TEST_CODE_RULE, ['src', '.'])).toMatchObject({ from: { path: '^(src/|)' } })
    expect(ruleNamed(TEST_CODE_RULE, ['tests'])).toBeUndefined()
  })

  // The rule replaces the shipped one of the same name, which reads `src` alone.
  it('keeps every production root, the whole member included, from importing a development-only dependency', () => {
    expect(ruleNamed(DEVELOPMENT_DEPENDENCY_RULE, ['src', 'tests', 'scripts', 'lib'])).toMatchObject({
      from: { path: '^(src/|lib/)' },
      to: { dependencyTypes: ['npm-dev'], dependencyTypesNot: ['type-only'] },
    })
    expect(ruleNamed(DEVELOPMENT_DEPENDENCY_RULE, ['tests'])).toBeUndefined()
  })

  it('leaves the Storybook configuration, a root of its own or under the whole member, to import the workbench', () => {
    expect(ruleNamed(DEVELOPMENT_DEPENDENCY_RULE, ['src', '.storybook'])).toMatchObject({
      from: { path: '^(src/)' },
    })
    expect(ruleNamed(TEST_CODE_RULE, ['.storybook'])).toBeUndefined()
    expect(isExemptFromDevelopmentDependencyRule('.storybook/preview.tsx')).toBe(true)
    expect(isExemptFromDevelopmentDependencyRule('src/storybook/preview.tsx')).toBe(false)
  })

  it.each(['postcss.config.mjs', 'eslint.config.js', 'tailwind.config.ts', 'vitest.config.mts', 'tests/total.spec.ts'])(
    'exempts the development-time file %s of a member declared whole as a root',
    (file: string) => {
      expect(isExemptFromDevelopmentDependencyRule(file)).toBe(true)
    },
  )

  it.each(['index.ts', 'src/app/page.tsx', 'next.config.ts', 'lib/postcss.config.ts'])(
    'holds the production file %s of a member declared whole as a root',
    (file: string) => {
      expect(isExemptFromDevelopmentDependencyRule(file)).toBe(false)
    },
  )
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
    const components: readonly unknown[] = [sbomOf(SET, subject)['components']].flat()
    expect(components.map((component: unknown): unknown => asRecord(component)['purl'])).toEqual([
      'pkg:npm/%40scope/tool@1.0.0',
      'pkg:npm/fsevents@2.3.3',
      'pkg:npm/left-pad@1.3.0',
    ])
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

// Whether a member holding exactly these files, with this manifest and these nested members, keeps a script source.
const hasScripts = (
  files: readonly string[],
  manifest: Readonly<Record<string, unknown>> = {},
  nestedMembers: readonly string[] = [],
): boolean => {
  const root: string = temporaryProject(Object.fromEntries(files.map((file: string): [string, string] => [file, ''])))
  try {
    return hasScriptSources({ ...memberAt(root, manifest), siblingPaths: nestedMembers })
  } finally {
    removeProject(root)
  }
}

const GENERATED: Readonly<Record<string, unknown>> = {
  airness: { generatedArtefacts: [{ pattern: 'src/generated/**', reason: 'a client generator writes it' }] },
}

describe('hasScriptSources', PROCESS_TIME, () => {
  it('counts no script in a stylesheet-only member that carries the pinned configurations', () => {
    expect(
      hasScripts(['eslint.config.mjs', 'vitest.config.mts', 'package.json', 'src/main/resources/static/css/site.css']),
    ).toBe(false)
  })

  it.each(['tests/e2e/home.e2e.spec.ts', 'public/menu.js', 'nested/eslint.config.mjs'])(
    'counts the script %s, whatever its directory',
    (file: string) => {
      expect(hasScripts([file], GENERATED, ['ui'])).toBe(true)
    },
  )

  it.each(['src/generated/api.ts', 'ui/src/main.ts'])(
    'counts no generated or nested member script: %s',
    (file: string) => {
      expect(hasScripts([file], GENERATED, ['ui'])).toBe(false)
    },
  )
})
