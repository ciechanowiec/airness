import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { runCommandLine } from '@/command-line.js'
import { GATES, gateById } from '@/gates.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { projectForTest } from './helpers/temporary-project.js'

describe('the gate registry', () => {
  it('starts with the precondition and finds each gate by its id', () => {
    expect(GATES[0]?.id).toBe('preflight')
    expect(GATES.every((gate): boolean => gateById(gate.id) === gate)).toBe(true)
    expect(gateById('unknown')).toBeUndefined()
  })
})

describe('runCommandLine', PROCESS_TIME, () => {
  it('lists the gates', async () => {
    expect(await runCommandLine(['gates'], '.')).toBe(0)
  })

  it('prints the usage for help and refuses an unknown command', async () => {
    expect(await runCommandLine([], '.')).toBe(0)
    expect(await runCommandLine(['--help'], '.')).toBe(0)
    expect(await runCommandLine(['verify'], '.')).toBe(1)
  })

  it('refuses a malformed gate invocation and an unknown gate', async () => {
    expect(await runCommandLine(['gate'], '.')).toBe(1)
    expect(await runCommandLine(['gate', 'preflight', '--result'], '.')).toBe(1)
    expect(await runCommandLine(['gate', 'preflight', '--color'], '.')).toBe(1)
    expect(await runCommandLine(['gate', 'unknown'], '.')).toBe(1)
  })

  it('writes the result file in the contract and exits nonzero for a failure', async () => {
    const root: string = projectForTest({ 'package.json': JSON.stringify({ name: 'library' }) })
    const result: string = path.join(root, 'preflight.json')
    expect(await runCommandLine(['gate', 'preflight', '--result', result, '--verbose'], root)).toBe(1)
    const written: unknown = JSON.parse(readFileSync(result, 'utf8'))
    expect(written).toMatchObject({ contract: 1, gate: 'preflight', status: 'failed' })
  })

  it('reads a gate with nothing to judge as not applicable, which is no failure', async () => {
    const root: string = projectForTest({ 'package.json': JSON.stringify({ name: 'library' }) })
    expect(await runCommandLine(['gate', 'payload-generated'], root)).toBe(0)
  })

  it('fails a node project whose lockfile cannot be read', async () => {
    const root: string = projectForTest({ 'package.json': '{ not json' })
    const result: string = path.join(root, 'blocklist.json')
    expect(await runCommandLine(['gate', 'payload-blocklist', '--result', result], root)).toBe(1)
  })

  it('refuses a probe invocation without both paths', async () => {
    expect(await runCommandLine(['probe', 'payload-defaults', 'config.ts'], '.')).toBe(1)
  })

  it('prints the report of a probe', async () => {
    const root: string = projectForTest({
      'defaultAccess.mjs': 'export const defaultAccess = () => true\n',
      'config.mjs': 'export default { collections: [], globals: [] }\n',
    })
    expect(
      await runCommandLine(
        ['probe', 'payload-defaults', path.join(root, 'config.mjs'), path.join(root, 'defaultAccess.mjs')],
        root,
      ),
    ).toBe(0)
  })
})
