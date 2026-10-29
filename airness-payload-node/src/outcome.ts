// The outcome of one Payload gate in one member, in the shape `@airness/node/gate-result` combines into the
// result file `airness:node-gate` reads. The shape is structural, so a Payload gate needs nothing from the
// node layer's internals to produce it.

/** The outcome of one gate in one member. */
export interface GateResult {
  readonly ok: boolean
  /** Lines the user must act on when the gate fails, or an informational report when it passes. */
  readonly findings: readonly string[]
  /** One line summarising what the gate observed. */
  readonly summary: string
  /** What the underlying tool printed, kept whole and printed only on request. */
  readonly output?: string
  /** False when the member lacks what the gate judges; the result then reads as not applicable. */
  readonly isApplicable?: false
}

/**
 * A passing result.
 * @param summary what the gate observed.
 * @returns the result.
 */
export const passed = (summary: string): GateResult => ({ ok: true, findings: [], summary })

/**
 * A failing result carrying the findings the user must act on.
 * @param summary what the gate observed.
 * @param findings one line per defect, each naming its repair.
 * @returns the result.
 */
export const failed = (summary: string, findings: readonly string[]): GateResult => ({ ok: false, findings, summary })

/**
 * The result for a member that has nothing the gate judges, such as a library beside the Payload application.
 * @param summary why the gate does not apply here.
 * @returns a passing result marked not applicable.
 */
export const notApplicable = (summary: string): GateResult => ({ ok: true, findings: [], summary, isApplicable: false })

/**
 * The same result, carrying the tool output that produced it.
 * @param result the result.
 * @param output what the tool printed.
 * @returns the result with its output.
 */
export const withOutput = (result: GateResult, output: string): GateResult => ({ ...result, output })

// Long enough to explain a failure, short enough that a result file stays readable: a stack trace through bundled
// code prints whole minified modules on one line.
const MAX_FINDING_LINES: number = 40
const MAX_FINDING_LENGTH: number = 400

const shortened = (line: string): string =>
  line.length > MAX_FINDING_LENGTH ? `${line.slice(0, MAX_FINDING_LENGTH)} [cut]` : line

/**
 * The lines of a tool's output, as findings: the non-blank ones, the last of them when there are many, each cut
 * to a readable length.
 * @param output what the tool printed.
 * @returns at most the last forty non-blank lines.
 */
export const asFindings = (output: string): readonly string[] => {
  const lines: readonly string[] = output.split('\n').filter((line: string): boolean => line.trim().length > 0)
  return lines.length === 0
    ? ['the tool printed nothing to explain its failure']
    : lines.slice(-MAX_FINDING_LINES).map((line: string): string => shortened(line))
}
