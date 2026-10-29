import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { type RunResult, runNode } from '@/process-run.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { projectForTest } from './helpers/temporary-project.js'

/** A script written into a project of its own. */
interface Tool {
  readonly root: string
  readonly file: string
}

const script = (body: string): Tool => {
  const root: string = projectForTest({ 'tool.mjs': body })
  return { root, file: path.join(root, 'tool.mjs') }
}

describe('runNode', PROCESS_TIME, () => {
  it('returns the exit status with both streams, and standard output apart', () => {
    const tool: Tool = script("console.log('out'); console.error('err'); process.exitCode = 4")
    const result: RunResult = runNode(tool.file, [], { cwd: tool.root })
    expect(result.code).toBe(4)
    expect(result.stdout).toBe('out\n')
    expect(result.output).toBe('out\nerr\n')
  })

  it('passes the arguments and the added environment, with the project binaries first on PATH', () => {
    const tool: Tool = script("console.log(process.argv[2], process.env['ADDED'], process.env['PATH'].split(':')[0])")
    const result: RunResult = runNode(tool.file, ['first'], { cwd: tool.root, env: { ADDED: 'yes' } })
    expect(result.stdout.trim()).toBe(`first yes ${path.join(tool.root, 'node_modules', '.bin')}`)
  })

  it('stops a tool that runs past its deadline and says so', () => {
    const tool: Tool = script('setTimeout(() => {}, 60_000)')
    const result: RunResult = runNode(tool.file, [], { cwd: tool.root, timeoutMs: 300 })
    expect(result.code).toBe(124)
    expect(result.output).toContain('did not finish in time')
  })

  it('reports a tool that could not start', () => {
    const tool: Tool = script('')
    const result: RunResult = runNode(tool.file, [], { cwd: path.join(tool.root, 'missing') })
    expect(result.code).toBe(127)
    expect(result.output).toContain('the tool could not run')
  })

  it('names the signal that killed a tool', () => {
    const tool: Tool = script("process.kill(process.pid, 'SIGTERM')")
    expect(runNode(tool.file, [], { cwd: tool.root }).output).toContain('killed by SIGTERM')
  })
})
