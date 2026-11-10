// The architecture, type-coverage, dead-code and suite gates against real projects in temporary working
// trees: one conforming project, and one planted defect per gate.
import { describe, expect, it } from 'vitest'
import { architecture } from '@/gate-architecture.js'
import { knip } from '@/gate-knip.js'
import type { GateResult } from '@/gate-result.js'
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
    'lets a declared CloudFront function import the module its runtime provides, and nothing else',
    () => {
      const manifest: Readonly<Record<string, unknown>> = {
        ...MANIFEST,
        airness: {
          sourceRoots: ['edge'],
          javascriptAllowlist: [
            { pattern: String.raw`^edge/function\.cjs$`, runtime: 'cloudfront-js-2.0', reason: 'the edge runtime' },
          ],
        },
      }
      withProject(
        {
          'package.json': `${JSON.stringify(manifest, null, JSON_INDENT)}\n`,
          'edge/function.cjs': "const cf = require('cloudfront')\n\nmodule.exports = { cf }\n",
        },
        (root: string): void => {
          expect(findingsOf(architecture(memberAt(root, manifest)))).not.toContain('not-to-unresolvable')
        },
      )
      withProject(
        {
          'package.json': `${JSON.stringify(manifest, null, JSON_INDENT)}\n`,
          'edge/function.cjs': "const cf = require('cloudfront')\n\nmodule.exports = { cf }\n",
          'src/edge.ts': "import cf from 'cloudfront'\n\nexport const edge = cf\n",
        },
        (root: string): void => {
          expect(findingsOf(architecture(memberAt(root, manifest)))).toContain('not-to-unresolvable')
        },
      )
    },
    TOOL_TIMEOUT_MS,
  )

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

  it(
    'fails production code under any production root that imports test code, by a relative path or an alias',
    () => {
      const aliased: string = `${JSON.stringify(
        {
          extends: '@airness/node/tsconfig.json',
          compilerOptions: { types: [], paths: { '@/*': ['./src/*'], '@tests/*': ['./tests/*'] }, outDir: 'dist' },
          include: ['**/*.ts'],
          exclude: ['node_modules', 'dist', 'target'],
        },
        null,
        JSON_INDENT,
      )}\n`
      const rooted: Readonly<Record<string, unknown>> = { ...MANIFEST, airness: { sourceRoots: ['lib/'] } }
      withProject(
        {
          'package.json': `${JSON.stringify(rooted, null, JSON_INDENT)}\n`,
          'tsconfig.json': aliased,
          'tests/helpers/fixtures.ts': 'export const ORDER: number = 1\n',
          'tests/helpers/fixtures.spec.ts': "import { ORDER } from './fixtures.js'\n\nexport const used = ORDER\n",
          'src/orders.ts': "import { ORDER } from '@tests/helpers/fixtures.js'\n\nexport const order = ORDER\n",
          'lib/auth.ts': "import { ORDER } from '../tests/helpers/fixtures.js'\n\nexport const auth = ORDER\n",
        },
        (root: string): void => {
          const findings: readonly string[] = architecture(memberAt(root, rooted)).findings.filter(
            (finding: string): boolean => finding.includes('production-imports-no-test-code'),
          )
          expect(findings.join('\n')).toContain('src/orders.ts')
          expect(findings.join('\n')).toContain('lib/auth.ts')
          expect(findings.join('\n')).not.toContain('fixtures.spec.ts')
        },
      )
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'fails production code under any production root that imports a development-only dependency',
    () => {
      const rooted: Readonly<Record<string, unknown>> = { ...MANIFEST, airness: { sourceRoots: ['lib/'] } }
      withProject(
        {
          'package.json': `${JSON.stringify(rooted, null, JSON_INDENT)}\n`,
          'src/orders.ts': "import { expect } from 'vitest'\n\nexport const order = expect\n",
          'lib/auth.ts': "import { expect } from 'vitest'\n\nexport const auth = expect\n",
          'lib/auth.spec.ts': "import { expect } from 'vitest'\n\nexport const checked = expect\n",
        },
        (root: string): void => {
          const findings: string = architecture(memberAt(root, rooted))
            .findings.filter((finding: string): boolean => finding.includes('not-to-dev-dep'))
            .join('\n')
          expect(findings).toContain('src/orders.ts')
          expect(findings).toContain('lib/auth.ts')
          expect(findings).not.toContain('auth.spec.ts')
        },
      )
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'refuses a Next.js routes directory at the member root, and tells the agent to move it under src',
    () => {
      const next: Readonly<Record<string, unknown>> = { ...MANIFEST, dependencies: { next: '16.0.0' } }
      const nextMemberAt = (root: string): Member => ({ ...memberAt(root, next), hasOwnRuntime: true })
      withProject(
        {
          'package.json': `${JSON.stringify(next, null, JSON_INDENT)}\n`,
          'app/layout.tsx': 'export const layout = 1\n',
          'pages/index.tsx': 'export const index = 1\n',
        },
        (root: string): void => {
          const result: GateResult = architecture(nextMemberAt(root))
          expect(result.ok).toBe(false)
          expect(findingsOf(result)).toContain('app/: move the Next.js routes directory under src/ (src/app/)')
          expect(findingsOf(result)).toContain('pages/: move the Next.js routes directory under src/ (src/pages/)')
        },
      )
      withProject(
        {
          'package.json': `${JSON.stringify(next, null, JSON_INDENT)}\n`,
          'app/layout.tsx': 'export const layout = 1\n',
          'src/first.ts': "import { second } from './second.js'\n\nexport const first = (): number => second()\n",
          'src/second.ts': "import { first } from './first.js'\n\nexport const second = (): number => first()\n",
        },
        (root: string): void => {
          const findings: string = findingsOf(architecture(nextMemberAt(root)))
          expect(findings).toContain('app/: move the Next.js routes directory under src/ (src/app/)')
          expect(findings).toContain('no-circular')
        },
      )
      withProject(
        {
          'package.json': `${JSON.stringify(next, null, JSON_INDENT)}\n`,
          'src/app/layout.tsx': 'export const a = 1\n',
        },
        (root: string): void => {
          expect(architecture(nextMemberAt(root)).ok).toBe(true)
        },
      )
      withProject({ 'app/layout.tsx': 'export const layout = 1\n' }, (root: string): void => {
        expect(architecture(memberAt(root)).ok).toBe(true)
      })
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
    'fails an unused Vite peer declared for production, which no test engine loads',
    () => {
      const manifest: Readonly<Record<string, unknown>> = { ...MANIFEST, dependencies: { yaml: '2.9.1' } }
      withProject({ 'package.json': JSON.stringify(manifest) }, (root: string): void => {
        expect(findingsOf(knip(memberAt(root, manifest)))).toContain('yaml')
      })
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'counts a stylesheet preprocessor declared in any block as used beside a stylesheet it compiles',
    () => {
      const manifest: Readonly<Record<string, unknown>> = { ...MANIFEST, dependencies: { sass: '1.99.0' } }
      const styled: Readonly<Record<string, string>> = {
        'package.json': JSON.stringify(manifest),
        'src/theme.scss': '$gap: 1rem;\n',
        'src/total.ts': `import './theme.scss'\n\n${CONFORMING['src/total.ts'] ?? ''}`,
      }
      withProject(styled, (root: string): void => {
        expect(knip(memberAt(root, manifest)).ok).toBe(true)
      })
    },
    TOOL_TIMEOUT_MS,
  )
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
    'leaves a nested member to its own run, at a workspace root',
    () => {
      // The member's test configuration cannot load outside its own environment, as a configuration that reads
      // the member's variables cannot.
      const member: Readonly<Record<string, unknown>> = {
        name: 'member-a',
        private: true,
        type: 'module',
        devDependencies: { vitest: '5.0.5' },
      }
      withProject(
        {
          'pnpm-workspace.yaml': 'packages:\n  - member-a\n',
          'member-a/package.json': `${JSON.stringify(member, null, JSON_INDENT)}\n`,
          'member-a/vitest.config.mts': "throw new Error('the member environment is missing')\n",
        },
        (root: string): void => {
          const result: GateResult = knip({ ...memberAt(root), siblingPaths: ['member-a'] })
          expect(findingsOf(result)).not.toContain('member-a')
          expect(result.ok).toBe(true)
        },
      )
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
