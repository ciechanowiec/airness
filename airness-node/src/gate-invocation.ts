// The command line of one gate, which every Airness gate command shares: `gate <id> [--result <file>]
// [--verbose]`, the result file it writes in the contract `airness:node-gate` reads, and the console lines
// that report it. A stack layer's command runs its own gates through the same shape, so a fix to parsing or
// reporting reaches every gate at once.
import type { GateStatus, MemberOutcome, ResultFile } from './gate-result.js'
import { writeJson } from './project.js'

/** One parsed `gate` invocation. */
export interface GateInvocation {
  readonly id: string
  readonly result: string | undefined
  readonly isVerbose: boolean
}

/** What a gate listing shows of a gate. */
export interface ListedGate {
  readonly id: string
  readonly title: string
}

// How the verdict reads on the console.
const STATUS_LABELS: Readonly<Record<GateStatus, string>> = {
  passed: '[PASS]',
  failed: '[FAIL]',
  'not-applicable': '[N/A]',
}
const RESULT_OPTION: string = '--result'
const VERBOSE_OPTION: string = '--verbose'
// Wide enough for the longest gate identifier, so the listing stays aligned.
const GATE_ID_COLUMN: number = 22

// The value after `--result`, or undefined when the option is absent. A missing or option-like value
// makes the whole invocation malformed rather than writing the result somewhere unintended.
const resultPath = (options: readonly string[]): string | undefined => {
  const resultAt: number = options.indexOf(RESULT_OPTION)
  return resultAt === -1 ? undefined : options[resultAt + 1]
}

// Every option is accounted for exactly once, so an unknown or repeated one is refused rather than ignored.
const isWellFormed = (options: readonly string[], result: string | undefined): boolean => {
  const expected: readonly string[] = [
    ...(options.includes(RESULT_OPTION) ? [RESULT_OPTION, result ?? ''] : []),
    ...(options.includes(VERBOSE_OPTION) ? [VERBOSE_OPTION] : []),
  ]
  const isResultValid: boolean = !(options.includes(RESULT_OPTION) && (result ?? '-').startsWith('-'))
  return isResultValid && expected.length === options.length
}

/**
 * Parse the arguments after `gate`.
 * @param rest the gate id and its options.
 * @returns the invocation, or undefined for a malformed command line.
 */
export const parseGateInvocation = (rest: readonly string[]): GateInvocation | undefined => {
  const [id, ...options] = rest
  if (id === undefined || id.startsWith('-')) {
    return undefined
  }
  const result: string | undefined = resultPath(options)
  return isWellFormed(options, result) ? { id, result, isVerbose: options.includes(VERBOSE_OPTION) } : undefined
}

/**
 * Print what each member's tool wrote, for a verbose invocation.
 * @param gateId the gate that ran.
 * @param outcomes the outcome of each member.
 */
export const printOutputs = (gateId: string, outcomes: readonly MemberOutcome[]): void => {
  for (const outcome of outcomes) {
    console.info(`--- ${gateId} output (${outcome.member}) ---`)
    console.info(outcome.result.output ?? '(the tool produced no output)')
  }
}

/**
 * Write the result file when one was asked for, and print the verdict and its findings.
 * @param invocation the invocation that ran.
 * @param result the gate's result.
 * @returns the exit code: only a failure exits nonzero, since a gate with nothing to judge is not applicable.
 */
export const reportGate = (invocation: GateInvocation, result: ResultFile): number => {
  if (invocation.result !== undefined) {
    writeJson(invocation.result, result)
  }
  console.info(`${STATUS_LABELS[result.status]} ${invocation.id}  ${result.summary}`)
  for (const finding of result.findings) {
    console.info(`  ${finding}`)
  }
  return result.status === 'failed' ? 1 : 0
}

/**
 * Print one line per gate.
 * @param gates the gates to list.
 * @returns the exit code of a listing, 0.
 */
export const listGates = (gates: readonly ListedGate[]): number => {
  for (const gate of gates) {
    console.info(`${gate.id.padEnd(GATE_ID_COLUMN)} ${gate.title}`)
  }
  return 0
}
