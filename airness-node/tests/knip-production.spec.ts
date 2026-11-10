// The production run of the dead-code gate against real projects in temporary working trees: an export or a
// production file that only a Vitest or a Playwright spec reaches fails a member that declares
// `productionDeadCode`, while an export production code uses, and the declared public interface, pass.
import { describe, expect, it } from 'vitest'
import { knip } from '@/gate-knip.js'
import type { GateResult } from '@/gate-result.js'
import { asRecord } from '@/json-shapes.js'
import { layerDefaultsOf } from '@/layer.js'
import { type LayerDefaults, readSettings } from '@/settings.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

// Each run starts knip twice, and each knip process reads the whole project.
const TOOL_TIMEOUT_MS: number = 90_000

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
  name: 'ledger',
  private: true,
  type: 'module',
  main: 'src/refund.ts',
  devDependencies: { '@airness/node': '2.0.0', vitest: '5.0.5', '@vitest/coverage-v8': '5.0.5' },
  airness: { productionDeadCode: true },
}

const PLAYWRIGHT_MANIFEST: Readonly<Record<string, unknown>> = {
  ...MANIFEST,
  devDependencies: { ...asRecord(MANIFEST['devDependencies']), '@playwright/test': '1.64.1' },
}

const NEGATED: string = 'export const negated = (amount: number): number => -amount\n'

// Beside `negated`, an export that only a spec reaches.
const WITH_DOUBLED: string = `${NEGATED}\nexport const doubled = (amount: number): number => amount + amount\n`

// `refund` is the declared public interface, and `negated` an export production code uses.
const LEDGER: Readonly<Record<string, string>> = {
  'tsconfig.json': TSCONFIG,
  'vitest.config.mts': "import config from '@airness/node/vitest'\n\nexport default config\n",
  'src/refund.ts':
    "import { negated } from './amount.js'\n\nexport const refund = (paid: number): number => negated(paid)\n",
  'src/amount.ts': NEGATED,
  'tests/refund.spec.ts': [
    "import { expect, it } from 'vitest'",
    "import { refund } from '@/refund.js'",
    '',
    "it('refunds the amount paid', () => {",
    '  expect(refund(2)).toBe(-2)',
    '})',
    '',
  ].join('\n'),
}

const VITEST_REACH: Readonly<Record<string, string>> = {
  'src/amount.ts': WITH_DOUBLED,
  'tests/amount.spec.ts': [
    "import { expect, it } from 'vitest'",
    "import { doubled } from '@/amount.js'",
    '',
    "it('doubles an amount', () => {",
    '  expect(doubled(2)).toBe(4)',
    '})',
    '',
  ].join('\n'),
}

const PLAYWRIGHT_REACH: Readonly<Record<string, string>> = {
  'src/amount.ts': WITH_DOUBLED,
  'tests/e2e/amount.e2e.spec.ts': [
    "import { expect, test } from '@playwright/test'",
    "import { doubled } from '../../src/amount.js'",
    '',
    "test('doubles an amount', () => {",
    '  expect(doubled(2)).toBe(4)',
    '})',
    '',
  ].join('\n'),
}

// A module only a specification under the sources reaches, as a harness package ships one.
const SHIPPED_REACH: Readonly<Record<string, string>> = {
  'src/boundary.ts': 'export const boundary = (amount: number): boolean => amount > 0\n',
  'src/boundary.e2e.spec.ts': [
    "import { expect, test } from '@playwright/test'",
    "import { boundary } from './boundary.js'",
    '',
    "test('admits a positive amount', () => {",
    '  expect(boundary(1)).toBe(true)',
    '})',
    '',
  ].join('\n'),
}

// A framework application whose record only its configuration reaches: the framework boots from that file, which
// nothing in the application imports.
const FRAMEWORK_REACH: Readonly<Record<string, string>> = {
  'src/app.config.ts': "import { posts } from './records/posts.js'\n\nexport default { records: [posts] }\n",
  'src/records/posts.ts': "export const posts: { readonly slug: string } = { slug: 'posts' }\n",
}

// The seam a stack layer declares for the file its framework boots from.
const FRAMEWORK_LAYER: LayerDefaults = layerDefaultsOf({
  airness: { layer: { productionEntries: ['src/app.config.ts'] } },
})

const manifestFile = (manifest: Readonly<Record<string, unknown>>): string =>
  `${JSON.stringify(manifest, null, JSON_INDENT)}\n`

const judged = (
  files: Readonly<Record<string, string>>,
  manifest: Readonly<Record<string, unknown>> = MANIFEST,
  base: Readonly<Record<string, string>> = LEDGER,
  layers: readonly LayerDefaults[] = [],
): GateResult => {
  const root: string = temporaryProject({ ...base, ...files, 'package.json': manifestFile(manifest) })
  try {
    return knip({ ...memberAt(root, manifest), settings: readSettings(manifest, layers) })
  } finally {
    removeProject(root)
  }
}

const findingsOf = (result: GateResult): string => result.findings.join('\n')

describe('knip production run', PROCESS_TIME, () => {
  it(
    'fails an export only a Vitest spec imports, and names the repair',
    () => {
      const result: GateResult = judged(VITEST_REACH)
      expect(findingsOf(result)).toMatch(/doubled\s+src\/amount\.ts/u)
      expect(findingsOf(result)).toContain('Test each through the production code that uses it')
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'fails an export only a Playwright spec imports',
    () => {
      expect(findingsOf(judged(PLAYWRIGHT_REACH, PLAYWRIGHT_MANIFEST))).toMatch(/doubled\s+src\/amount\.ts/u)
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'passes an export production code uses and the export of the declared public interface',
    () => {
      const result: GateResult = judged(VITEST_REACH)
      expect(findingsOf(result)).not.toMatch(/negated\s/u)
      expect(findingsOf(result)).not.toMatch(/refund\s/u)
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'passes a member whose every export production code uses',
    () => {
      expect(judged({}).findings).toEqual([])
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'reports the findings of both runs when each fails',
    () => {
      const result: GateResult = judged({ ...VITEST_REACH, 'src/orphan.ts': 'export const orphan: number = 1\n' })
      expect(findingsOf(result)).toMatch(/^src\/orphan\.ts/mu)
      expect(findingsOf(result)).toMatch(/doubled\s+src\/amount\.ts/u)
      expect(result.summary).toBe(
        'dead code or an unused dependency was found; an export or a production file only tests reach was found',
      )
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'judges an export only a spec imports as used where the member does not declare the production run',
    () => {
      const { airness: _declared, ...undeclared } = MANIFEST
      expect(judged(VITEST_REACH, undeclared).findings).toEqual([])
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'fails a production file only a spec reaches, and leaves a test helper outside production to the specs',
    () => {
      const result: GateResult = judged({
        'src/sample.ts': 'export const sample: number = 2\n',
        'tests/helpers/sample.ts': "export { sample } from '@/sample.js'\n",
        'tests/sample.spec.ts': [
          "import { expect, it } from 'vitest'",
          "import { refund } from '@/refund.js'",
          "import { sample } from './helpers/sample.js'",
          '',
          "it('refunds a sample', () => {",
          '  expect(refund(sample)).toBe(-sample)',
          '})',
          '',
        ].join('\n'),
      })
      expect(findingsOf(result)).toMatch(/^src\/sample\.ts/mu)
      expect(findingsOf(result)).not.toContain('tests/helpers/sample.ts')
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    "counts a layer's framework configuration as a production entry point, and what only it reaches as used",
    () => {
      expect(findingsOf(judged(FRAMEWORK_REACH, MANIFEST, LEDGER, [FRAMEWORK_LAYER]))).not.toContain(
        'src/records/posts.ts',
      )
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'leaves what only a framework configuration reaches to the tests where no layer names it',
    () => {
      expect(findingsOf(judged(FRAMEWORK_REACH))).toMatch(/^src\/records\/posts\.ts/mu)
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'counts the specifications a harness package ships from its sources as production entry points',
    () => {
      const harness: Readonly<Record<string, unknown>> = { ...PLAYWRIGHT_MANIFEST, name: '@airness/node' }
      expect(findingsOf(judged(SHIPPED_REACH, harness))).not.toContain('src/boundary.ts')
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'leaves a specification under the sources of any other member to the tests',
    () => {
      expect(findingsOf(judged(SHIPPED_REACH, PLAYWRIGHT_MANIFEST))).toMatch(/^src\/boundary\.ts/mu)
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'judges the subdirectories of a member that declares itself whole as a source root',
    () => {
      const whole: Readonly<Record<string, unknown>> = {
        ...MANIFEST,
        main: 'lib/refund.ts',
        airness: { productionDeadCode: true, sourceRoots: ['.'] },
      }
      const moved: Readonly<Record<string, string>> = Object.fromEntries(
        Object.entries({ ...LEDGER, ...VITEST_REACH }).map(([file, content]: readonly [string, string]) => [
          file.replace(/^src\//u, 'lib/'),
          content.replace(/'@\/(\w+)\.js'/u, "'../lib/$1.js'"),
        ]),
      )
      const result: GateResult = judged({}, whole, moved)
      expect(findingsOf(result)).toMatch(/doubled\s+lib\/amount\.ts/u)
    },
    TOOL_TIMEOUT_MS,
  )
})
