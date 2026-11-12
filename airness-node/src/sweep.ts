// The passes of the managed browser sweep. The pinned spec calls one of these per test, so each part of
// a pass has its own budget and one failing part hides nothing another would have reported.
//
// Discovery runs once, in Chromium, as the setup project every pass depends on, and writes what it
// found - routes, stories, breakpoints, colour schemes, control counts - to the ignored output
// directory. Every pass is split into shards that run as separate tests on parallel workers; each
// reads that file instead of crawling again, and writes its own findings and coverage under its
// engine's directory. The summary is the teardown: it runs after every part of every engine has
// finished, whatever their outcome, judges discovery's problems and the exemptions no part used, and
// prints the coverage. A part that did not finish is reported there too, so reduced coverage never
// reads as a full pass.
//
// Nothing here decides what a defect is. Each verdict is a the pure policy modules function over what the
// browser measured; the `sweep-*` modules drive the browser into each state and hand over the
// measurements. This module is the entry point: it holds the summary and re-exports every pass.
import { existsSync, readdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { type APIRequestContext, type APIResponse, expect, request, type TestInfo, test } from '@playwright/test'
import { unsweptRoutes } from './a11y.js'
import type { TemplateSample } from './browser-discovery.js'
import { type DeclaredExemption, staleExemptions, type UsedExemption } from './browser-exemptions.js'
import { type BrowserFinding, distinctFindings, findingLine } from './browser-findings.js'
import { BROWSER_ENGINES, enginePassParts, type Shard, type SweepPass } from './browser-run.js'
import { coversEveryEngine } from './change-scope-policy.js'
import { writeJson } from './project.js'
import { projectDirectory, projectSettings } from './project-settings.js'
import { completedScanRoutes } from './scan-evidence.js'
import { SCROLLING_RULES } from './scroll-policy.js'
import {
  type Discovery,
  type DiscoveryReport,
  discoveryFile,
  discoveryReportFile,
  isDiscovery,
  isDiscoveryReport,
  isPassReport,
  type PassReport,
  readJson,
  reportFile,
  reportName,
  sweepRoot,
} from './sweep-shared.js'
import { endedSessionProblems, hasSignedInVisitor, sessionFile, type Visitor } from './sweep-visitors.js'
import { SWEEP_WORKERS as SWEEP_SHARDS } from './sweep-workers.js'

export { expectDisclosureStates } from './sweep-disclosures.js'
export { expectDiscoveredSite } from './sweep-discovery.js'
export { expectInteractionStates } from './sweep-interaction.js'
export { expectTextStress, expectWidthSweep } from './sweep-reflow.js'
export {
  expectDarkScheme,
  expectEngineRules,
  expectRestingRules,
} from './sweep-resting.js'
export { expectScrolling } from './sweep-scrolling.js'
export type { Shard } from './sweep-shared.js'
export { SWEEP_WORKERS as SWEEP_SHARDS } from './sweep-workers.js'

const CHROMIUM: string = 'chromium'

const CHROMIUM_PASSES: readonly SweepPass[] = [
  'resting',
  'interaction',
  'disclosures',
  'text-stress',
  'width-sweep',
  'dark-scheme',
  'scrolling',
]

const ENGINE_PASSES: readonly SweepPass[] = ['engine']

// The engine pass runs in as many parts as its engine affords; every other pass in all of them.
const shardsOf = (engine: string, pass: SweepPass): readonly Shard[] => {
  const count: number = pass === 'engine' ? enginePassParts(engine, SWEEP_SHARDS) : SWEEP_SHARDS
  return Array.from({ length: count }, (_unused: unknown, index: number): Shard => ({ index, count }))
}

/** One part of a pass the summary expects a report from. */
interface ExpectedReport {
  readonly engine: string
  readonly pass: SweepPass
  readonly shard: Shard
}

const presentEngines = (info: TestInfo): readonly string[] => {
  try {
    return readdirSync(sweepRoot(info), { withFileTypes: true }).flatMap((entry): readonly string[] =>
      entry.isDirectory() && BROWSER_ENGINES.includes(entry.name) ? [entry.name] : [],
    )
  } catch {
    return []
  }
}

// Chromium always runs; the other engines run when the pinned browsers do outside a `delta` run's
// change scope, and whatever ran in them is judged even when the endpoint is no longer named.
const expectedEngines = (info: TestInfo): readonly string[] => {
  const isEveryEngine: boolean = coversEveryEngine(process.env)
  const present: ReadonlySet<string> = new Set<string>(presentEngines(info))
  return BROWSER_ENGINES.filter(
    (engine: string): boolean => engine === CHROMIUM || isEveryEngine || present.has(engine),
  )
}

const expectedReports = (engines: readonly string[]): readonly ExpectedReport[] =>
  engines.flatMap((engine: string): readonly ExpectedReport[] =>
    (engine === CHROMIUM ? CHROMIUM_PASSES : ENGINE_PASSES).flatMap((pass: SweepPass): readonly ExpectedReport[] =>
      shardsOf(engine, pass).map((shard: Shard): ExpectedReport => ({ engine, pass, shard })),
    ),
  )

const visitorText = (visitor: Visitor): string => (visitor === 'signed-in' ? ' signed in' : '')

const missingLine = (expected: ExpectedReport, visitor: Visitor): string =>
  `${expected.engine} ${expected.pass}${visitorText(visitor)} part ${String(expected.shard.index + 1)} of ` +
  `${String(expected.shard.count)}: did not record ${reportName(expected.pass, expected.shard)}`

const MS_PER_SECOND: number = 1000

const NOTHING_DISCOVERED: Discovery = {
  routes: [],
  stories: [],
  locations: [],
  samples: [],
}

const NO_DISCOVERY_REPORT: DiscoveryReport = {
  visitedRoutes: [],
  answeredRoutes: [],
  fragmentRoutes: [],
  findings: [],
  problems: [],
  places: 0,
  locations: [],
  elapsedMs: 0,
  scope: { isScoped: false, lines: [] },
}

const discoveryText = (discovery: Discovery, report: DiscoveryReport): string =>
  `discovery (${CHROMIUM}): ${String(discovery.routes.length)} routes, ${String(discovery.stories.length)} stories, ` +
  `${String(discovery.samples.length)} templates, ${String(report.findings.length)} findings, ` +
  `${String(Math.round(report.elapsedMs / MS_PER_SECOND))}s`

// One line per pass in each engine: the places and findings of its parts summed, the locations they
// covered, and the slowest part, which is how long the pass held the run. A pass a change scope narrowed
// says so, because its coverage is not complete.
const passLine = (engine: string, pass: SweepPass, reports: readonly PassReport[], isScoped: boolean): string => {
  const parts: readonly PassReport[] = reports.filter(
    (report: PassReport): boolean => report.engine === engine && report.pass === pass,
  )
  const places: number = parts.reduce((total: number, report: PassReport): number => total + report.places, 0)
  const findings: number = parts.reduce(
    (total: number, report: PassReport): number => total + report.findings.length,
    0,
  )
  const locations: number = new Set<string>(parts.flatMap((report: PassReport): readonly string[] => report.locations))
    .size
  const slowestMs: number = Math.max(0, ...parts.map((report: PassReport): number => report.elapsedMs))
  return (
    `${pass} (${engine}${isScoped ? ', scoped to the change' : ''}): ${String(places)} places over ` +
    `${String(locations)} locations, ${String(findings)} ` +
    `findings, ${String(parts.length)} of ${String(shardsOf(engine, pass).length)} parts, slowest part ` +
    `${String(Math.round(slowestMs / MS_PER_SECOND))}s`
  )
}

/** Everything the summary read of one visitor's sweep. */
interface SweepRecord {
  readonly visitor: Visitor
  readonly discovery: Discovery | undefined
  readonly discoveryReport: DiscoveryReport | undefined
  readonly engines: readonly string[]
  readonly reports: readonly PassReport[]
  readonly missing: readonly string[]
}

const visitorCoverage = (record: SweepRecord): readonly string[] =>
  [
    ...(record.discoveryReport ?? NO_DISCOVERY_REPORT).scope.lines.map((line: string): string => `scope: ${line}`),
    discoveryText(record.discovery ?? NOTHING_DISCOVERED, record.discoveryReport ?? NO_DISCOVERY_REPORT),
    ...(record.discovery?.samples ?? []).map(
      (sample: TemplateSample): string =>
        `  template ${sample.template}: sampled by ${sample.route} (${String(sample.routes)} routes)`,
    ),
    ...record.engines.flatMap((engine: string): readonly string[] =>
      (engine === CHROMIUM ? CHROMIUM_PASSES : ENGINE_PASSES).map((pass: SweepPass): string =>
        passLine(engine, pass, record.reports, record.discoveryReport?.scope.isScoped ?? false),
      ),
    ),
    ...[...new Set<string>(record.reports.flatMap((report: PassReport): readonly string[] => report.notes))].map(
      (note: string): string => `  note: ${note}`,
    ),
    ...record.missing,
  ].map((line: string): string => (record.visitor === 'signed-in' ? `signed in: ${line}` : line))

const coverageText = (records: readonly SweepRecord[]): string =>
  records.flatMap((record: SweepRecord): readonly string[] => visitorCoverage(record)).join('\n')

// An exemption is stale in an engine when no part of any pass in that engine used it. The scrolling pass's
// rules are judged on one route per template, so at a route it did not cross they were never applied, and an
// exemption there cannot have proved itself and is not judged stale for them. Every other rule keeps the
// stricter reading: judged anywhere in the engine, it must excuse something.
const staleIn = (engine: string, reports: readonly PassReport[]): readonly BrowserFinding[] => {
  const own: readonly PassReport[] = reports.filter((report: PassReport): boolean => report.engine === engine)
  const isJudgedAt = (location: string, rule: string): boolean =>
    !SCROLLING_RULES.includes(rule) ||
    own.some((report: PassReport): boolean => report.judgedRules.includes(rule) && report.locations.includes(location))
  return own.length === 0
    ? []
    : staleExemptions(
        own
          .flatMap((report: PassReport): readonly DeclaredExemption[] => report.declared)
          .map(
            (entry: DeclaredExemption): DeclaredExemption => ({
              ...entry,
              rules: entry.rules.filter((rule: string): boolean => isJudgedAt(entry.location, rule)),
            }),
          ),
        own.flatMap((report: PassReport): readonly UsedExemption[] => report.used),
        [...new Set<string>(own.flatMap((report: PassReport): readonly string[] => report.judgedRules))],
        engine,
      )
}

const readRecord = (info: TestInfo, visitor: Visitor): SweepRecord => {
  const engines: readonly string[] = expectedEngines(info)
  const expected: readonly ExpectedReport[] = expectedReports(engines)
  const read: readonly (PassReport | undefined)[] = expected.map((part: ExpectedReport): PassReport | undefined =>
    readJson(reportFile(info, { ...part, visitor }), isPassReport),
  )
  return {
    visitor,
    discovery: readJson(discoveryFile(info, visitor), isDiscovery),
    discoveryReport: readJson(discoveryReportFile(info, visitor), isDiscoveryReport),
    engines,
    reports: read.flatMap((report: PassReport | undefined): readonly PassReport[] =>
      report === undefined ? [] : [report],
    ),
    missing: expected.flatMap((part: ExpectedReport, index: number): readonly string[] =>
      read[index] === undefined ? [missingLine(part, visitor)] : [],
    ),
  }
}

const recordProblems = (record: SweepRecord): readonly string[] => {
  const problems: readonly string[] =
    record.discovery === undefined || record.discoveryReport === undefined
      ? ['discovery did not record what to scan; its own test names the reason']
      : record.discoveryReport.problems
  return problems.map((problem: string): string => (record.visitor === 'signed-in' ? `signed in: ${problem}` : problem))
}

const everyRecordProblem = (records: readonly SweepRecord[]): readonly string[] =>
  records.flatMap((record: SweepRecord): readonly string[] => recordProblems(record))

const routesOf = (
  records: readonly SweepRecord[],
  routes: (report: DiscoveryReport) => readonly string[],
): readonly string[] =>
  records.flatMap((record: SweepRecord): readonly string[] =>
    record.discoveryReport === undefined ? [] : routes(record.discoveryReport),
  )

// A route either visitor reached is covered: the signed-in visit judged it with every pass as the anonymous
// one judges a public page.
const coverageProblems = (records: readonly SweepRecord[], info: TestInfo): readonly string[] =>
  unsweptRoutes(
    routesOf(records, (report: DiscoveryReport): readonly string[] => report.visitedRoutes),
    routesOf(records, (report: DiscoveryReport): readonly string[] => report.answeredRoutes),
    completedScanRoutes(info.project.outputDir, new URL(projectSettings.serverUrl).origin),
    routesOf(records, (report: DiscoveryReport): readonly string[] => report.fragmentRoutes),
  )

const probeStatus = async (context: APIRequestContext, probe: string): Promise<number | undefined> => {
  try {
    const response: APIResponse<unknown> = await context.get(new URL(probe, projectSettings.serverUrl).href, {
      maxRedirects: 0,
    })
    return response.status()
  } catch {
    return undefined
  }
}

// The signed-in passes share one session, so one that ended it left every later signed-in visit without it.
// The route that proved the session at discovery is asked again in the stored session once every pass ran.
const endedSession = async (records: readonly SweepRecord[], info: TestInfo): Promise<readonly string[]> => {
  const probe: string | undefined = records.find((record: SweepRecord): boolean => record.visitor === 'signed-in')
    ?.discoveryReport?.probeRoute
  if (probe === undefined) {
    return []
  }
  const context: APIRequestContext = await request.newContext({ storageState: sessionFile(info.project.outputDir) })
  try {
    return endedSessionProblems(probe, await probeStatus(context, probe)).map(
      (problem: string): string => `signed in: ${problem}`,
    )
  } finally {
    await context.dispose()
  }
}

// Without the anonymous discovery there is nothing to judge coverage against, so its absence is the verdict.
const summaryProblems = async (records: readonly SweepRecord[], info: TestInfo): Promise<readonly string[]> =>
  records.some((record: SweepRecord): boolean => record.visitor === 'anonymous' && record.discoveryReport === undefined)
    ? everyRecordProblem(records)
    : [...everyRecordProblem(records), ...(await endedSession(records, info)), ...coverageProblems(records, info)]

const VISITORS: readonly Visitor[] = hasSignedInVisitor(projectDirectory, existsSync)
  ? ['anonymous', 'signed-in']
  : ['anonymous']

// What report.json says of each part of a pass, with the visitor it visited as.
const passEntries = (records: readonly SweepRecord[]): readonly Readonly<Record<string, unknown>>[] =>
  records.flatMap((record: SweepRecord): readonly Readonly<Record<string, unknown>>[] =>
    record.reports.map(
      (report: PassReport): Readonly<Record<string, unknown>> => ({
        visitor: record.visitor,
        pass: report.pass,
        engine: report.engine,
        shard: report.shard,
        places: report.places,
        locations: report.locations,
        notes: report.notes,
        elapsedMs: report.elapsedMs,
      }),
    ),
  )

/**
 * Judge discovery's problems and findings and the exemptions no part of a pass used, write the report
 * and the coverage summary, and fail when a part of a pass did not finish. It runs as the sweep's
 * teardown, after every part of every engine, whatever their outcome.
 */
export const expectSweepSummary = async (): Promise<void> => {
  const info: TestInfo = test.info()
  const records: readonly SweepRecord[] = VISITORS.map((visitor: Visitor): SweepRecord => readRecord(info, visitor))
  const problems: readonly string[] = await summaryProblems(records, info)
  const discoveryFindings: readonly BrowserFinding[] = distinctFindings(
    records.flatMap((record: SweepRecord): readonly BrowserFinding[] => record.discoveryReport?.findings ?? []),
  )
  const reports: readonly PassReport[] = records.flatMap((record: SweepRecord): readonly PassReport[] => record.reports)
  const missing: readonly string[] = records.flatMap((record: SweepRecord): readonly string[] => record.missing)
  // An exemption proves itself when either visitor's passes used it, because both judge the same pages.
  const stale: readonly BrowserFinding[] = distinctFindings(
    (records[0]?.engines ?? []).flatMap((engine: string): readonly BrowserFinding[] => staleIn(engine, reports)),
  )
  const coverage: string = coverageText(records)
  writeJson(path.join(sweepRoot(info, 'anonymous'), 'report.json'), {
    findings: distinctFindings([
      ...discoveryFindings,
      ...reports.flatMap((report: PassReport): readonly BrowserFinding[] => report.findings),
      ...stale,
    ]),
    problems,
    scope: records[0]?.discoveryReport?.scope,
    passes: passEntries(records),
    missing,
  })
  writeFileSync(path.join(sweepRoot(info, 'anonymous'), 'coverage.txt'), `${coverage}\n`)
  await info.attach('coverage', { body: coverage, contentType: 'text/plain' })
  process.stdout.write(`\nAirness sweep coverage:\n${coverage}\n`)
  expect.soft(problems, 'discovery is incomplete').toEqual([])
  expect
    .soft(
      discoveryFindings.map((finding: BrowserFinding): string => findingLine(finding)),
      'discovery findings',
    )
    .toEqual([])
  expect.soft(missing, 'sweep parts that did not finish; their own tests name the reason').toEqual([])
  expect(
    stale.map((finding: BrowserFinding): string => findingLine(finding)),
    'exemptions no pass used',
  ).toEqual([])
}
