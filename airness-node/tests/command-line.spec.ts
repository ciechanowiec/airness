import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { runCommandLine } from '@/command-line.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

const withProject = async (
  files: Readonly<Record<string, string>>,
  use: (root: string) => Promise<void>,
): Promise<void> => {
  const root: string = temporaryProject({ 'package.json': '{"name":"shop"}\n', ...files })
  try {
    await use(root)
  } finally {
    removeProject(root)
  }
}

describe('the command line', () => {
  it('lists the gates and prints its usage', async () => {
    expect(await runCommandLine(['gates'], process.cwd())).toBe(0)
    expect(await runCommandLine([], process.cwd())).toBe(0)
    expect(await runCommandLine(['--help'], process.cwd())).toBe(0)
  })

  it('refuses an unknown command, an unknown gate and a malformed option', async () => {
    expect(await runCommandLine(['verify'], process.cwd())).toBe(1)
    expect(await runCommandLine(['gate', 'unknown'], process.cwd())).toBe(1)
    expect(await runCommandLine(['gate'], process.cwd())).toBe(1)
    expect(await runCommandLine(['gate', 'wiring', '--result'], process.cwd())).toBe(1)
    expect(await runCommandLine(['gate', 'wiring', '--result', '--verbose'], process.cwd())).toBe(1)
    expect(await runCommandLine(['gate', 'wiring', '--quiet'], process.cwd())).toBe(1)
  })

  it('writes the result of a passing gate and exits zero, and of a failing one and exits one', async () => {
    await withProject({ 'src/total.ts': 'export const total = 1\n' }, async (root: string): Promise<void> => {
      const result: string = path.join(root, 'javascript-allowlist.json')
      expect(await runCommandLine(['gate', 'javascript-allowlist', '--result', result, '--verbose'], root)).toBe(0)
      expect(JSON.parse(readFileSync(result, 'utf8'))).toEqual(
        expect.objectContaining({ contract: 1, gate: 'javascript-allowlist', status: 'passed' }),
      )
    })
    await withProject({ 'src/legacy.js': 'export const legacy = 1\n' }, async (root: string): Promise<void> => {
      expect(await runCommandLine(['gate', 'javascript-allowlist'], root)).toBe(1)
    })
  })

  it('reports a gate that cannot run to a verdict as a failure naming the cause', async () => {
    const outside: string = mkdtempSync(path.join(tmpdir(), 'airness-outside-'))
    try {
      const result: string = path.join(outside, 'javascript-allowlist.json')
      expect(await runCommandLine(['gate', 'javascript-allowlist', '--result', result], outside)).toBe(1)
      expect(JSON.parse(readFileSync(result, 'utf8'))).toEqual(
        expect.objectContaining({ status: 'failed', summary: 'the gate could not run to a verdict' }),
      )
    } finally {
      rmSync(outside, { recursive: true, force: true })
    }
  })
})
