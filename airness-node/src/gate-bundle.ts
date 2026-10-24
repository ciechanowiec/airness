// The Next.js facet's client bundle budget, measured on the build the build gate produced from the tree as it
// is now, in the directory the member's own Next configuration names, because a project may move `distDir`.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { gzipSync } from 'node:zlib'
import { type BundleFile, type BundleReport, evaluateBundle } from './bundle-budget.js'
import { failed, type GateResult, notApplicable, passed } from './exec.js'
import { isNextApp } from './facets.js'
import { currentBuild, type StampedBuild } from './next-build.js'
import type { Member } from './project.js'

const BYTES_PER_KIB: number = 1024
const KIB_DECIMALS: number = 1
const GZIP_LEVEL: number = 9

const asKiB = (bytes: number): string => `${(bytes / BYTES_PER_KIB).toFixed(KIB_DECIMALS)} KiB`

const collectJavaScript = (directory: string): readonly string[] =>
  readdirSync(directory, { withFileTypes: true }).flatMap((entry): readonly string[] => {
    const full: string = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      return collectJavaScript(full)
    }
    return entry.name.endsWith('.js') ? [full] : []
  })

const measure = (member: Member, staticDirectory: string): GateResult => {
  if (!existsSync(staticDirectory)) {
    return failed('the production build output is missing', [
      `${staticDirectory} does not exist; the build gate runs before this one`,
    ])
  }
  const files: readonly BundleFile[] = collectJavaScript(staticDirectory).map(
    (file: string): BundleFile => ({
      path: file,
      gzipBytes: gzipSync(readFileSync(file), { level: GZIP_LEVEL }).length,
    }),
  )
  const report: BundleReport = evaluateBundle(files, member.settings.bundleBudgetBytes)
  const summary: string =
    `client JavaScript ${asKiB(report.totalGzipBytes)} gzipped across ${String(report.fileCount)} file(s) ` +
    `(budget ${asKiB(report.budgetBytes)})`
  return report.isWithinBudget
    ? passed(summary)
    : failed(`${summary} is over budget`, [
        "investigate the added weight; the ceiling is the harness's, and a project may only lower it",
      ])
}

/** The gzipped client JavaScript of a Next.js application's production build stays within the budget. */
export const bundle = (member: Member): GateResult => {
  if (!isNextApp(member)) {
    return notApplicable('the member is no Next.js application, so it ships no client bundle')
  }
  const recorded: StampedBuild | { readonly problem: string } = currentBuild(member)
  if ('problem' in recorded) {
    return failed('no current production build to measure', [recorded.problem])
  }
  return measure(member, path.join(recorded.directory, 'static'))
}
