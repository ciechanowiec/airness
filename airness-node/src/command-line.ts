// The `airness-node` command line. It runs one gate in the node project it is started in and gives no
// verdict on the project as a whole: `mvn verify` is the only full run, and it runs every gate through
// `airness:node-gate`, which reads the result file this writes.
import path from 'node:path'
import type { GateContext } from './exec.js'
import { formatCommand } from './format-command.js'
import { type GateInvocation, listGates, parseGateInvocation, printOutputs, reportGate } from './gate-invocation.js'
import { crashedResult, type MemberOutcome, type ResultFile, resultOf } from './gate-result.js'
import { GATES, type Gate, gateById } from './gates.js'
import { type Member, membersOf } from './project.js'
import { isScopeCommand, scopeCommand } from './scope-command.js'

const USAGE: string = `airness-node, the JS/TS gates of the Airness node layer

  airness-node gate <id> [--result <file>] [--verbose]   run one gate in this node project
  airness-node scope [--json]                         explain the browser scope of this change
  airness-node format                                    apply the harness formatters
  airness-node gates                                     list the gates

One gate is a diagnosis, not a verdict. Run mvn verify for the verdict.`

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
  if (invocation.isVerbose) {
    printOutputs(gate.id, outcomes)
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
  return reportGate(invocation, result)
}

const utilityCommand = (
  command: string | undefined,
  rest: readonly string[],
  directory: string,
): number | undefined => {
  if (rest.length > 0) {
    return undefined
  }
  switch (command ?? '') {
    case 'format': {
      return formatCommand(directory)
    }
    case 'gates': {
      return listGates(GATES)
    }
    default: {
      return undefined
    }
  }
}

/**
 * Run one command line.
 * @param commandLine the arguments after the executable, such as `gate wiring --result r.json`.
 * @param directory the node project the command runs in.
 * @returns the exit code: 0 for a pass or a listing, 1 for a failure or a malformed command line.
 */
export const runCommandLine = async (commandLine: readonly string[], directory: string): Promise<number> => {
  const [command, ...rest] = commandLine
  if (isScopeCommand(command, rest)) {
    return scopeCommand(directory, rest.includes('--json'))
  }
  const utility: number | undefined = utilityCommand(command, rest, directory)
  if (utility !== undefined) {
    return utility
  }
  const invocation: GateInvocation | undefined = command === 'gate' ? parseGateInvocation(rest) : undefined
  if (invocation === undefined) {
    console.error(USAGE)
    return command === undefined || command === '--help' ? 0 : 1
  }
  return gateCommand(invocation, directory)
}
