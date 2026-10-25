// Each gate against a real project in a temporary git working tree: one conforming project, and one
// planted defect per gate, so a gate that stopped judging would let the defect through.
import { writeFileSync } from 'node:fs'
import path from 'node:path'
import { afterAll, describe, expect, it } from 'vitest'
import type { GateResult } from '@/exec.js'
import { biome } from '@/gate-biome.js'
import { configReferences } from '@/gate-config-references.js'
import { eslint } from '@/gate-eslint.js'
import { javascriptAllowlist } from '@/gate-javascript-allowlist.js'
import { oxlint } from '@/gate-oxlint.js'
import { sourceRules } from '@/gate-source-rules.js'
import { types } from '@/gate-types.js'
import { GATES, gateById } from '@/gates.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

// The type-aware lint and the compiler each start a process that reads the whole project.
const TOOL_TIMEOUT_MS: number = 60_000

// A stack layer's ESLint export, planted under node_modules: the node blocks, then the given ones.
const layerConfig = (blocks: string): string => `import node from '@airness/node/eslint'\nexport default ${blocks}\n`

// Five lint runs, one per layer configuration the spec plants.
const LAYERED_TIMEOUT_MS: number = 300_000

// Formatted the way Biome writes JSON, since Biome reads the manifest and the tsconfig too.
const JSON_INDENT: number = 2

const TSCONFIG: string = [
  '{',
  '  "extends": "@airness/node/tsconfig.json",',
  '  "compilerOptions": {',
  '    "types": []',
  '  },',
  '  "include": ["**/*.ts", "**/*.tsx", "**/*.mts", "**/*.cts"],',
  '  "exclude": ["node_modules", "dist", "target"]',
  '}',
  '',
].join('\n')

const CONFORMING: Readonly<Record<string, string>> = {
  'package.json': `${JSON.stringify({ name: 'conforming', private: true, type: 'module' }, null, JSON_INDENT)}\n`,
  'tsconfig.json': TSCONFIG,
  'src/total.ts': [
    '/**',
    ' * Add two amounts.',
    ' * @param left the first amount.',
    ' * @param right the second amount.',
    ' * @returns their sum.',
    ' */',
    'export const total = (left: number, right: number): number => left + right',
    '',
  ].join('\n'),
}

const conforming: string = temporaryProject(CONFORMING)

// Each planted project is a sibling of the conforming one, so removing their common parent's children
// by prefix would reach the specs; each test removes its own instead.
const withPlanted = (files: Readonly<Record<string, string>>, use: (root: string) => void): void => {
  const root: string = temporaryProject({ ...CONFORMING, ...files })
  try {
    use(root)
  } finally {
    removeProject(root)
  }
}

afterAll(() => {
  removeProject(conforming)
})

const findingsOf = (result: GateResult): string => result.findings.join('\n')

/** A manifest declaring settings under the airness key. */
type Manifest = Readonly<Record<string, unknown>>

describe('the gate registry', PROCESS_TIME, () => {
  it('ships every node gate under its id, in the order the parent binds them', () => {
    expect(GATES.map((gate) => gate.id)).toEqual([
      'wiring',
      'types',
      'biome',
      'oxlint',
      'eslint',
      'source-rules',
      'config-refs',
      'javascript-allowlist',
      'css',
      'tailwind-tokens',
      'arch',
      'type-coverage',
      'knip',
      'tests',
      'install-scripts',
      'release-age',
      'npm-inventory',
      'npm-sbom',
      'build',
      'bundle',
      'e2e',
    ])
    expect(gateById('payload-defaults')).toBeUndefined()
  })
})

describe('javascript-allowlist', PROCESS_TIME, () => {
  it('passes TypeScript and the ESLint configuration', () => {
    withPlanted({ 'eslint.config.mjs': "export { default } from '@airness/node/eslint'\n" }, (root: string) => {
      expect(javascriptAllowlist(memberAt(root)).findings).toEqual([])
    })
  })

  it('fails hand-written JavaScript outside the allowlist', () => {
    withPlanted({ 'src/legacy.js': 'export const a = 1\n' }, (root: string) => {
      expect(findingsOf(javascriptAllowlist(memberAt(root)))).toContain(
        'src/legacy.js hand-written JavaScript is banned; write TypeScript instead',
      )
    })
  })

  it('admits a file an allowlist entry names with its role and runtime, and refuses one without a runtime', () => {
    const manifest: Manifest = {
      airness: {
        javascriptAllowlist: [
          { pattern: '^edge/', reason: 'run by CloudFront without a build', runtime: 'cloudfront-js-2.0' },
          { pattern: '^tools/', reason: 'a script node runs directly' },
        ],
      },
    }
    withPlanted({ 'edge/viewer.js': 'var a = 1\n', 'tools/run.mjs': 'export {}\n' }, (root: string) => {
      const findings: string = findingsOf(javascriptAllowlist(memberAt(root, manifest)))
      expect(findings).not.toContain('edge/viewer.js')
      expect(findings).toContain('tools/run.mjs hand-written JavaScript is banned')
      expect(findings).toContain('entry "^tools/" declares no runtime')
    })
  })
})

describe('source-rules', PROCESS_TIME, () => {
  it('passes source that imports through the alias', () => {
    expect(sourceRules(memberAt(conforming)).findings).toEqual([])
  })

  it('fails a parent-relative import under a source root, naming the file and line', () => {
    withPlanted({ 'src/deep/read.ts': "import { total } from '../total.js'\nexport { total }\n" }, (root: string) => {
      expect(findingsOf(sourceRules(memberAt(root)))).toContain('src/deep/read.ts:1 [no-deep-relative-imports]')
    })
  })

  it('reads only the declared source roots', () => {
    withPlanted({ 'other/read.ts': "import { total } from '../src/total.js'\nexport { total }\n" }, (root: string) => {
      expect(sourceRules(memberAt(root)).findings).toEqual([])
    })
  })
})

describe('config-refs', PROCESS_TIME, () => {
  it('passes a project that declares nothing', () => {
    expect(configReferences(memberAt(conforming)).findings).toEqual([])
  })

  it('fails a declared exclusion that reaches nothing and one that states no role', () => {
    const manifest: Manifest = {
      airness: { generatedArtefacts: [{ pattern: 'src/generated/**', reason: 'the API client' }, 'src/other/**'] },
    }
    const findings: string = findingsOf(configReferences(memberAt(conforming, manifest)))
    expect(findings).toContain('generatedArtefacts entry "src/generated/**" excludes nothing')
    expect(findings).toContain('generatedArtefacts entry "src/other/**" states no reason')
  })

  it('fails a project configuration that carves out a file that does not exist', () => {
    withPlanted({ 'biome.json': '{ "files": { "includes": ["!src/gone.ts"] } }\n' }, (root: string) => {
      expect(findingsOf(configReferences(memberAt(root)))).toContain('biome.json: src/gone.ts')
    })
  })
})

describe('biome', PROCESS_TIME, () => {
  it('passes formatted source', () => {
    expect(biome(memberAt(conforming)).findings).toEqual([])
  })

  it('fails a formatting defect', () => {
    withPlanted({ 'src/total.ts': 'export const total = (a: number, b: number): number => a+b;\n' }, (root: string) => {
      expect(findingsOf(biome(memberAt(root)))).toContain('src/total.ts format')
    })
  })

  it('checks a Next.js application with the React and Next.js domains added', () => {
    expect(biome({ ...memberAt(conforming), hasOwnRuntime: true }).findings).toEqual([])
    withPlanted({ 'src/total.ts': 'export const total = (a: number, b: number): number => a+b;\n' }, (root: string) => {
      expect(findingsOf(biome({ ...memberAt(root), hasOwnRuntime: true }))).toContain('src/total.ts format')
    })
  })

  it("fires the React domain and the project domain's import graph in a Next.js application", () => {
    const planted: Readonly<Record<string, string>> = {
      'src/list.tsx':
        'export const List = ({ items }: { readonly items: readonly string[] }) => (\n  <ul>\n' +
        '    {items.map((item) => (\n      <li>{item}</li>\n    ))}\n  </ul>\n)\n',
      'src/cycle-first.ts': "import { second } from './cycle-second'\n\nexport const first = (): number => second()\n",
      'src/cycle-second.ts': "import { first } from './cycle-first'\n\nexport const second = (): number => first()\n",
    }
    withPlanted(planted, (root: string) => {
      const findings: string = findingsOf(biome({ ...memberAt(root), hasOwnRuntime: true }))
      expect(findings).toContain('lint/correctness/useJsxKeyInIterable')
      expect(findings).toContain('lint/suspicious/noImportCycles')
    })
  })

  it('refuses a nested configuration, which would replace the harness one for its directory', () => {
    withPlanted({ 'src/biome.json': '{ "root": false }\n' }, (root: string) => {
      expect(findingsOf(biome(memberAt(root)))).toContain('src/biome.json: Biome configuration is owned by Airness')
    })
  })

  it('refuses a nested dotted configuration, which Biome loads as well', () => {
    withPlanted({ 'src/.biome.jsonc': '{ "root": false, "linter": { "enabled": false } }\n' }, (root: string) => {
      expect(findingsOf(biome(memberAt(root)))).toContain('src/.biome.jsonc: Biome configuration is owned by Airness')
    })
  })

  it('judges by the shipped configuration whatever the project root configuration turns off', () => {
    const off: string = '{ "formatter": { "enabled": false }, "linter": { "enabled": false } }\n'
    withPlanted({ 'biome.json': off, 'src/total.ts': 'export const total = 1;\n' }, (root: string) => {
      expect(findingsOf(biome(memberAt(root)))).toContain('src/total.ts format')
    })
  })
})

describe('oxlint', PROCESS_TIME, () => {
  it('passes source that follows the native rules', () => {
    expect(oxlint(memberAt(conforming)).findings).toEqual([])
  })

  it('fails a promise executor that returns a value', () => {
    const wait: string = 'export const wait = (): Promise<number> => new Promise((resolve) => resolve(1))\n'
    withPlanted({ 'src/wait.ts': wait }, (root: string) => {
      expect(findingsOf(oxlint(memberAt(root)))).toContain('no-promise-executor-return')
    })
  })

  it('refuses a project Oxlint configuration', () => {
    withPlanted({ '.oxlintrc.json': '{}\n' }, (root: string) => {
      expect(findingsOf(oxlint(memberAt(root)))).toContain('.oxlintrc.json: Oxlint configuration is owned by Airness')
    })
  })
})

describe('types', PROCESS_TIME, () => {
  it('fails a tsconfig.json that relaxes the shipped options before running the compiler', () => {
    const relaxed: string = '{ "compilerOptions": { "strict": false }, "include": ["src/**/*.ts"] }\n'
    withPlanted({ 'tsconfig.json': relaxed }, (root: string) => {
      const findings: string = findingsOf(types(memberAt(root)))
      expect(findings).toContain('must declare "extends": "@airness/node/tsconfig.json"')
      expect(findings).toContain('compilerOptions.strict')
      expect(findings).toContain('tsconfig.json include')
    })
  })

  it(
    'passes a project that type-checks and fails one that does not',
    () => {
      expect(types(memberAt(conforming)).findings).toEqual([])
      withPlanted({ 'src/wrong.ts': "export const wrong: number = 'text'\n" }, (root: string) => {
        expect(findingsOf(types(memberAt(root)))).toContain("Type 'string' is not assignable to type 'number'")
      })
    },
    TOOL_TIMEOUT_MS,
  )
})

describe('eslint', PROCESS_TIME, () => {
  it(
    'judges by the shipped configuration whatever the project configuration turns off, and fails a member with nothing to lint',
    () => {
      const off: string = "export default [{ ignores: ['**'] }]\n"
      withPlanted(
        { 'eslint.config.mjs': off, 'src/limit.ts': '/** The limit. */\nexport const limit: number = 42 * 3\n' },
        (root: string) => {
          expect(findingsOf(eslint(memberAt(root)))).toContain('no-magic-numbers')
        },
      )
      const generated: string = `${JSON.stringify(
        { airness: { generatedArtefacts: [{ pattern: 'src/**', reason: 'every source is generated' }] } },
        null,
        JSON_INDENT,
      )}\n`
      withPlanted({ 'package.json': generated }, (root: string) => {
        expect(eslint(memberAt(root)).ok).toBe(false)
      })
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'adds the blocks of a declared stack layer after the node layer, and refuses one that weakens it',
    () => {
      const manifest: Manifest = {
        name: 'conforming',
        private: true,
        type: 'module',
        devDependencies: { ploaness: '1.0.0' },
      }
      const layer: Manifest = {
        name: 'ploaness',
        type: 'module',
        exports: { './eslint': './eslint.js', './package.json': './package.json' },
        airness: { layer: { eslint: './eslint' } },
      }
      const writeLayer = (root: string, blocks: string): void => {
        writeFileSync(path.join(root, 'node_modules', 'ploaness', 'eslint.js'), layerConfig(blocks))
      }
      withPlanted(
        {
          'package.json': `${JSON.stringify(manifest, null, JSON_INDENT)}\n`,
          'node_modules/ploaness/package.json': JSON.stringify(layer),
          'node_modules/ploaness/eslint.js': layerConfig(
            "[...node, { files: ['src/**/*.ts'], rules: { 'id-denylist': ['error', 'left'] } }]",
          ),
          // The project's own configuration turns everything off, and the verdict does not move.
          'eslint.config.mjs': "export default [{ ignores: ['**'] }]\n",
        },
        (root: string) => {
          expect(findingsOf(eslint(memberAt(root, manifest)))).toContain('id-denylist')
          writeLayer(root, "[...node, { files: ['src/**/*.ts'], rules: { 'no-magic-numbers': 'off' } }]")
          expect(findingsOf(eslint(memberAt(root, manifest)))).toContain('weakens the node rule no-magic-numbers')
          writeLayer(root, "[{ files: ['**/*.ts'], rules: {} }]")
          expect(findingsOf(eslint(memberAt(root, manifest)))).toContain("does not start with the node layer's blocks")
          writeLayer(root, "[...node, { ignores: ['src/**'] }]")
          expect(findingsOf(eslint(memberAt(root, manifest)))).toContain('ignores files for every block')
          // A layer that names no configuration leaves the node layer's in place.
          writeFileSync(
            path.join(root, 'node_modules', 'ploaness', 'package.json'),
            JSON.stringify({ ...layer, airness: { layer: {} } }),
          )
          expect(eslint(memberAt(root, manifest)).findings).toEqual([])
        },
      )
    },
    LAYERED_TIMEOUT_MS,
  )

  it(
    'passes a conforming project and fails a rule the harness configuration holds it to',
    () => {
      expect(eslint(memberAt(conforming)).findings).toEqual([])
      withPlanted({ 'src/limit.ts': '/** The limit. */\nexport const limit: number = 42 * 3\n' }, (root: string) => {
        expect(findingsOf(eslint(memberAt(root)))).toContain('no-magic-numbers')
      })
    },
    TOOL_TIMEOUT_MS,
  )
})
