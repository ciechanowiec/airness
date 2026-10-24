#!/usr/bin/env node
// The `airness-node` command. It runs one gate in the node project it is started in and gives no
// verdict on the project as a whole: `mvn verify` is the only full run, and it runs every gate through
// `airness:node-gate`, which reads the result file this writes.
import { writeFileSync } from 'node:fs'
import { crashedResult, type MemberOutcome, type ResultFile, resultOf } from './gate-result.js'
import { GATES, type Gate, gateById } from './gates.js'
import { type Member, membersOf } from './project.js'

// argv begins with the node binary and the script path; the command follows them.
const ARGV_COMMAND_OFFSET: number = 2
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

const runGate = (gate: Gate, members: readonly Member[], isVerbose: boolean): ResultFile => {
  const outcomes: readonly MemberOutcome[] = members.map(
    (member: Member): MemberOutcome => ({ member: member.path, result: gate.run(member) }),
  )
  if (isVerbose) {
    for (const outcome of outcomes) {
      console.info(`--- ${gate.id} output (${outcome.member}) ---`)
      console.info(outcome.result.output ?? '(the tool produced no output)')
    }
  }
  return resultOf(gate.id, gate.title, outcomes)
}

const describeError = (error: unknown): string => (error instanceof Error ? error.message : String(error))

const gateCommand = (invocation: GateInvocation): number => {
  const gate: Gate | undefined = gateById(invocation.id)
  if (gate === undefined) {
    console.error(`unknown gate "${invocation.id}". Run \`airness-node gates\` to list them.`)
    return 1
  }
  const result: ResultFile = ((): ResultFile => {
    try {
      return runGate(gate, membersOf(process.cwd()), invocation.isVerbose)
    } catch (error: unknown) {
      return crashedResult(gate.id, gate.title, describeError(error))
    }
  })()
  if (invocation.result !== undefined) {
    writeFileSync(invocation.result, `${JSON.stringify(result, null, JSON_INDENT)}\n`)
  }
  console.info(`${result.status === 'passed' ? '[PASS]' : '[FAIL]'} ${gate.id}  ${result.summary}`)
  for (const finding of result.findings) {
    console.info(`  ${finding}`)
  }
  return result.status === 'passed' ? 0 : 1
}

const listGates = (): number => {
  for (const gate of GATES) {
    console.info(`${gate.id.padEnd(GATE_ID_COLUMN)} ${gate.title}`)
  }
  return 0
}

const main = (): number => {
  const [command, ...rest] = process.argv.slice(ARGV_COMMAND_OFFSET)
  if (command === 'gates' && rest.length === 0) {
    return listGates()
  }
  const invocation: GateInvocation | undefined = command === 'gate' ? parseGate(rest) : undefined
  if (invocation === undefined) {
    console.error(USAGE)
    return command === undefined || command === '--help' ? 0 : 1
  }
  return gateCommand(invocation)
}

// Setting the exit code is how Node reports a verdict while still flushing stdout.
process.exitCode = main()
