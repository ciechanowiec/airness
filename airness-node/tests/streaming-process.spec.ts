import { appendFileSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { type RunResult, runStreaming } from '@/exec.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

// The child waits for its first progress line to reach the callback before finishing. A runner that
// buffers output until exit deadlocks that handshake and the child's bounded timeout fails the test.
const HANDSHAKE: string = String.raw`
const fs = require('node:fs')
process.stdout.write('first\n')
process.stderr.write('detail\n')
const deadline = Date.now() + 5000
const timer = setInterval(() => {
  if (fs.readFileSync(process.argv[1], 'utf8').includes('first')) {
    clearInterval(timer); process.stdout.write('last\n')
  } else if (Date.now() > deadline) { process.exit(4) }
}, 10)
`

describe('streaming process output', PROCESS_TIME, () => {
  it('delivers progress before exit and retains both streams for the final log', async () => {
    const root: string = temporaryProject({ 'progress.txt': '' })
    const file: string = path.join(root, 'progress.txt')
    try {
      const result: RunResult = await runStreaming(process.execPath, ['--eval', HANDSHAKE, file], {
        cwd: root,
        onLine: (line: string): void => {
          appendFileSync(file, `${line}\n`)
        },
      })
      const lines: readonly string[] = readFileSync(file, 'utf8').trim().split('\n')
      expect(result.code).toBe(0)
      expect(lines).toEqual(expect.arrayContaining(['first', 'detail', 'last']))
      expect(lines.indexOf('first')).toBeLessThan(lines.indexOf('last'))
      expect(result.stdout).toBe('first\nlast\n')
      expect(result.output).toContain('detail')
    } finally {
      removeProject(root)
    }
  })

  it('reports startup failures and signal termination without manufacturing progress', async () => {
    const root: string = temporaryProject({ 'progress.txt': '' })
    const file: string = path.join(root, 'progress.txt')
    const record = (line: string): void => {
      appendFileSync(file, `${line}\n`)
    }
    try {
      const missing: RunResult = await runStreaming('/airness/no-such-command', [], { cwd: root, onLine: record })
      expect(missing.code).toBe(127)
      const killed: RunResult = await runStreaming(
        process.execPath,
        ['--eval', "process.kill(process.pid, 'SIGTERM')"],
        {
          cwd: root,
          onLine: record,
        },
      )
      expect(killed.code).toBe(1)
      expect(killed.output).toContain('SIGTERM')
      expect(readFileSync(file, 'utf8')).toBe('')
    } finally {
      removeProject(root)
    }
  })
})
