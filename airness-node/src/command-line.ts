// The `airness-node` command line. It runs one gate in the node project it is started in and gives no
// verdict on the project as a whole: `mvn verify` is the only full run, and it runs every gate through
// `airness:node-gate`, which reads the result file this writes.
import { writeFileSync } from 'node:fs'
import path from 'node:path'
import type { GateContext } from './exec.js'
import { crashedResult, type GateStatus, type MemberOutcome, type ResultFile, resultOf } from './gate-result.js'
import { GATES, type Gate, gateById } from './gates.js'
import { type Member, membersOf } from './project.js'

// How the verdict reads on the console.
const STATUS_LABELS: Readonly<Record<GateStatus, string>> = {
  passed: '[PASS]',
  failed: '[FAIL]',
  'not-applicable': '[N/A]',
}
const RESULT_OPTION: string = '--result'
const VERBOSE_OPTION: string = '--verbose'
const JSON_INDENT: number = 2
// Wide enough for the longest gate identifier, so the listing stays aligned.
const GATE_ID_COLUMN: number = 22

const USAGE: string = `airness-node, the JS/TS gates of the Airness node layer

  airness-node gate <id> [--result <file>] [--verbose]   run one gate in this node project
  airness-node gates                                     list the gates

One gate is a diagnosis, not a verdict. Run mvn verify for the verdict.`

/** One parsed `gate` invocation. */
interface GateInvocation {
  readonly id: string
  readonly result: string | undefined
  readonly isVerbose: boolean
}

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

const parseGate = (rest: readonly string[]): GateInvocation | undefined => {
  const [id, ...options] = rest
  if (id === undefined || id.startsWith('-')) {
    return undefined
  }
  const result: string | undefined = resultPath(options)
  return isWellFormed(options, result) ? { id, result, isVerbose: options.includes(VERBOSE_OPTION) } : undefined
}

const runGate = async (gate: Gate, members: readonly Member[], invocation: GateInvocation): Promise<ResultFile> => {
  const context: GateContext = {
    evidenceDirectory: invocation.result === undefined ? undefined : path.dirname(path.resolve(invocation.result)),
  }
  const outcomes: readonly MemberOutcome[] = await Promise.all(
    members.map(
      async (member: Member): Promise<MemberOutcome> => ({
        member: member.path,
        result: await gate.run(member, context),
      }),
    ),
  )
  const isVerbose: boolean = invocation.isVerbose
  if (isVerbose) {
    for (const outcome of outcomes) {
      console.info(`--- ${gate.id} output (${outcome.member}) ---`)
      console.info(outcome.result.output ?? '(the tool produced no output)')
    }
  }
  return resultOf(gate.id, gate.title, outcomes)
}

const describeError = (error: unknown): string => (error instanceof Error ? error.message : String(error))

const gateCommand = async (invocation: GateInvocation, directory: string): Promise<number> => {
  const gate: Gate | undefined = gateById(invocation.id)
  if (gate === undefined) {
    console.error(`unknown gate "${invocation.id}". Run \`airness-node gates\` to list them.`)
    return 1
  }
  const result: ResultFile = await (async (): Promise<ResultFile> => {
    try {
      return await runGate(gate, membersOf(directory), invocation)
    } catch (error: unknown) {
      return crashedResult(gate.id, gate.title, describeError(error))
    }
  })()
  if (invocation.result !== undefined) {
    writeFileSync(invocation.result, `${JSON.stringify(result, null, JSON_INDENT)}\n`)
  }
  console.info(`${STATUS_LABELS[result.status]} ${gate.id}  ${result.summary}`)
  for (const finding of result.findings) {
    console.info(`  ${finding}`)
  }
  // Only a failure exits nonzero: a facet gate with nothing to judge is not applicable, which is no failure.
  return result.status === 'failed' ? 1 : 0
}

const listGates = (): number => {
  for (const gate of GATES) {
    console.info(`${gate.id.padEnd(GATE_ID_COLUMN)} ${gate.title}`)
  }
  return 0
}

/**
 * Run one command line.
 * @param commandLine the arguments after the executable, such as `gate wiring --result r.json`.
 * @param directory the node project the command runs in.
 * @returns the exit code: 0 for a pass or a listing, 1 for a failure or a malformed command line.
 */
export const runCommandLine = async (commandLine: readonly string[], directory: string): Promise<number> => {
  const [command, ...rest] = commandLine
  if (command === 'gates' && rest.length === 0) {
    return listGates()
  }
  const invocation: GateInvocation | undefined = command === 'gate' ? parseGate(rest) : undefined
  if (invocation === undefined) {
    console.error(USAGE)
    return command === undefined || command === '--help' ? 0 : 1
  }
  return gateCommand(invocation, directory)
}
