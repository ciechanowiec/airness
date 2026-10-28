import { describe, expect, it } from 'vitest'
import { invocationSettings } from '@/invocation-settings.js'
import { readSettings } from '@/settings.js'

describe('application preparation declarations', () => {
  it('rejects a malformed settings block before it can discard service preparation', () => {
    expect(() => readSettings({ airness: 'invalid' })).toThrow('must be an object')
    expect(() => readSettings({ airness: [] })).toThrow('must be an object')
  })
  it('defaults to no preparation and preserves literal arguments', () => {
    expect(invocationSettings({})).toEqual({ pretest: [], testWrapper: [] })
    expect(invocationSettings({ pretest: [], testWrapper: ['node', 'with database.ts', '--', ''] })).toEqual({
      pretest: [],
      testWrapper: ['node', 'with database.ts', '--', ''],
    })
  })

  it('refuses malformed declarations instead of silently dropping database isolation', () => {
    for (const key of ['pretest', 'testWrapper']) {
      for (const value of ['pnpm run prepare', {}, [false], [' ']]) {
        expect(() => invocationSettings({ [key]: value })).toThrow(`airness.${key}`)
      }
    }
  })
})
