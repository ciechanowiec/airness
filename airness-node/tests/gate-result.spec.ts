import { describe, expect, it } from 'vitest'
import { failed, passed } from '@/exec.js'
import { crashedResult, RESULT_CONTRACT, type ResultFile, resultOf } from '@/gate-result.js'

describe('the result file', () => {
  it('passes only when every member passes, and names the gate and the contract', () => {
    const result: ResultFile = resultOf('types', 'strict type check', [
      { member: '.', result: passed('the root type-checks') },
      { member: 'apps/web', result: passed('the web type-checks') },
    ])
    expect(result).toEqual({
      contract: RESULT_CONTRACT,
      gate: 'types',
      title: 'strict type check',
      status: 'passed',
      summary: 'the root type-checks; apps/web: the web type-checks',
      findings: [],
      evidence: [],
    })
  })

  it('fails on one failing member and names that member on each of its findings', () => {
    const result: ResultFile = resultOf('types', 'strict type check', [
      { member: '.', result: passed('the root type-checks') },
      { member: 'apps/web', result: failed('does not type-check', ['src/a.ts(1,1): error']) },
    ])
    expect(result.status).toBe('failed')
    expect(result.summary).toBe('apps/web: does not type-check')
    expect(result.findings).toEqual(['apps/web: src/a.ts(1,1): error'])
  })

  it('keeps a passing report out of the findings, which the contract reserves for a failure', () => {
    expect(resultOf('biome', 'formatting', [{ member: '.', result: passed('clean', ['note']) }]).findings).toEqual([])
  })

  it('fails a gate that could not run, naming the cause', () => {
    expect(crashedResult('eslint', 'type-aware lint', 'boom')).toEqual(
      expect.objectContaining({ gate: 'eslint', status: 'failed', findings: ['boom'] }),
    )
  })
})
