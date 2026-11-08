// The result file: the only contract between `airness:node-gate` and a gate, and the outcome of one gate in one
// member, which a stack layer's gates build with the same constructors as the node layer's. A uniform shape lets
// the runner present one verdict whether a gate shelled out to a tool or evaluated a rule in process.
import { ROOT_MEMBER_PATH } from './workspace.js'

/** The outcome of one gate. */
export interface GateResult {
  readonly ok: boolean
  /** Lines the user must act on when the gate fails, or an informational report when it passes. */
  readonly findings: readonly string[]
  /** One line summarising what the gate observed. */
  readonly summary: string
  /**
   * What the underlying tool actually printed, kept whole and printed only on request.
   *
   * Deliberately apart from `findings`, whose contract is the lines a user must ACT on: a passing
   * gate's output is not that, and merging the two would make a gate that found nothing read as
   * though it had. Held so a reader can audit a verdict rather than take it on faith - "no
   * vulnerability at or above moderate" is a claim, and this is the evidence for it.
   */
  readonly output?: string
  /** Files the gate wrote beside its result for the repository's rules to read, by plain file name. */
  readonly evidence?: readonly string[]
  /**
   * False when the member lacks what the gate judges, such as a facet's framework; the result then reads as
   * not applicable rather than as a pass. Absent means the gate applied.
   */
  readonly isApplicable?: false
}

/** What a gate run knows beyond the member it judges. */
export interface GateContext {
  /** The directory of the result file, where a gate writes its evidence; undefined without `--result`. */
  readonly evidenceDirectory: string | undefined
}

/** A passing result, optionally carrying a non-failing report. */
export const passed = (summary: string, findings: readonly string[] = []): GateResult => ({
  ok: true,
  findings,
  summary,
})

/**
 * A result for a member that lacks what the gate judges, which reads as not applicable rather than as a pass.
 * @param summary why the gate does not apply here.
 * @returns a passing result marked not applicable.
 */
export const notApplicable = (summary: string): GateResult => ({
  ok: true,
  findings: [],
  summary,
  isApplicable: false,
})

/** A failing result carrying the findings the user must act on. */
export const failed = (summary: string, findings: readonly string[]): GateResult => ({
  ok: false,
  findings,
  summary,
})

/** The same result, carrying the tool output that produced it. */
export const withOutput = (result: GateResult, output: string): GateResult =>
  output.length > 0 ? { ...result, output } : result

/** The version of the result-file shape this package writes. */
export const RESULT_CONTRACT: number = 1

/** The verdict a result file carries. */
export type GateStatus = 'passed' | 'failed' | 'not-applicable'

/** The result file, as `node-gate` reads it. */
export interface ResultFile {
  readonly contract: number
  readonly gate: string
  readonly title: string
  readonly status: GateStatus
  readonly summary: string
  readonly findings: readonly string[]
  readonly evidence: readonly string[]
}

/** One member's outcome, named by its path in the node project. */
export interface MemberOutcome {
  readonly member: string
  readonly result: GateResult
}

const located = (member: string, line: string): string => (member === ROOT_MEMBER_PATH ? line : `${member}: ${line}`)

// Not applicable only when no member had anything for the gate to judge: one member it applied to makes the
// project's verdict a pass or a failure.
const statusOf = (failing: readonly MemberOutcome[], outcomes: readonly MemberOutcome[]): GateStatus => {
  if (failing.length > 0) {
    return 'failed'
  }
  return outcomes.every((outcome: MemberOutcome): boolean => outcome.result.isApplicable === false)
    ? 'not-applicable'
    : 'passed'
}

/**
 * Combine every member's outcome into one result file. The gate passes only where every member passes,
 * and a failing member's findings name it. A passing member's findings are an informational report, and
 * the contract carries findings only for a failure, so they stay out of the file.
 * @param gate the gate id.
 * @param title what the gate checks.
 * @param outcomes one outcome per member, the project first.
 * @returns the result file.
 */
export const resultOf = (gate: string, title: string, outcomes: readonly MemberOutcome[]): ResultFile => {
  const failing: readonly MemberOutcome[] = outcomes.filter((outcome: MemberOutcome): boolean => !outcome.result.ok)
  const reported: readonly MemberOutcome[] = failing.length > 0 ? failing : outcomes
  return {
    contract: RESULT_CONTRACT,
    gate,
    title,
    status: statusOf(failing, outcomes),
    summary: reported
      .map((outcome: MemberOutcome): string => located(outcome.member, outcome.result.summary))
      .join('; '),
    findings: failing.flatMap((outcome: MemberOutcome): readonly string[] =>
      outcome.result.findings.map((line: string): string => located(outcome.member, line)),
    ),
    evidence:
      failing.length > 0
        ? []
        : outcomes.flatMap((outcome: MemberOutcome): readonly string[] => outcome.result.evidence ?? []),
  }
}

/**
 * The result of a gate that could not run to a verdict. It fails, naming the cause, so a crash never
 * reads as a pass.
 * @param gate the gate id.
 * @param title what the gate checks.
 * @param cause what stopped it.
 * @returns a failed result file.
 */
export const crashedResult = (gate: string, title: string, cause: string): ResultFile => ({
  contract: RESULT_CONTRACT,
  gate,
  title,
  status: 'failed',
  summary: 'the gate could not run to a verdict',
  findings: [cause],
  evidence: [],
})
