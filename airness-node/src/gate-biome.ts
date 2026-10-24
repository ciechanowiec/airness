// Formatting and fast lint, under the shipped Biome configuration, over the files the member owns.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { biomeIncludes, biomeSelection, isNestedBiomeConfig } from './biome-selection.js'
import { failed, fromRun, type GateResult, passed, runNode } from './exec.js'
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

/** Formatting and fast lint, bounded to the member's working-tree files. */
export const biome = (member: Member): GateResult => {
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
  const files: readonly string[] = biomeSelection(
    inventory,
    biomeIncludes(member.settings.sourceRoots, member.settings.generatedArtefacts),
    member.siblingPaths,
  )
  if (files.length === 0) {
    return passed('no owned file is selected for Biome')
  }
  return fromRun(
    runNode(
      resolveTool('@biomejs/biome', 'biome'),
      ['check', '--error-on-warnings', '--config-path', shippedConfig(), ...files],
      { cwd: member.root },
    ),
    `Biome reports no formatting or lint defect in ${String(files.length)} file(s)`,
    'Biome reports a formatting or lint defect',
  )
}
