// The project obtains its services and disposable data; the harness still chooses the tools and arguments.
import { clockedEnvironment } from './clock-environment.js'
import { fromRun, type GateResult, type RunResult, run } from './exec.js'
import type { Member } from './project.js'

/** The executable and arguments after applying the project's declared environment wrapper. */
export interface Invocation {
  readonly command: string
  readonly commandArguments: readonly string[]
}

/** Forward the pinned Node invocation intact through the optional project wrapper. */
export const wrapped = (member: Member, interpreterArguments: readonly string[]): Invocation => {
  const [wrapper, ...rest] = member.settings.testWrapper
  return wrapper === undefined
    ? { command: process.execPath, commandArguments: interpreterArguments }
    : { command: wrapper, commandArguments: [...rest, process.execPath, ...interpreterArguments] }
}

const pretestFailure = (member: Member): GateResult | undefined => {
  const [command, ...argumentsList] = member.settings.pretest
  if (command === undefined) {
    return undefined
  }
  const result: RunResult = run(command, argumentsList, {
    cwd: member.root,
    env: clockedEnvironment(member, '--no-deprecation'),
  })
  return result.code === 0
    ? undefined
    : fromRun(result, 'the declared pretest command completed', 'the declared pretest command failed')
}

/** Prepare services before a synchronous application operation. */
export const withPretest = (member: Member, gate: () => GateResult): GateResult => pretestFailure(member) ?? gate()

/** Prepare services before an asynchronous application operation. */
export const withPretestAsync = async (member: Member, gate: () => Promise<GateResult>): Promise<GateResult> =>
  pretestFailure(member) ?? (await gate())
