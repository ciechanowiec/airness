// Dead code: unused files, exports and dependencies, under the shipped knip configuration, rendered per
// member with the member's nested members as boundaries and its allowlisted JavaScript as entry points. The
// shipped configuration counts @vitest/coverage-v8 as used, because the tests gate loads it by the provider
// name of the shipped Vitest configuration, which knip cannot see from a member that keeps no copy of it, and a
// declared stack layer package as used, because the harness reads it by name: node-gate runs its gates and the
// layer seams read its defaults, ESLint blocks and end-to-end specifications, none of which an import shows.
//
// That run counts every test and tool as an entry point, so an export only a test imports reads as used. A member
// that declares `productionDeadCode` gets a second, production run: knip follows the production entry points
// alone, the package's declared public interface among them, and judges the exports and files under the production
// source roots, so an export or a production file that only tests and tooling reach fails too.
import { mkdtempSync, rmSync } from 'node:fs'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { localEnvironment } from './environment-files.js'
import { fromRun, type RunResult, runNode } from './exec.js'
import {
  JAVASCRIPT_EXTENSIONS,
  NON_PRODUCTION_ROOTS,
  productionSourceRoots,
  TYPESCRIPT_SOURCE_EXTENSIONS,
} from './file-roles.js'
import { failed, type GateResult, withOutput } from './gate-result.js'
import { HARNESS_PACKAGE, isHarnessPackage, SHIPPED_SPEC_SOURCES } from './harness-packages.js'
import { CLOUDFRONT_RUNTIME, rolesFor } from './javascript-runtimes.js'
import { asRecord, asText, declaredDependencies, readKey } from './json-shapes.js'
import { LAYER_NAMES } from './layer.js'
import { harnessDirectory, type Member, readJson, resolveTool, writeJson } from './project.js'
import { allowedJavaScriptFiles } from './source-selection.js'
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

// Vite loads optional transforms through peers. A project supplying one is using it through its
// test engine, even when no application source imports the transform package directly.
const testEnginePeers = (member: Member): readonly string[] => {
  const declared: Readonly<Record<string, string>> = declaredDependencies(member.packageJson)
  if (!Object.hasOwn(declared, 'vitest')) {
    return []
  }
  try {
    const project: NodeJS.Require = createRequire(path.join(member.root, 'package.json'))
    const test: NodeJS.Require = createRequire(project.resolve('vitest/package.json'))
    const engine: unknown = readJson(test.resolve('vite/package.json'))
    return Object.keys(asRecord(readKey(engine, 'peerDependencies'))).filter((name: string): boolean =>
      Object.hasOwn(declared, name),
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

// A harness package ships end-to-end specifications from its sources, which the e2e gate runs in every browser
// application that installs it. They are production entry points of the package, so what only they reach is used.
// knip drops a production entry that a test runner's plugin reads as a test, so the production run of a harness
// package leaves the Playwright and Vitest plugins out, which in production mode contribute nothing but tests. A
// project cannot reach this: the wiring gate refuses a member that names itself after a harness package.
const shippedSpecifications = (member: Member, entries: readonly string[]): Readonly<Record<string, unknown>> =>
  isHarnessPackage(asText(readKey(member.packageJson, 'name')))
    ? { entry: [...entries, `${SHIPPED_SPEC_SOURCES}!`], playwright: false, vitest: false }
    : {}

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
    entry: entries,
    ignoreDependencies: [
      ...texts(shipped['ignoreDependencies']),
      ...ownDependencies(member),
      ...declaredLayers(member, layers),
      ...testEnginePeers(member),
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
    ...shippedSpecifications(member, entries),
    project: productionProject(member),
  })
  return rendered
}

const runKnip = (member: Member, config: string, options: readonly string[]): RunResult =>
  runNode(resolveTool('knip'), ['--config', config, '--no-progress', ...options], {
    cwd: member.root,
    env: localEnvironment(member.root),
  })

const PRODUCTION_REPAIR: string =
  'Production run: these exports and production files are reached only from tests or tooling. Test each through ' +
  'the production code that uses it, or remove it.'

const productionResult = (member: Member, configs: RenderedConfigs): GateResult => {
  const result: GateResult = fromRun(
    runKnip(member, configs.production, ['--production', '--include', PRODUCTION_ISSUE_TYPES]),
    'no export or production file only tests reach',
    'an export or a production file only tests reach was found',
  )
  return result.ok ? result : { ...result, findings: [PRODUCTION_REPAIR, ...result.findings] }
}

/**
 * No unused file, export or dependency, and under `productionDeadCode` no export or production file that only
 * tests and tooling reach.
 * @param member the member to judge.
 * @param layers the packages counted as layers, the registered ones unless a caller names others.
 * @returns the verdict.
 */
export const knip = (member: Member, layers: readonly string[] = LAYER_NAMES): GateResult => {
  const configs: RenderedConfigs = knipConfigs(member, layers)
  try {
    const whole: GateResult = fromRun(
      runKnip(member, configs.config, []),
      'no dead code or unused dependency',
      'dead code or an unused dependency was found',
    )
    if (!member.settings.productionDeadCode) {
      return whole
    }
    const production: GateResult = productionResult(member, configs)
    if (production.ok) {
      return whole
    }
    return whole.ok
      ? production
      : withOutput(
          failed(`${whole.summary}; ${production.summary}`, [...whole.findings, ...production.findings]),
          [whole.output, production.output].join(''),
        )
  } finally {
    rmSync(configs.directory, { recursive: true, force: true })
  }
}
