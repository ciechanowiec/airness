// Dead code: unused files, exports and dependencies, under the shipped knip configuration, rendered per
// member with the member's nested members as boundaries and its allowlisted JavaScript as entry points. The
// shipped configuration counts @vitest/coverage-v8 as used, because the tests gate loads it by the provider
// name of the shipped Vitest configuration, which knip cannot see from a member that keeps no copy of it, and a
// declared stack layer package as used, because the harness reads it by name: node-gate runs its gates and the
// layer seams read its defaults, ESLint blocks and end-to-end specifications, none of which an import shows.
//
// That run counts every test and tool as an entry point, so an export only a test imports reads as used. Every
// member therefore gets a second, production run: knip follows the production entry points alone, the package's
// declared public interface among them, the files a stack layer's framework runs and those the project declares,
// and judges the exports and files under the production source roots, so an export or a production file that only
// tests and tooling reach fails too.
//
// knip's framework plugins import the configuration files they read, and a framework's configuration may validate
// its environment as it loads. Both runs therefore carry the analysis placeholders the declared layers name, under
// the member's environment files and the process's own values, so a clean checkout loads without supplying them.
import { mkdtempSync, rmSync } from 'node:fs'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { analysisEnvironment } from './environment-files.js'
import { fromRun, type RunResult, runNode } from './exec.js'
import {
  JAVASCRIPT_EXTENSIONS,
  matchesGlob,
  NON_PRODUCTION_ROOTS,
  productionSourceRoots,
  TYPESCRIPT_SOURCE_EXTENSIONS,
} from './file-roles.js'
import { failed, type GateResult, withOutput } from './gate-result.js'
import { HARNESS_PACKAGE, isHarnessPackage, SHIPPED_SPEC_SOURCES } from './harness-packages.js'
import { CLOUDFRONT_RUNTIME, rolesFor } from './javascript-runtimes.js'
import { asRecord, asText, declaredDependencies, readKey } from './json-shapes.js'
import { LAYER_NAMES, layerAnalysisEnvironmentFor } from './layer.js'
import { harnessDirectory, type Member, readJson, resolveTool, writeJson } from './project.js'
import { type DeclaredExclusion, isHonouredExclusion, PRODUCTION_ENTRIES_SETTING, retiredSettings } from './settings.js'
import { allowedJavaScriptFiles, memberFiles } from './source-selection.js'
import { isTestFile } from './spec-roles.js'
import { presentWorkingTreeFiles } from './working-tree.js'

// The harness itself reaches these by name, through the manifest resolution the gates share, which knip
// cannot follow; and it runs its own gates through its link to itself. It also pins `type-coverage-core`, which
// nothing imports: `type-coverage` asks for it by a range, and the pin holds that range to the release whose
// security replacement the harness approved. Only the harness package gets this.
const RESOLVED_BY_NAME: readonly string[] = [
  HARNESS_PACKAGE,
  '@biomejs/biome',
  'dependency-cruiser',
  'oxlint',
  'postcss-scss',
  'prettier',
  'stylelint',
  'stylelint-config-standard',
  'stylelint-scss',
  'type-coverage',
  'type-coverage-core',
]

// The entries of a list the shipped configuration holds; a missing or misshapen one holds none.
const texts = (value: unknown): readonly string[] =>
  [value].flat().filter((entry: unknown): entry is string => typeof entry === 'string')

const ownDependencies = (member: Member): readonly string[] =>
  asText(readKey(member.packageJson, 'name')) === HARNESS_PACKAGE ? RESOLVED_BY_NAME : []

// Only the layer packages the harness names, and only those the member declares: no name a project chooses can
// hide an unused dependency this way.
const declaredLayers = (member: Member, layers: readonly string[]): readonly string[] => {
  const declared: Readonly<Record<string, string>> = declaredDependencies(member.packageJson)
  return layers.filter((name: string): boolean => Object.hasOwn(declared, name))
}

// The stylesheet preprocessors among Vite's peers, each with the extensions of the stylesheets it compiles.
const STYLESHEET_PREPROCESSORS: Readonly<Record<string, readonly string[]>> = {
  sass: ['.scss', '.sass'],
  'sass-embedded': ['.scss', '.sass'],
  less: ['.less'],
  stylus: ['.styl', '.stylus'],
  sugarss: ['.sss'],
}

// Whether a declared Vite peer is one the test engine uses. A preprocessor is, in whichever block it is
// declared, while the member holds a stylesheet it compiles: a component spec imports that stylesheet through
// Vite. Any other peer is excused as a development tool only: in `dependencies` it ships to production, where
// nothing loads it through Vite, so an unused one there stays unused.
const isTestEnginePeer = (
  name: string,
  development: Readonly<Record<string, unknown>>,
  files: readonly string[],
): boolean => {
  const extensions: readonly string[] | undefined = STYLESHEET_PREPROCESSORS[name]
  return extensions === undefined
    ? Object.hasOwn(development, name)
    : files.some((file: string): boolean => extensions.some((extension: string): boolean => file.endsWith(extension)))
}

// Vite loads optional transforms through peers. A project supplying one is using it through its
// test engine, even when no application source imports the transform package directly.
const testEnginePeers = (member: Member, files: readonly string[]): readonly string[] => {
  const declared: Readonly<Record<string, string>> = declaredDependencies(member.packageJson)
  if (!Object.hasOwn(declared, 'vitest')) {
    return []
  }
  const development: Readonly<Record<string, unknown>> = asRecord(readKey(member.packageJson, 'devDependencies'))
  const own: readonly string[] = memberFiles(files, member.siblingPaths)
  try {
    const project: NodeJS.Require = createRequire(path.join(member.root, 'package.json'))
    const test: NodeJS.Require = createRequire(project.resolve('vitest/package.json'))
    const engine: unknown = readJson(test.resolve('vite/package.json'))
    return Object.keys(asRecord(readKey(engine, 'peerDependencies'))).filter(
      (name: string): boolean => Object.hasOwn(declared, name) && isTestEnginePeer(name, development, own),
    )
  } catch {
    // Missing test tools are reported by wiring; they cannot excuse a dependency here.
    return []
  }
}

// CloudFront provides its own module; no npm package supplies it. The exemption applies only when
// a declared CloudFront file actually exists in this member, including its sibling boundaries.
const runtimeModules = (member: Member, files: readonly string[]): readonly string[] =>
  allowedJavaScriptFiles(files, rolesFor(member.settings.javascriptRuntimes, CLOUDFRONT_RUNTIME), member.siblingPaths)
    .length > 0
    ? ['cloudfront']
    : []

// The issue types of the production run: the export kinds the first run judges, and the production file that only
// tests reach. Dependencies stay with the first run, which already judges every declared one.
const PRODUCTION_ISSUE_TYPES: string = 'files,exports,types,enumMembers,namespaceMembers'

const SOURCE_EXTENSIONS: string = [...TYPESCRIPT_SOURCE_EXTENSIONS, ...JAVASCRIPT_EXTENSIONS]
  .map((extension: string): string => extension.slice(1))
  .join(',')

// The files the production run judges: those under a production source root, with knip's production suffix. A
// member that declares itself whole as a root keeps its top-level files out, which are its tooling.
const productionProject = (member: Member): readonly string[] => [
  ...productionSourceRoots(member.settings.sourceRoots).map(
    (root: string): string => `${root === '.' ? '*' : root}/**/*.{${SOURCE_EXTENSIONS}}!`,
  ),
  ...NON_PRODUCTION_ROOTS.map((root: string): string => `!${root}/**`),
]

// The test runners' knip plugins, which in production mode contribute nothing but tests.
const TEST_RUNNER_PLUGINS: readonly string[] = ['playwright', 'vitest']

// A harness package ships end-to-end specifications from its sources, which the e2e gate runs in every browser
// application that installs it. They are production entry points of the package, so what only they reach is used.
// knip drops a production entry that a test runner's plugin reads as a test, so the production run of a harness
// package leaves those plugins out. A project cannot reach this: the wiring gate refuses a member that names
// itself after a harness package.
const isHarness = (member: Member): boolean => isHarnessPackage(asText(readKey(member.packageJson, 'name')))

// What the production run adds to the configuration every member gets: the production entry points knip cannot
// see, which are a harness package's shipped specifications and the files a stack layer's framework runs, and the
// plugins it leaves out. knip's production mode passes over a file a plugin reads as a tool's configuration, even
// one named as an entry, so a layer names the plugin that reads its framework's configuration that way.
const productionOverrides = (member: Member, entries: readonly string[]): Readonly<Record<string, unknown>> => {
  const shipped: readonly string[] = isHarness(member) ? [SHIPPED_SPEC_SOURCES] : []
  const plugins: readonly string[] = [
    ...(isHarness(member) ? TEST_RUNNER_PLUGINS : []),
    ...member.settings.knipConfigPlugins,
  ]
  return {
    entry: [
      ...entries,
      ...[...shipped, ...member.settings.productionEntries].map((pattern: string): string => `${pattern}!`),
    ],
    ...Object.fromEntries(plugins.map((plugin: string): readonly [string, boolean] => [plugin, false])),
  }
}

/** The configurations a run renders: the one every member gets, and the production one. */
interface RenderedConfigs {
  readonly directory: string
  readonly config: string
  readonly production: string
}

// Written outside the working tree, so the run leaves nothing the tree check would notice.
const knipConfigs = (member: Member, layers: readonly string[]): RenderedConfigs => {
  const shippedPath: string = path.join(harnessDirectory(), 'configs', 'knip.json')
  const shipped: Record<string, unknown> = asRecord(readJson(shippedPath))
  const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-knip-'))
  const files: readonly string[] = presentWorkingTreeFiles(member.root)
  const entries: readonly string[] = allowedJavaScriptFiles(
    files,
    member.settings.javascriptAllowlist,
    member.siblingPaths,
  )
  const boundaries: readonly string[] = member.siblingPaths.map((sibling: string): string => `${sibling}/**`)
  const config: Readonly<Record<string, unknown>> = {
    ...shipped,
    // A file a framework runs in production of its own accord is an entry point of every run, not only of the
    // production one: nothing imports it, so without this the run would report it unused.
    entry: [...entries, ...member.settings.productionEntries],
    ignoreDependencies: [
      ...texts(shipped['ignoreDependencies']),
      ...ownDependencies(member),
      ...declaredLayers(member, layers),
      ...testEnginePeers(member, files),
      ...runtimeModules(member, files),
    ],
    ignore: [...texts(shipped['ignore']), ...boundaries, ...member.settings.generatedArtefacts],
    // A workspace root's run would otherwise analyse every member as a workspace of its own, loading each
    // member's tool configurations without that member's environment. Each member is judged by its own run.
    ignoreWorkspaces: [...texts(shipped['ignoreWorkspaces']), ...member.siblingPaths],
  }
  const rendered: RenderedConfigs = {
    directory,
    config: path.join(directory, 'knip.json'),
    production: path.join(directory, 'knip-production.json'),
  }
  writeJson(rendered.config, config)
  writeJson(rendered.production, {
    ...config,
    ...productionOverrides(member, entries),
    project: productionProject(member),
  })
  return rendered
}

/** What one knip run is handed: the member, its rendered configurations and the environment its plugins load in. */
interface KnipRun {
  readonly member: Member
  readonly configs: RenderedConfigs
  readonly environment: Readonly<Record<string, string>>
}

const runKnip = (run: KnipRun, config: string, options: readonly string[]): RunResult =>
  runNode(resolveTool('knip'), ['--config', config, '--no-progress', ...options], {
    cwd: run.member.root,
    env: run.environment,
  })

const PRODUCTION_REPAIR: string =
  'Production run: these exports and production files are reached only from tests or tooling. Test each through ' +
  'the production code that uses it, or remove it.'

const productionResult = (run: KnipRun): GateResult => {
  const result: GateResult = fromRun(
    runKnip(run, run.configs.production, ['--production', '--include', PRODUCTION_ISSUE_TYPES]),
    'no export or production file only tests reach',
    'an export or a production file only tests reach was found',
  )
  return result.ok ? result : { ...result, findings: [PRODUCTION_REPAIR, ...result.findings] }
}

// The production entry points the project declares, each with the reason it gives.
const declaredEntries = (member: Member): readonly DeclaredExclusion[] =>
  member.settings.declaredExclusions.filter(
    (entry: DeclaredExclusion): boolean => entry.setting === PRODUCTION_ENTRIES_SETTING && isHonouredExclusion(entry),
  )

// A test is no production entry point: declaring one would count what only tests reach as production code.
const testEntries = (member: Member): readonly string[] => {
  const files: readonly string[] = memberFiles(presentWorkingTreeFiles(member.root), member.siblingPaths)
  return declaredEntries(member).flatMap((entry: DeclaredExclusion): readonly string[] =>
    files
      .filter((file: string): boolean => matchesGlob(entry.pattern, file) && isTestFile(file))
      .map(
        (file: string): string =>
          `${PRODUCTION_ENTRIES_SETTING} entry "${entry.pattern}" names the test ${file}; a test is no production ` +
          'entry point, so declare the production file that runs, never the test that exercises it',
      ),
  )
}

// Every entry point the project declares is printed with the verdict, so a reader sees what the production run
// counted as production beyond what knip found itself.
const withDeclaredEntries = (member: Member, result: GateResult): GateResult => {
  const entries: readonly string[] = declaredEntries(member).map(
    (entry: DeclaredExclusion): string => `${entry.pattern} (${entry.reason})`,
  )
  if (entries.length === 0) {
    return result
  }
  const listing: string = `production entry points the project declares: ${entries.join('; ')}`
  return withOutput({ ...result, summary: `${result.summary}; ${listing}` }, `${listing}\n${result.output ?? ''}`)
}

// Two failures as one verdict, each summary, finding and output kept in order.
const merged = (left: GateResult, right: GateResult): GateResult =>
  withOutput(
    failed(`${left.summary}; ${right.summary}`, [...left.findings, ...right.findings]),
    `${left.output ?? ''}${right.output ?? ''}`,
  )

// The whole run's verdict, joined by every other one that failed.
const combined = (whole: GateResult, others: readonly GateResult[]): GateResult =>
  others
    .filter((result: GateResult): boolean => !result.ok)
    .reduce((verdict: GateResult, next: GateResult): GateResult => (verdict.ok ? next : merged(verdict, next)), whole)

// A setting the harness no longer reads, which the project still declares as though it changed this gate.
const retiredVerdict = (member: Member): readonly GateResult[] => {
  const retired: readonly string[] = retiredSettings(member.packageJson)
  return retired.length > 0 ? [failed('the project declares a setting the harness no longer reads', retired)] : []
}

const verdict = (run: KnipRun): GateResult =>
  combined(
    fromRun(
      runKnip(run, run.configs.config, []),
      'no dead code or unused dependency',
      'dead code or an unused dependency was found',
    ),
    [productionResult(run), ...retiredVerdict(run.member)],
  )

/**
 * No unused file, export or dependency, and no export or production file that only tests and tooling reach. A
 * production entry point the project declares that names a test is refused, and so is a retired setting.
 * @param member the member to judge.
 * @param layers the packages counted as layers, the registered ones unless a caller names others.
 * @returns the verdict, naming every production entry point the project declares.
 */
export const knip = (member: Member, layers: readonly string[] = LAYER_NAMES): GateResult => {
  const refused: readonly string[] = testEntries(member)
  if (refused.length > 0) {
    return failed('a declared production entry point names a test', refused)
  }
  const configs: RenderedConfigs = knipConfigs(member, layers)
  const environment: Readonly<Record<string, string>> = analysisEnvironment(
    member.root,
    layerAnalysisEnvironmentFor(member.root, member.packageJson, readJson, layers),
  )
  try {
    return withDeclaredEntries(member, verdict({ member, configs, environment }))
  } finally {
    rmSync(configs.directory, { recursive: true, force: true })
  }
}
