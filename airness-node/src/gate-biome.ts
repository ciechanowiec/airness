// Formatting and fast lint, under the shipped Biome configuration, over the files the member owns.
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { biomeIncludes, biomeSelection, isNestedBiomeConfig, isServerTemplate } from './biome-selection.js'
import { failed, fromRun, type GateResult, passed, runNode } from './exec.js'
import { isNextApp } from './facets.js'
import { asRecord } from './json-shapes.js'
import { harnessDirectory, type Member, resolvedVersion, resolveTool } from './project.js'
import { presentWorkingTreeFiles } from './working-tree.js'

const SCHEMA_PATTERN: RegExp = /biomejs\.dev\/schemas\/(\d+\.\d+\.\d+)\/schema\.json/u

const shippedConfig = (): string => path.join(harnessDirectory(), 'configs', 'airness-biome.json')

// Biome reports a schema mismatch only as a non-failing notice, so a Biome version other than the one
// the shipped configuration was written for would otherwise pass silently, whether an override in the
// project or a harness release that forgot to move the schema caused it.
/**
 * Whether the Biome that runs is the one a configuration's `$schema` was written for.
 * @param installed the version of Biome that runs.
 * @param config the text of the configuration.
 * @returns one problem when the versions differ, or none.
 */
export const biomeSchemaProblems = (installed: string, config: string): readonly string[] => {
  const declared: string | undefined = SCHEMA_PATTERN.exec(config)?.[1]
  return declared === installed
    ? []
    : [`the Airness Biome configuration is written for Biome ${declared ?? 'unknown'}, but Biome ${installed} runs`]
}

const schemaProblems = (): readonly string[] =>
  biomeSchemaProblems(resolvedVersion('@biomejs/biome'), readFileSync(shippedConfig(), 'utf8'))

const JSON_INDENT: number = 2

// The Next.js facet adds Biome's React and Next.js rule domains to the shipped configuration, rendered under the
// member's node_modules, which the tree ignores, so the project still holds no Biome configuration of its own.
// Not in the system's temporary directory: Biome roots its project scan at the configuration's directory, and
// rooted there it reported nothing at all, the import-cycle rules of the project domain included.
const NEXT_DOMAINS: Readonly<Record<string, string>> = { next: 'all', react: 'all' }

/**
 * The shipped Biome configuration with the Next.js facet's rule domains added.
 * @param shipped the shipped configuration's text.
 * @returns the configuration a Next.js application is checked with.
 */
export const nextBiomeConfig = (shipped: string): Readonly<Record<string, unknown>> => {
  const config: Record<string, unknown> = asRecord(JSON.parse(shipped))
  const linter: Record<string, unknown> = asRecord(config['linter'])
  return { ...config, linter: { ...linter, domains: { ...asRecord(linter['domains']), ...NEXT_DOMAINS } } }
}

const check = (member: Member, files: readonly string[], configPath: string, mode: 'check' | 'write'): GateResult =>
  fromRun(
    runNode(
      resolveTool('@biomejs/biome', 'biome'),
      ['check', ...(mode === 'write' ? ['--write'] : []), '--error-on-warnings', '--config-path', configPath, ...files],
      { cwd: member.root },
    ),
    `Biome reports no formatting or lint defect in ${String(files.length)} file(s)`,
    'Biome reports a formatting or lint defect',
  )

const checkNext = (member: Member, files: readonly string[], mode: 'check' | 'write'): GateResult => {
  mkdirSync(path.join(member.root, 'node_modules', '.airness'), { recursive: true })
  const directory: string = mkdtempSync(path.join(member.root, 'node_modules', '.airness', 'biome-'))
  try {
    const config: string = path.join(directory, 'biome.json')
    const shipped: string = readFileSync(shippedConfig(), 'utf8')
    writeFileSync(config, `${JSON.stringify(nextBiomeConfig(shipped), null, JSON_INDENT)}\n`)
    return check(member, files, config, mode)
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}

const readMarkup = (member: Member, file: string): string => readFileSync(path.join(member.root, file), 'utf8')

/** Formatting and fast lint, bounded to the member's working-tree files. */
const biomeRun = (member: Member, mode: 'check' | 'write'): GateResult => {
  const inventory: readonly string[] = presentWorkingTreeFiles(member.root)
  const problems: readonly string[] = [
    ...schemaProblems(),
    ...inventory
      .filter((file: string): boolean => isNestedBiomeConfig(file))
      .map((file: string): string => `${file}: Biome configuration is owned by Airness; remove it`),
  ]
  if (problems.length > 0) {
    return failed('Biome wiring is violated', problems)
  }
  // Server templates are left to the checks that understand their engine; see isServerTemplate.
  const files: readonly string[] = biomeSelection(
    inventory,
    biomeIncludes(member.settings.sourceRoots, member.settings.generatedArtefacts),
    member.siblingPaths,
  ).filter((file: string): boolean => !isServerTemplate(file, (markup: string): string => readMarkup(member, markup)))
  if (files.length === 0) {
    return passed('no owned file is selected for Biome')
  }
  return isNextApp(member) ? checkNext(member, files, mode) : check(member, files, shippedConfig(), mode)
}

/** Judge formatting without changing the working tree. */
export const biome = (member: Member): GateResult => biomeRun(member, 'check')

/** Apply Biome's safe fixes to exactly the files the gate judges. */
export const biomeWrite = (member: Member): GateResult => biomeRun(member, 'write')
