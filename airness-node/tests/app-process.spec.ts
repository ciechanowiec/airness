import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { runApp, streamApp } from '@/app-process.js'
import type { RunResult } from '@/exec.js'
import type { Member } from '@/project.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

const WRAPPER: string = [
  "import fs from 'node:fs'",
  "import { spawnSync } from 'node:child_process'",
  'const [command, ...args] = process.argv.slice(2)',
  "fs.writeFileSync('receipt-path', args[1])",
  "spawnSync(command, args, { stdio: 'inherit' })",
  '// Deliberately swallow the child status: the harness must still see its failure.',
].join('\n')

const withWrapper = async (script: string, use: (member: Member) => void | Promise<void>): Promise<void> => {
  const root: string = temporaryProject({ 'wrapper.mjs': script })
  try {
    const manifest: unknown = { airness: { testWrapper: [process.execPath, 'wrapper.mjs'] } }
    await use(memberAt(root, manifest))
  } finally {
    removeProject(root)
  }
}

describe('the wrapped application verdict', PROCESS_TIME, () => {
  it('runs ordinary commands unchanged when no wrapper is declared', async () => {
    const root: string = temporaryProject({})
    try {
      const member: Member = memberAt(root)
      expect(runApp(member, ['-e', 'console.log("plain")'], { cwd: root }).stdout.trim()).toBe('plain')
      const streamed: RunResult = await streamApp(member, ['-e', 'process.exitCode = 4'], {
        cwd: root,
        onLine: (): void => undefined,
      })
      expect(streamed.code).toBe(4)
    } finally {
      removeProject(root)
    }
  })

  it('retains a failed child even when the wrapper returns zero and removes its receipt', async () => {
    await withWrapper(WRAPPER, async (member: Member): Promise<void> => {
      const result: RunResult = runApp(member, ['-e', 'process.exitCode = 7'], { cwd: member.root })
      expect(result.code).toBe(7)
      expect(result.output).toContain('wrapped tool exited with 7')
      const receipt: string = readFileSync(path.join(member.root, 'receipt-path'), 'utf8')
      expect(existsSync(path.dirname(receipt))).toBe(false)
      const streamed: RunResult = await streamApp(member, ['-e', 'console.log("finished")'], {
        cwd: member.root,
        onLine: (): void => undefined,
      })
      expect(streamed).toMatchObject({ code: 0, stdout: 'finished\n' })
    })
  })

  it('refuses a wrapper that never runs the requested tool', async () => {
    await withWrapper('process.exitCode = 0', async (member: Member): Promise<void> => {
      expect(runApp(member, ['-e', 'console.log("unreached")'], { cwd: member.root }).output).toContain(
        'did not complete the requested command',
      )
      const result: RunResult = await streamApp(member, ['-e', 'process.exitCode = 0'], {
        cwd: member.root,
        onLine: (): void => undefined,
      })
      expect(result.code).toBe(1)
    })
  })

  it('retains wrapper failures and rejects a malformed completion record', async () => {
    await withWrapper('process.exitCode = 3', (member: Member): void => {
      expect(runApp(member, ['-e', 'process.exitCode = 0'], { cwd: member.root }).code).toBe(3)
    })
    await withWrapper("import fs from 'node:fs'; fs.writeFileSync(process.argv[4], '{}')", (member: Member): void => {
      expect(runApp(member, ['-e', 'process.exitCode = 0'], { cwd: member.root }).code).toBe(1)
    })
  })
})
