import { describe, expect, it } from 'vitest'
import { type Environment, loadEnvironment } from '@/lib/environment'

// The outcome of reading the environment, as text: the values, or the reason they could not be read.
const attempt = (read: () => Environment): string => {
  try {
    return JSON.stringify(read())
  } catch (error: unknown) {
    return String(error)
  }
}

describe('loadEnvironment', () => {
  it('reads both required variables', () => {
    expect(loadEnvironment({ PAYLOAD_SECRET: 'token', DATABASE_URL: 'postgres://db' })).toStrictEqual({
      payloadSecret: 'token',
      databaseUrl: 'postgres://db',
    })
  })

  it('refuses a variable that is missing or blank', () => {
    expect(() => loadEnvironment({ DATABASE_URL: 'postgres://db' })).toThrow('PAYLOAD_SECRET is required')
    expect(() => loadEnvironment({ PAYLOAD_SECRET: 'token', DATABASE_URL: ' ' })).toThrow('DATABASE_URL is required')
  })

  it('reads the two variables the process carries when given no record', () => {
    const carried: Readonly<Record<string, string | undefined>> = {
      PAYLOAD_SECRET: process.env['PAYLOAD_SECRET'],
      DATABASE_URL: process.env['DATABASE_URL'],
    }
    expect(attempt(loadEnvironment)).toStrictEqual(attempt(() => loadEnvironment(carried)))
  })
})
