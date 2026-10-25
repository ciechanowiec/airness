// Dead code: unused files, exports and dependencies, under the shipped knip configuration, rendered per
// member with the member's nested members as boundaries and its allowlisted JavaScript as entry points. The
// shipped configuration counts @vitest/coverage-v8 as used, because the tests gate loads it by the provider
// name of the shipped Vitest configuration, which knip cannot see from a member that keeps no copy of it, and a
// declared stack layer package as used, because the harness reads it by name: node-gate runs its gates and the
// layer seams read its defaults, ESLint blocks and end-to-end specifications, none of which an import shows.
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fromRun, type GateResult, runNode } from './exec.js'
import { HARNESS_PACKAGE } from './harness-packages.js'
import { asRecord, asText, declaredDependencies, readKey } from './json-shapes.js'
import { LAYER_PACKAGES } from './layer.js'
import { harnessDirectory, type Member, readJson, resolveTool } from './project.js'
import { allowedJavaScriptFiles } from './source-selection.js'
import { presentWorkingTreeFiles } from './working-tree.js'

const JSON_INDENT: number = 2

// The harness itself reaches these by name, through the manifest resolution the gates share, which knip
// cannot follow; and it runs its own gates through its link to itself. Only the harness package gets this.
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
]

// The entries of a list the shipped configuration holds; a missing or misshapen one holds none.
const texts = (value: unknown): readonly string[] =>
  [value].flat().filter((entry: unknown): entry is string => typeof entry === 'string')

const ownDependencies = (member: Member): readonly string[] =>
  asText(readKey(member.packageJson, 'name')) === HARNESS_PACKAGE ? RESOLVED_BY_NAME : []

// Only the layer packages the harness names, and only those the member declares: no name a project chooses can
// hide an unused dependency this way.
const declaredLayers = (member: Member): readonly string[] => {
  const declared: Readonly<Record<string, string>> = declaredDependencies(member.packageJson)
  return LAYER_PACKAGES.filter((name: string): boolean => Object.hasOwn(declared, name))
}

// Written outside the working tree, so the run leaves nothing the tree check would notice.
const knipConfig = (member: Member): string => {
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
      ...declaredLayers(member),
    ],
    ignore: boundaries,
  }
  writeFileSync(rendered, `${JSON.stringify(config, null, JSON_INDENT)}\n`)
  return rendered
}

/** No unused file, export or dependency. */
export const knip = (member: Member): GateResult => {
  const config: string = knipConfig(member)
  try {
    return fromRun(
      runNode(resolveTool('knip'), ['--config', config, '--no-progress'], { cwd: member.root }),
      'no dead code or unused dependency',
      'dead code or an unused dependency was found',
    )
  } finally {
    rmSync(path.dirname(config), { recursive: true, force: true })
  }
}
