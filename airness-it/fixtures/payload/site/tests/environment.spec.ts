import { describe, expect, it } from 'vitest'
import { type Environment, loadEnvironment, parseEnvironment } from '@/lib/environment'

// The outcome of reading the environment, as text: the values, or the reason they could not be read.
const attempt = (read: () => Environment): string => {
  try {
    return JSON.stringify(read())
  } catch (error: unknown) {
    return String(error)
  }
}

describe('parseEnvironment', () => {
  it('reads both required variables', () => {
    expect(parseEnvironment({ PAYLOAD_SECRET: 'token', DATABASE_URL: 'postgres://db' })).toStrictEqual({
      payloadSecret: 'token',
      databaseUrl: 'postgres://db',
    })
  })

  it('refuses a variable that is missing or blank', () => {
    expect(() => parseEnvironment({ DATABASE_URL: 'postgres://db' })).toThrow('PAYLOAD_SECRET is required')
    expect(() => parseEnvironment({ PAYLOAD_SECRET: 'token', DATABASE_URL: ' ' })).toThrow('DATABASE_URL is required')
  })
})

describe('loadEnvironment', () => {
  it('reads the two variables the process carries, as parseEnvironment reads them', () => {
    const carried: Readonly<Record<string, string | undefined>> = {
      PAYLOAD_SECRET: process.env['PAYLOAD_SECRET'],
      DATABASE_URL: process.env['DATABASE_URL'],
    }
    expect(attempt(loadEnvironment)).toStrictEqual(attempt(() => parseEnvironment(carried)))
  })
})
