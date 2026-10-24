// Full type coverage on hand-written logic: no implicit or explicit `any`, and no type assertion, survives.
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { failed, fromRun, type GateResult, runNode } from './exec.js'
import { asRecord, asStringRecord, isArray } from './json-shapes.js'
import { harnessDirectory, type Member, readJson, resolveTool } from './project.js'

// Roles with no hand-written type surface to measure. The list must never be empty: type-coverage also
// reads a `typeCoverage` key from the project's package.json, and a command-line `--ignore-files` replaces
// that key's list rather than merging with it, which is what keeps the scope the harness's.
const TYPE_COVERAGE_IGNORE: readonly string[] = [
  // Test code is held to the compiler, every lint rule and the coverage floor. With `--strict` an assertion
  // counts as uncovered, and constructing inputs the production types cannot express is a test's job.
  'tests/**',
  '*.config.ts',
  '*.config.mts',
  '*.config.mjs',
  // What `next build` writes beside the sources: the route types it generates under `.next`, which the
  // tsconfig a Next.js application carries includes, and its ambient declaration file. Neither is hand-written.
  '.next/**',
  'next-env.d.ts',
]

const JSON_INDENT: number = 2

// The compiler options that name a path, which the rendered configuration must spell absolutely because it
// lives in another directory than the configuration that declared them.
const PATH_OPTIONS: ReadonlySet<string> = new Set<string>(['rootDir', 'outDir'])

const texts = (value: unknown): readonly string[] =>
  isArray(value) ? value.filter((entry: unknown): entry is string => typeof entry === 'string') : []

const absolutePaths = (member: Member, paths: unknown): Record<string, readonly string[]> =>
  Object.fromEntries(
    Object.entries(asRecord(paths)).map(
      ([alias, targets]: readonly [string, unknown]): readonly [string, readonly string[]] => [
        alias,
        texts(targets).map((target: string): string => path.resolve(member.root, target)),
      ],
    ),
  )

// type-coverage reads tsconfig files with a resolver of its own, which finds no `extends` written as a
// package specifier through an `exports` map, and then falls back to reading every file below the
// directory. So it is handed the member's configuration with the shipped one extended by absolute path,
// written below the member's node_modules, where the compiler still finds the member's type packages and
// the tree check does not look.
const renderedTsconfig = (member: Member): string => {
  const own: Record<string, unknown> = asRecord(readJson(path.join(member.root, 'tsconfig.json')))
  const options: Record<string, unknown> = asRecord(own['compilerOptions'])
  const directory: string = path.join(member.root, 'node_modules', '.airness')
  const rendered: string = path.join(directory, 'tsconfig.type-coverage.json')
  const absolute = (relative: string): string => path.resolve(member.root, relative)
  const pathOptions: Readonly<Record<string, string>> = Object.fromEntries(
    Object.entries(asStringRecord(options))
      .filter(([name]: readonly [string, string]): boolean => PATH_OPTIONS.has(name))
      .map(([name, value]: readonly [string, string]): readonly [string, string] => [name, absolute(value)]),
  )
  const config: Readonly<Record<string, unknown>> = {
    extends: path.join(harnessDirectory(), 'configs', 'tsconfig.json'),
    compilerOptions: {
      ...options,
      ...(options['paths'] !== undefined && { paths: absolutePaths(member, options['paths']) }),
      ...pathOptions,
      incremental: false,
    },
    include: texts(own['include']).map((pattern: string): string => absolute(pattern)),
    exclude: texts(own['exclude']).map((pattern: string): string => absolute(pattern)),
  }
  mkdirSync(directory, { recursive: true })
  writeFileSync(rendered, `${JSON.stringify(config, null, JSON_INDENT)}\n`)
  return rendered
}

// type-coverage also takes its options from a `typeCoverage` key of package.json, where a project could
// ignore catch clauses, assertions or nested anys. Only the harness decides them.
const OWN_SETTINGS: string = 'typeCoverage'

const measured = (member: Member): GateResult =>
  fromRun(
    runNode(
      resolveTool('type-coverage'),
      [
        '--project',
        renderedTsconfig(member),
        '--at-least',
        '100',
        '--strict',
        // `--cache false` passes the text "false", which is truthy, so it switched the cache on: a cache written
        // into the member and read back on the next run. `--no-cache` is the spelling that switches it off.
        '--no-cache',
        ...[
          ...new Set([
            ...TYPE_COVERAGE_IGNORE,
            ...member.settings.generatedArtefacts,
            ...member.settings.frameworkGlue,
          ]),
        ].flatMap((pattern: string): readonly string[] => ['--ignore-files', pattern]),
      ],
      { cwd: member.root },
    ),
    'hand-written logic is fully typed',
    'some hand-written logic is not fully typed',
  )

/** Hand-written logic is fully typed. */
export const typeCoverage = (member: Member): GateResult =>
  Object.hasOwn(asRecord(member.packageJson), OWN_SETTINGS)
    ? failed('the project sets type-coverage options of its own', [
        `package.json ${OWN_SETTINGS}: type-coverage is configured by Airness; remove the key`,
      ])
    : measured(member)
