// The result file: the only contract between `airness:node-gate` and a gate.
import type { GateResult } from './exec.js'
import { ROOT_MEMBER_PATH } from './workspace.js'

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
    status: failing.length > 0 ? 'failed' : 'passed',
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
