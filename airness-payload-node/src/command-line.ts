// The `airness-payload` command line. It runs one Payload gate in the node project it is started in and gives no
// verdict on the project as a whole: `mvn verify` is the only full run, and it runs every gate through
// `airness:node-gate`, which reads the result file this writes in the node layer's contract.
import {
  type GateInvocation,
  listGates,
  parseGateInvocation,
  printOutputs,
  reportGate,
} from '@airness/node/gate-invocation'
import { crashedResult, type MemberOutcome, type ResultFile, resultOf } from '@airness/node/gate-result'
import { type Member, membersOf } from '@airness/node/project'
import { PROBE_COMMAND } from './gate-payload-defaults.js'
import { MIGRATIONS_PROBE_COMMAND } from './gate-payload-migrations.js'
import { GATES, type Gate, gateById } from './gates.js'
import { migrationDriftProbe } from './migration-drift-probe.js'
import { type PayloadMember, payloadMemberOf } from './payload-member.js'
import { inheritedAccessProbe } from './payload-probe.js'

const USAGE: string = `airness-payload, the Payload gates of the Airness Payload stack

  airness-payload gate <id> [--result <file>] [--verbose]   run one gate in this node project
  airness-payload gates                                     list the gates

One gate is a diagnosis, not a verdict. Run mvn verify for the verdict.`

const runGate = (gate: Gate, directory: string, isVerbose: boolean): ResultFile => {
  const members: readonly PayloadMember[] = membersOf(directory).map(
    (member: Member): PayloadMember => payloadMemberOf(member),
  )
  const outcomes: readonly MemberOutcome[] = members.map(
    (target: PayloadMember): MemberOutcome => ({ member: target.member.path, result: gate.run(target, members) }),
  )
  if (isVerbose) {
    printOutputs(gate.id, outcomes)
  }
  return resultOf(gate.id, gate.title, outcomes)
}

const describeError = (error: unknown): string => (error instanceof Error ? error.message : String(error))

const gateCommand = (invocation: GateInvocation, directory: string): number => {
  const gate: Gate | undefined = gateById(invocation.id)
  if (gate === undefined) {
    console.error(`unknown gate "${invocation.id}". Run \`airness-payload gates\` to list them.`)
    return 1
  }
  const result: ResultFile = ((): ResultFile => {
    try {
      return runGate(gate, directory, invocation.isVerbose)
    } catch (error: unknown) {
      return crashedResult(gate.id, gate.title, describeError(error))
    }
  })()
  return reportGate(invocation, result)
}

/** One probe a gate starts through tsx in the member's directory: its words, its two paths, and what it prints. */
interface Probe {
  readonly command: readonly string[]
  readonly paths: string
  readonly run: (first: string, second: string) => Promise<string>
}

const PROBES: readonly Probe[] = [
  { command: PROBE_COMMAND, paths: '<payload.config.ts> <defaultAccess.js>', run: inheritedAccessProbe },
  { command: MIGRATIONS_PROBE_COMMAND, paths: '<payload.config.ts> <payload/dist/index.js>', run: migrationDriftProbe },
]

const probeCommand = async (probe: Probe, rest: readonly string[]): Promise<number> => {
  const [first, second, ...extra] = rest
  if (first === undefined || second === undefined || extra.length > 0) {
    console.error(`usage: airness-payload ${probe.command.join(' ')} ${probe.paths}`)
    return 1
  }
  console.info(await probe.run(first, second))
  return 0
}

const probeOf = (commandLine: readonly string[]): Probe | undefined =>
  PROBES.find((probe: Probe): boolean =>
    probe.command.every((word: string, index: number): boolean => commandLine[index] === word),
  )

/**
 * Run one command line.
 * @param commandLine the arguments after the executable, such as `gate preflight --result r.json`.
 * @param directory the node project the command runs in.
 * @returns the exit code: 0 for a pass or a listing, 1 for a failure or a malformed command line.
 */
export const runCommandLine = async (commandLine: readonly string[], directory: string): Promise<number> => {
  const probe: Probe | undefined = probeOf(commandLine)
  if (probe !== undefined) {
    return await probeCommand(probe, commandLine.slice(probe.command.length))
  }
  const [command, ...rest] = commandLine
  if (command === 'gates' && rest.length === 0) {
    return listGates(GATES)
  }
  const invocation: GateInvocation | undefined = command === 'gate' ? parseGateInvocation(rest) : undefined
  if (invocation === undefined) {
    console.error(USAGE)
    return command === undefined || command === '--help' ? 0 : 1
  }
  return gateCommand(invocation, directory)
}
