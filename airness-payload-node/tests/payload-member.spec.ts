import { describe, expect, it } from 'vitest'
import { analysisEnvironment } from '@/payload-member.js'
import { payloadMemberAt, projectForTest } from './helpers/temporary-project.js'

describe('payloadMemberOf', () => {
  it('reads a member that declares Payload in any dependency block as a Payload application', () => {
    expect(payloadMemberAt('.', { devDependencies: { payload: '3.91.0' } }).isPayload).toBe(true)
    expect(payloadMemberAt('.', { dependencies: { next: '16.4.2' } }).isPayload).toBe(false)
  })
})

describe('analysisEnvironment', () => {
  it('prefers the local environment file to the shared one, and both to the placeholders', () => {
    const root: string = projectForTest({
      '.env': 'ANALYSIS_ONLY_SHARED=shared\nANALYSIS_ONLY_BOTH=shared\n',
      '.env.local': 'ANALYSIS_ONLY_BOTH=local\n',
    })
    const environment: Readonly<Record<string, string>> = analysisEnvironment(payloadMemberAt(root, {}))
    expect(environment['ANALYSIS_ONLY_SHARED']).toBe('shared')
    expect(environment['ANALYSIS_ONLY_BOTH']).toBe('local')
    expect(environment['PAYLOAD_SECRET']).toBeDefined()
  })

  it('adds nothing the process environment already carries', () => {
    const root: string = projectForTest({ '.env': 'PATH=/nowhere\n' })
    expect(analysisEnvironment(payloadMemberAt(root, {}))['PATH']).toBeUndefined()
  })
})
