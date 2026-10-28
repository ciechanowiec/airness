import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { type GateResult, passed, type RunResult, run } from '@/exec.js'
import { type Member, membersOf } from '@/project.js'
import { type Invocation, withPretest, withPretestAsync, wrapped } from '@/test-invocation.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

const member = (root: string): Member => {
  const first: Member | undefined = membersOf(root)[0]
  if (first === undefined) {
    throw new Error('The fixture has no member')
  }
  return first
}

describe('application process preparation', PROCESS_TIME, () => {
  it('forwards a plain invocation and runs synchronous and asynchronous gates without preparation', async () => {
    const root: string = temporaryProject({ 'package.json': '{"name":"fixture"}' })
    try {
      expect(wrapped(member(root), ['tool.js', 'run'])).toEqual({
        command: process.execPath,
        commandArguments: ['tool.js', 'run'],
      })
      expect(withPretest(member(root), (): GateResult => passed('ran')).ok).toBe(true)
      const outcome: GateResult = await withPretestAsync(
        member(root),
        (): Promise<GateResult> => Promise.resolve(passed('ran')),
      )
      expect(outcome.ok).toBe(true)
    } finally {
      removeProject(root)
    }
  })

  it('prepares under the verification clock and forwards the pinned child through the wrapper', () => {
    const root: string = temporaryProject({
      'package.json': JSON.stringify({
        name: 'fixture',
        airness: { pretest: [process.execPath, 'prepare.mjs'], testWrapper: [process.execPath, 'wrapper.mjs', '--'] },
      }),
      'prepare.mjs': "import fs from 'node:fs'; fs.writeFileSync('prepared', String(new Date().getUTCFullYear()))",
      'wrapper.mjs': [
        "import { spawnSync } from 'node:child_process'",
        "const [command, ...args] = process.argv.slice(process.argv.indexOf('--') + 1)",
        "const result = spawnSync(command, args, { stdio: 'inherit', env: { ...process.env, ISOLATED: 'yes' } })",
        'process.exitCode = result.status ?? 1',
      ].join('\n'),
    })
    try {
      const result: GateResult = withPretest(member(root), (): GateResult => {
        const invocation: Invocation = wrapped(member(root), [
          '-e',
          'console.log(process.env.ISOLATED); process.exitCode = 7',
        ])
        const child: RunResult = run(invocation.command, invocation.commandArguments, { cwd: root })
        expect(child.code).toBe(7)
        expect(child.stdout.trim()).toBe('yes')
        return passed('the actual child ran')
      })
      expect(result.ok).toBe(true)
      expect(readFileSync(path.join(root, 'prepared'), 'utf8')).toBe('2100')
    } finally {
      removeProject(root)
    }
  })

  it('stops before the gate when service preparation fails', () => {
    const root: string = temporaryProject({
      'package.json': JSON.stringify({
        name: 'fixture',
        airness: { pretest: [process.execPath, '-e', 'process.exitCode = 3'] },
      }),
    })
    try {
      const file: string = path.join(root, 'gate-ran')
      const result: GateResult = withPretest(member(root), (): GateResult => {
        writeFileSync(file, 'incorrect')
        return passed('incorrect')
      })
      expect(result.ok).toBe(false)
      expect(result.summary).toBe('the declared pretest command failed')
      expect(existsSync(file)).toBe(false)
    } finally {
      removeProject(root)
    }
  })
})
