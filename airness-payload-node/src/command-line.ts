// The `ploaness` command line. It runs one Payload gate in the node project it is started in and gives no
// verdict on the project as a whole: `mvn verify` is the only full run, and it runs every gate through
// `airness:node-gate`, which reads the result file this writes in the node layer's contract.
import { writeFileSync } from 'node:fs'
import {
  crashedResult,
  type GateStatus,
  type MemberOutcome,
  type ResultFile,
  resultOf,
} from '@airness/node/gate-result'
import { type Member, membersOf } from '@airness/node/project'
import { PROBE_COMMAND } from './gate-payload-defaults.js'
import { GATES, type Gate, gateById } from './gates.js'
import { type PayloadMember, payloadMemberOf } from './payload-member.js'
import { inheritedAccessProbe } from './payload-probe.js'

const STATUS_LABELS: Readonly<Record<GateStatus, string>> = {
  passed: '[PASS]',
  failed: '[FAIL]',
  'not-applicable': '[N/A]',
}
const RESULT_OPTION: string = '--result'
const VERBOSE_OPTION: string = '--verbose'
const JSON_INDENT: number = 2
// Wide enough for the longest gate id, so the listing stays aligned.
const GATE_ID_COLUMN: number = 22

const USAGE: string = `ploaness, the Payload gates of the Airness Payload layer

  ploaness gate <id> [--result <file>] [--verbose]   run one gate in this node project
  ploaness gates                                     list the gates

One gate is a diagnosis, not a verdict. Run mvn verify for the verdict.`

interface GateInvocation {
  readonly id: string
  readonly result: string | undefined
  readonly isVerbose: boolean
}

// The value after `--result`, or undefined when the option is absent.
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

const runGate = (gate: Gate, directory: string, isVerbose: boolean): ResultFile => {
  const members: readonly PayloadMember[] = membersOf(directory).map(
    (member: Member): PayloadMember => payloadMemberOf(member),
  )
  const outcomes: readonly MemberOutcome[] = members.map(
    (target: PayloadMember): MemberOutcome => ({ member: target.member.path, result: gate.run(target, members) }),
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

const gateCommand = (invocation: GateInvocation, directory: string): number => {
  const gate: Gate | undefined = gateById(invocation.id)
  if (gate === undefined) {
    console.error(`unknown gate "${invocation.id}". Run \`ploaness gates\` to list them.`)
    return 1
  }
  const result: ResultFile = ((): ResultFile => {
    try {
      return runGate(gate, directory, invocation.isVerbose)
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
  // Only a failure exits nonzero: a gate with nothing to judge in this project is not applicable.
  return result.status === 'failed' ? 1 : 0
}

const listGates = (): number => {
  for (const gate of GATES) {
    console.info(`${gate.id.padEnd(GATE_ID_COLUMN)} ${gate.title}`)
  }
  return 0
}

// The configuration probe the payload-defaults gate starts through tsx in the member's directory.
const probeCommand = async (rest: readonly string[]): Promise<number> => {
  const [configFile, defaultAccessFile, ...extra] = rest
  if (configFile === undefined || defaultAccessFile === undefined || extra.length > 0) {
    console.error(`usage: ploaness ${PROBE_COMMAND.join(' ')} <payload.config.ts> <defaultAccess.js>`)
    return 1
  }
  console.info(await inheritedAccessProbe(configFile, defaultAccessFile))
  return 0
}

const isProbe = (commandLine: readonly string[]): boolean =>
  PROBE_COMMAND.every((word: string, index: number): boolean => commandLine[index] === word)

/**
 * Run one command line.
 * @param commandLine the arguments after the executable, such as `gate preflight --result r.json`.
 * @param directory the node project the command runs in.
 * @returns the exit code: 0 for a pass or a listing, 1 for a failure or a malformed command line.
 */
export const runCommandLine = async (commandLine: readonly string[], directory: string): Promise<number> => {
  if (isProbe(commandLine)) {
    return await probeCommand(commandLine.slice(PROBE_COMMAND.length))
  }
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
