import { describe, expect, it } from 'vitest'
import {
  AUXILIARY_SERVER_URLS_ENVIRONMENT_VARIABLE,
  type OriginSettings,
  onPort,
  originEnvironment,
  SERVER_URL_ENVIRONMENT_VARIABLE,
  type ServedOrigins,
  servedOrigins,
  servedSettings,
} from '@/browser-origin.js'

const declared: OriginSettings = {
  serverUrl: 'http://localhost:3000',
  auxiliaryServers: [
    { command: 'storybook dev', url: 'http://127.0.0.1:6006' },
    {
      command: 'node cms.js',
      url: 'http://127.0.0.1:4000/admin',
      cwd: '../cms',
    },
  ],
  serverUrlVariables: ['NEXT_PUBLIC_SERVER_URL', 'PUBLIC_ORIGIN'],
}

describe('the addresses a run serves on', () => {
  it('keeps the scheme, host and path of a declared URL and swaps its port', () => {
    expect(onPort('http://localhost:3000', 41_000)).toBe('http://localhost:41000')
    expect(onPort('http://localhost', 41_000)).toBe('http://localhost:41000')
    expect(onPort('https://127.0.0.1:8443/app/', 41_000)).toBe('https://127.0.0.1:41000/app/')
  })

  it('gives the application the first port and each auxiliary server the next, in declaration order', () => {
    expect(servedOrigins(declared, [41_000, 41_001, 41_002])).toEqual({
      serverUrl: 'http://localhost:41000',
      auxiliaryUrls: ['http://127.0.0.1:41001', 'http://127.0.0.1:41002/admin'],
      ports: [41_000, 41_001, 41_002],
    })
    expect(() => servedOrigins(declared, [41_000])).toThrow('one port is needed')
  })

  it('names the served origin in the harness variables and in every variable the application reads', () => {
    const origins: ServedOrigins = {
      serverUrl: 'http://localhost:41000/app',
      auxiliaryUrls: ['http://localhost:41001'],
      ports: [41_000, 41_001],
    }
    expect(originEnvironment(declared, origins)).toEqual({
      NEXT_PUBLIC_SERVER_URL: 'http://localhost:41000',
      PUBLIC_ORIGIN: 'http://localhost:41000',
      [SERVER_URL_ENVIRONMENT_VARIABLE]: 'http://localhost:41000/app',
      [AUXILIARY_SERVER_URLS_ENVIRONMENT_VARIABLE]: '["http://localhost:41001"]',
    })
  })

  it('reads the assigned addresses back in place of the declared ones, and the declared ones without them', () => {
    const environment: Readonly<Record<string, string>> = originEnvironment(
      declared,
      servedOrigins(declared, [41_000, 41_001, 41_002]),
    )
    const served: OriginSettings = servedSettings(declared, environment)
    expect(served.serverUrl).toBe('http://localhost:41000')
    expect(served.auxiliaryServers).toEqual([
      { command: 'storybook dev', url: 'http://127.0.0.1:41001' },
      {
        command: 'node cms.js',
        url: 'http://127.0.0.1:41002/admin',
        cwd: '../cms',
      },
    ])
    expect(servedSettings(declared, {})).toBe(declared)
  })

  it('refuses an assignment that does not name one URL per declared auxiliary server', () => {
    const environment: Readonly<Record<string, string>> = {
      [SERVER_URL_ENVIRONMENT_VARIABLE]: 'http://localhost:41000',
      [AUXILIARY_SERVER_URLS_ENVIRONMENT_VARIABLE]: '["http://127.0.0.1:41001"]',
    }
    expect(() => servedSettings(declared, environment)).toThrow('one URL per declared auxiliary server')
    expect(() =>
      servedSettings(declared, {
        [SERVER_URL_ENVIRONMENT_VARIABLE]: 'http://localhost:1',
      }),
    ).toThrow('one URL per declared auxiliary server')
  })
})
