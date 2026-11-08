import { describe, expect, it } from 'vitest'
import { fromRun, type RunResult, runNode } from '@/exec.js'
import type { GateResult } from '@/gate-result.js'
import { PROCESS_TIME } from './helpers/process-time.js'

const CWD: string = process.cwd()

const script = (source: string, options: { readonly input?: string; readonly timeoutMs?: number } = {}): RunResult =>
  runNode('-e', [source], { cwd: CWD, ...options })

describe('running a tool', PROCESS_TIME, () => {
  it('keeps standard output apart from the combined output', () => {
    const result: RunResult = script('process.stdout.write("out"); process.stderr.write("err")')
    expect(result).toEqual({ code: 0, output: 'outerr', stdout: 'out' })
  })

  it('hands the tool its input', () => {
    expect(script('process.stdin.pipe(process.stdout)', { input: 'subject' }).stdout).toBe('subject')
  })

  it('reports a tool killed for its deadline as timed out, not as missing', () => {
    const result: RunResult = script('setTimeout(() => {}, 10_000)', { timeoutMs: 200 })
    expect(result.code).toBe(124)
    expect(result.output).toContain('ETIMEDOUT')
  })

  it('reports a tool that cannot start as not found', () => {
    expect(runNode('/nonexistent/tool.js', [], { cwd: '/nonexistent/directory' }).code).toBe(127)
  })

  it('names the signal that killed a tool', () => {
    expect(script('process.kill(process.pid, "SIGKILL")').output).toContain('the process was killed by SIGKILL')
  })
})

describe('a tool run as a gate result', PROCESS_TIME, () => {
  it('passes on a zero exit and fails with the output, or a marker for none, otherwise', () => {
    expect(fromRun({ code: 0, output: '', stdout: '' }, 'held', 'broken').ok).toBe(true)
    const silent: GateResult = fromRun({ code: 1, output: '', stdout: '' }, 'held', 'broken')
    expect(silent.findings).toEqual(['(the tool produced no output)'])
    expect(fromRun({ code: 1, output: 'a\r\nb', stdout: '' }, 'held', 'broken').findings).toEqual(['a', 'b'])
  })
})
