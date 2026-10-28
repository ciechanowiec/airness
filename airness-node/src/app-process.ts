import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { type RunOptions, type RunResult, run, runStreaming, type StreamingOptions } from './exec.js'
import { asRecord } from './json-shapes.js'
import type { Member } from './project.js'
import { type Invocation, wrapped } from './test-invocation.js'

const checked = (result: RunResult, receipt: string): RunResult => {
  if (result.code !== 0) {
    return result
  }
  try {
    const code: unknown = asRecord(JSON.parse(readFileSync(receipt, 'utf8')))['code']
    if (typeof code === 'number' && Number.isSafeInteger(code) && code >= 0) {
      return code === 0
        ? result
        : { ...result, code, output: `${result.output}\nThe wrapped tool exited with ${String(code)}` }
    }
  } catch {
    // No receipt means the wrapper never completed the requested tool, even if the wrapper exited zero.
  }
  return {
    ...result,
    code: 1,
    output: `${result.output}\nThe application wrapper did not complete the requested command`,
  }
}

const invocation = (member: Member, argumentsList: readonly string[], receipt: string): Invocation =>
  wrapped(member, [fileURLToPath(import.meta.resolve('#harness/app-child')), receipt, ...argumentsList])

/** Run an application tool under its declared wrapper, retaining the actual child verdict. */
export const runApp = (member: Member, argumentsList: readonly string[], options: RunOptions): RunResult => {
  if (member.settings.testWrapper.length === 0) {
    return run(process.execPath, argumentsList, options)
  }
  const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-application-'))
  try {
    const receipt: string = path.join(directory, 'completed.json')
    const child: Invocation = invocation(member, argumentsList, receipt)
    return checked(run(child.command, child.commandArguments, options), receipt)
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}

/** Stream browser progress through the same wrapper and child-verdict boundary as other application tools. */
export const streamApp = async (
  member: Member,
  argumentsList: readonly string[],
  options: StreamingOptions,
): Promise<RunResult> => {
  if (member.settings.testWrapper.length === 0) {
    return await runStreaming(process.execPath, argumentsList, options)
  }
  const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-application-'))
  try {
    const receipt: string = path.join(directory, 'completed.json')
    const child: Invocation = invocation(member, argumentsList, receipt)
    return checked(await runStreaming(child.command, child.commandArguments, options), receipt)
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}
