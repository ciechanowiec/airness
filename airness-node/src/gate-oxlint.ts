// Native JavaScript and TypeScript rules, run by Oxlint under a closed rule set the harness generates.
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { type RunResult, runNode } from './exec.js'
import { failed, type GateResult, passed, withOutput } from './gate-result.js'
import { asRecord, asText } from './json-shapes.js'
import { isOxlintConfig } from './jsx-accessibility-scope.js'
import { oxlintConfig, oxlintRuleNames } from './oxlint-policy.js'
import { type OxlintLegacySite, oxlintArguments, oxlintReportProblems } from './oxlint-report.js'
import { type OxlintGroup, oxlintGroups, oxlintSourceFiles } from './oxlint-scope.js'
import { isEslintOwnedSuppression, oxlintSuppressionProblems, type SourceComment } from './oxlint-suppressions.js'
import { harnessDirectory, type Member, readJson, resolvedVersion, resolveTool, writeJson } from './project.js'
import { sourceComments } from './source-comments.js'
import { workingTreeFiles } from './working-tree.js'

const versionProblems = (): readonly string[] => {
  const manifestPath: string = path.join(harnessDirectory(), 'package.json')
  const manifest: Record<string, unknown> = asRecord(readJson(manifestPath))
  const expected: string = asText(asRecord(manifest['dependencies'])['oxlint'])
  return expected.length > 0 && resolvedVersion('oxlint') === expected
    ? []
    : [`Oxlint must resolve to the harness-owned version ${expected}`]
}

const eligibleFiles = (member: Member, inventory: readonly string[]): readonly string[] =>
  oxlintSourceFiles(inventory, member.settings.generatedArtefacts, member.siblingPaths).filter(
    (file: string): boolean => {
      const absolute: string = path.join(member.root, file)
      return existsSync(absolute) && statSync(absolute).isFile()
    },
  )

const suppressionProblems = (member: Member, group: OxlintGroup): readonly string[] =>
  group.files.flatMap((file: string): readonly string[] => {
    const text: string = readFileSync(path.join(member.root, file), 'utf8')
    return oxlintSuppressionProblems(sourceComments(text, file), oxlintRuleNames(group.rules)).map(
      (problem: string): string => `${file}: ${problem}`,
    )
  })

const legacySitesIn = (member: Member, files: readonly string[]): readonly OxlintLegacySite[] =>
  files.flatMap((file: string): readonly OxlintLegacySite[] => {
    const text: string = readFileSync(path.join(member.root, file), 'utf8')
    return sourceComments(text, file)
      .filter(isEslintOwnedSuppression)
      .map((comment: SourceComment): OxlintLegacySite => ({ file, line: comment.line }))
  })

const nativeVerdict = (result: RunResult, group: OxlintGroup, legacy: readonly OxlintLegacySite[]): GateResult => {
  const findings: readonly string[] = oxlintReportProblems(result.stdout, group.files.length, group.rules.length, {
    exitCode: result.code,
    output: result.output,
    legacy,
  })
  return withOutput(
    findings.length === 0
      ? passed(`${String(group.files.length)} source file(s) pass ${String(group.rules.length)} native rules`)
      : failed('Oxlint did not establish conformance', [...findings, ...(result.code === 0 ? [] : [result.output])]),
    result.output,
  )
}

const analyze = (member: Member, group: OxlintGroup): GateResult => {
  const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-oxlint-'))
  try {
    const config: string = path.join(directory, 'oxlint.json')
    writeJson(config, oxlintConfig(group.rules))
    return nativeVerdict(
      runNode(resolveTool('oxlint'), [...oxlintArguments(config, group.files)], { cwd: member.root }),
      group,
      legacySitesIn(member, group.files),
    )
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}

/** Run disjoint groups sequentially, preserving each invocation's measured coverage. */
const analyzeGroups = (member: Member, groups: readonly OxlintGroup[]): GateResult => {
  const [first, ...rest]: readonly OxlintGroup[] = groups
  if (first === undefined) {
    return passed('no eligible source remains to analyze')
  }
  const result: GateResult = analyze(member, first)
  if (!result.ok || rest.length === 0) {
    return result
  }
  const remaining: GateResult = analyzeGroups(member, rest)
  return withOutput(
    { ...remaining, summary: `${result.summary}; ${remaining.summary}` },
    [result.output, remaining.output].filter(Boolean).join('\n'),
  )
}

/** Native core checks for every member, with the accessibility rules over an application's JSX. */
export const oxlint = (member: Member): GateResult => {
  const inventory: readonly string[] = workingTreeFiles(member.root)
  const groups: readonly OxlintGroup[] = oxlintGroups(eligibleFiles(member, inventory), member.hasOwnRuntime)
  const problems: readonly string[] = [
    ...versionProblems(),
    ...inventory
      .filter((file: string): boolean => isOxlintConfig(file))
      .map((file: string): string => `${file}: Oxlint configuration is owned by Airness`),
    ...groups.flatMap((group: OxlintGroup): readonly string[] => suppressionProblems(member, group)),
  ]
  return problems.length > 0
    ? failed('Oxlint wiring or suppression policy is violated', problems)
    : analyzeGroups(member, groups)
}
