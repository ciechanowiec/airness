// Dead code: unused files, exports and dependencies, under the shipped knip configuration, rendered per
// member with the member's nested members as boundaries and its allowlisted JavaScript as entry points. The
// shipped configuration counts @vitest/coverage-v8 as used, because the tests gate loads it by the provider
// name of the shipped Vitest configuration, which knip cannot see from a member that keeps no copy of it, and a
// declared stack layer package as used, because the harness reads it by name: node-gate runs its gates and the
// layer seams read its defaults, ESLint blocks and end-to-end specifications, none of which an import shows.
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { localEnvironment } from './environment-files.js'
import { fromRun, type GateResult, runNode } from './exec.js'
import { HARNESS_PACKAGE } from './harness-packages.js'
import { CLOUDFRONT_RUNTIME, rolesFor } from './javascript-runtimes.js'
import { asRecord, asText, declaredDependencies, readKey } from './json-shapes.js'
import { LAYER_NAMES } from './layer.js'
import { harnessDirectory, type Member, readJson, resolveTool } from './project.js'
import { allowedJavaScriptFiles } from './source-selection.js'
import { presentWorkingTreeFiles } from './working-tree.js'

const JSON_INDENT: number = 2

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

// Written outside the working tree, so the run leaves nothing the tree check would notice.
const knipConfig = (member: Member, layers: readonly string[]): string => {
  const shippedPath: string = path.join(harnessDirectory(), 'configs', 'knip.json')
  const shipped: Record<string, unknown> = asRecord(readJson(shippedPath))
  const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-knip-'))
  const rendered: string = path.join(directory, 'knip.json')
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
  }
  writeFileSync(rendered, `${JSON.stringify(config, null, JSON_INDENT)}\n`)
  return rendered
}

/**
 * No unused file, export or dependency.
 * @param member the member to judge.
 * @param layers the packages counted as layers, the registered ones unless a caller names others.
 * @returns the verdict.
 */
export const knip = (member: Member, layers: readonly string[] = LAYER_NAMES): GateResult => {
  const config: string = knipConfig(member, layers)
  try {
    return fromRun(
      runNode(resolveTool('knip'), ['--config', config, '--no-progress'], {
        cwd: member.root,
        env: localEnvironment(member.root),
      }),
      'no dead code or unused dependency',
      'dead code or an unused dependency was found',
    )
  } finally {
    rmSync(path.dirname(config), { recursive: true, force: true })
  }
}
