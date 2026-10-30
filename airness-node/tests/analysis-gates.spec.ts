// The architecture, type-coverage, dead-code and suite gates against real projects in temporary working
// trees: one conforming project, and one planted defect per gate.
import { describe, expect, it } from 'vitest'
import type { GateResult } from '@/exec.js'
import { architecture } from '@/gate-architecture.js'
import { knip } from '@/gate-knip.js'
import { tests } from '@/gate-tests.js'
import { typeCoverage } from '@/gate-type-coverage.js'
import { type Gate, gateById } from '@/gates.js'
import { asRecord as asRecordOf } from '@/json-shapes.js'
import type { Member } from '@/project.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

// Each analyzer starts a process that reads the whole project.
const TOOL_TIMEOUT_MS: number = 60_000

const JSON_INDENT: number = 2

const TSCONFIG: string = `${JSON.stringify(
  {
    extends: '@airness/node/tsconfig.json',
    compilerOptions: { types: [], paths: { '@/*': ['./src/*'] }, outDir: 'dist' },
    include: ['**/*.ts', '**/*.tsx', '**/*.mts', '**/*.cts'],
    exclude: ['node_modules', 'dist', 'target'],
  },
  null,
  JSON_INDENT,
)}\n`

const MANIFEST: Readonly<Record<string, unknown>> = {
  name: 'library',
  private: true,
  type: 'module',
  main: 'src/total.ts',
  scripts: { verify: 'mvn clean verify' },
  devDependencies: { '@airness/node': '2.0.0', vitest: '5.0.5', '@vitest/coverage-v8': '5.0.5' },
}

const CONFORMING: Readonly<Record<string, string>> = {
  'package.json': `${JSON.stringify(MANIFEST, null, JSON_INDENT)}\n`,
  'tsconfig.json': TSCONFIG,
  'vitest.config.mts': "import config from '@airness/node/vitest'\n\nexport default config\n",
  'src/total.ts': 'export const total = (left: number, right: number): number => left + right\n',
  'tests/total.spec.ts': [
    "import { expect, it } from 'vitest'",
    "import { total } from '@/total.js'",
    '',
    "it('adds two amounts', () => {",
    '  expect(total(1, 2)).toBe(3)',
    '})',
    '',
  ].join('\n'),
}

// A layer package these specs name as a layer: a fixture's, so the seam is measured whatever the harness registers.
const LAYER: string = 'layer-a'

// A function with a branch no conforming spec covers, so a file holding it falls below the floor.
const SIGN_SOURCE: string =
  'export const sign = (value: number): string => {\n  if (value < 0) {\n    return "negative"\n  }\n' +
  '  return "positive"\n}\n'

const withProject = (files: Readonly<Record<string, string>>, use: (root: string) => void): void => {
  const root: string = temporaryProject({ ...CONFORMING, ...files })
  try {
    use(root)
  } finally {
    removeProject(root)
  }
}

const findingsOf = (result: GateResult): string => result.findings.join('\n')

// The gate as node-gate runs it, through the registry, with the layers and the configuration it reads by default.
const registered = async (id: string, member: Member): Promise<GateResult> => {
  const gate: Gate | undefined = gateById(id)
  if (gate === undefined) {
    throw new Error(`the registry ships no gate ${id}`)
  }
  return await gate.run(member, { evidenceDirectory: undefined })
}

describe('arch', PROCESS_TIME, () => {
  it(
    'passes acyclic modules and fails a cycle',
    () => {
      withProject({}, (root: string): void => {
        expect(architecture(memberAt(root)).ok).toBe(true)
      })
      withProject(
        {
          'src/first.ts': "import { second } from './second.js'\n\nexport const first = (): number => second()\n",
          'src/second.ts': "import { first } from './first.js'\n\nexport const second = (): number => first()\n",
        },
        (root: string): void => {
          expect(findingsOf(architecture(memberAt(root)))).toContain('no-circular')
        },
      )
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'holds a Next.js application to its application layer, and a declared floor to purity',
    () => {
      const layered: Readonly<Record<string, string>> = {
        'src/app/page.ts': 'export const page = 1\n',
        'src/lib/price.ts': "import { page } from '../app/page.js'\n\nexport const price = (): number => page\n",
      }
      const floor: Readonly<Record<string, unknown>> = {
        ...MANIFEST,
        airness: { pureLogicRoots: [{ pattern: 'src/lib', reason: 'the pricing rules' }] },
      }
      withProject(layered, (root: string): void => {
        expect(architecture(memberAt(root)).ok).toBe(true)
        const findings: string = findingsOf(architecture({ ...memberAt(root, floor), hasOwnRuntime: true }))
        expect(findings).toContain('nothing-imports-app')
        expect(findings).toContain('pure-logic-stays-pure')
      })
      withProject(
        {
          'src/document-types.ts': 'export interface Document { readonly amount: number }\n',
          'src/lib/price.ts':
            "import type { Document } from '../document-types.js'\nexport const price = (doc: Document): number => doc.amount\n",
        },
        (root: string): void => {
          expect(architecture(memberAt(root, floor)).ok).toBe(true)
        },
      )
    },
    TOOL_TIMEOUT_MS,
  )

  it('passes a member with no source directory', () => {
    withProject({}, (root: string): void => {
      expect(
        architecture({ ...memberAt(root), settings: { ...memberAt(root).settings, sourceRoots: ['absent'] } }).summary,
      ).toBe('the member holds no source directory to cruise')
    })
  })
})

describe('type-coverage', PROCESS_TIME, () => {
  it(
    'passes fully typed logic and fails an explicit any',
    () => {
      withProject({}, (root: string): void => {
        expect(typeCoverage(memberAt(root)).ok).toBe(true)
      })
      const unexcluded: string = TSCONFIG.replace(/,\n {2}"exclude": \[[^\]]*\]/u, '')
      withProject(
        { 'src/loose.ts': 'export const loose = (value: any): number => value.length\n', 'tsconfig.json': unexcluded },
        (root: string): void => {
          expect(findingsOf(typeCoverage(memberAt(root)))).toContain('src/loose.ts')
        },
      )
    },
    TOOL_TIMEOUT_MS,
  )
})

describe('knip', PROCESS_TIME, () => {
  it('counts a supplied Vite transform peer as used by the declared test engine', () => {
    const manifest: Readonly<Record<string, unknown>> = {
      ...MANIFEST,
      devDependencies: { ...asRecordOf(MANIFEST['devDependencies']), esbuild: '0.28.2' },
    }
    withProject({ 'package.json': JSON.stringify(manifest) }, (root: string): void => {
      expect(knip(memberAt(root, manifest)).ok).toBe(true)
      const withoutTests: Readonly<Record<string, unknown>> = { devDependencies: { esbuild: '0.28.2' } }
      expect(findingsOf(knip(memberAt(root, withoutTests)))).toContain('esbuild')
    })
  })
  it(
    'passes a project whose every file and export is used, and fails an unused file',
    () => {
      withProject({}, (root: string): void => {
        expect(knip(memberAt(root)).findings).toEqual([])
      })
      withProject({ 'src/orphan.ts': 'export const orphan = 1\n' }, (root: string): void => {
        expect(findingsOf(knip(memberAt(root)))).toContain('src/orphan.ts')
        expect(findingsOf(knip({ ...memberAt(root), siblingPaths: ['src'] }))).not.toContain('src/orphan.ts')
      })
      const harness: string = JSON.stringify({ ...MANIFEST, name: '@airness/node', dependencies: { oxlint: '1.89.0' } })
      withProject({ 'package.json': `${harness}\n` }, (root: string): void => {
        expect(knip(memberAt(root, JSON.parse(harness))).findings).toEqual([])
      })
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'counts a declared layer package as used only while the harness names it as a layer',
    async () => {
      const layered: Readonly<Record<string, unknown>> = {
        ...MANIFEST,
        devDependencies: { ...asRecordOf(MANIFEST['devDependencies']), [LAYER]: '2.0.0' },
      }
      const root: string = temporaryProject({
        ...CONFORMING,
        'package.json': `${JSON.stringify(layered, null, JSON_INDENT)}\n`,
      })
      try {
        expect(knip(memberAt(root, layered), [LAYER]).findings).toEqual([])
        expect(findingsOf(await registered('knip', memberAt(root, layered)))).toContain(LAYER)
      } finally {
        removeProject(root)
      }
    },
    TOOL_TIMEOUT_MS,
  )
})

describe('tests', PROCESS_TIME, () => {
  it(
    'passes a suite that covers its source and fails a file below the floor',
    () => {
      withProject({}, (root: string): void => {
        expect(tests(memberAt(root)).findings).toEqual([])
      })
      withProject(
        {
          'src/sign.ts':
            'export const sign = (value: number): string => {\n  if (value < 0) {\n    return "negative"\n  }\n' +
            '  return "positive"\n}\n',
        },
        (root: string): void => {
          expect(findingsOf(tests(memberAt(root)))).toContain('src/sign.ts')
        },
      )
    },
    TOOL_TIMEOUT_MS,
  )

  it('fails a member whose Vitest cannot be resolved', () => {
    withProject(
      { 'node_modules/vitest/package.json': '{"name":"vitest","exports":{".":"./index.js"}}\n' },
      (root: string): void => {
        expect(tests(memberAt(root)).summary).toBe('vitest could not be resolved from the member')
      },
    )
  })

  it('passes a member that holds no measured source without running a suite', () => {
    withProject({}, (root: string): void => {
      expect(tests({ ...memberAt(root), settings: { ...memberAt(root).settings, sourceRoots: [] } }).summary).toBe(
        'the member holds no source the floor measures, so there is no suite to run',
      )
    })
  })
})

describe('type-coverage settings', PROCESS_TIME, () => {
  it('refuses type-coverage options a project sets for itself', () => {
    withProject({}, (root: string): void => {
      expect(typeCoverage(memberAt(root, { typeCoverage: { ignoreCatch: true } })).findings).toEqual([
        'package.json typeCoverage: type-coverage is configured by Airness; remove the key',
      ])
    })
  })
})

describe('a project configuration of its own', PROCESS_TIME, () => {
  const Sign: string = SIGN_SOURCE
  const Cycle: Readonly<Record<string, string>> = {
    'src/first.ts': "import { second } from './second.js'\n\nexport const first = (): number => second()\n",
    'src/second.ts': "import { first } from './first.js'\n\nexport const second = (): number => first()\n",
  }

  it(
    'cannot lower the coverage floor the tests gate holds a file to',
    () => {
      const lowered: string =
        "import { defineConfig } from 'vitest/config'\n\nexport default defineConfig({ test: { coverage: " +
        '{ thresholds: { lines: 0, branches: 0, functions: 0, statements: 0 } } } })\n'
      withProject({ 'src/sign.ts': Sign, 'vitest.config.mts': lowered }, (root: string): void => {
        expect(findingsOf(tests(memberAt(root)))).toContain('src/sign.ts')
      })
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'cannot hide an unused file from knip',
    () => {
      const ignoring: string = '{ "ignore": ["src/orphan.ts"] }\n'
      withProject({ 'src/orphan.ts': 'export const orphan = 1\n', 'knip.json': ignoring }, (root: string): void => {
        expect(findingsOf(knip(memberAt(root)))).toContain('src/orphan.ts')
      })
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'cannot allow a cycle',
    () => {
      withProject({ ...Cycle, '.dependency-cruiser.json': '{ "forbidden": [] }\n' }, (root: string): void => {
        expect(findingsOf(architecture(memberAt(root)))).toContain('no-circular')
      })
    },
    TOOL_TIMEOUT_MS,
  )
})
