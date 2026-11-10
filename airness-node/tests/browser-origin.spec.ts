import { describe, expect, it } from 'vitest'
import {
  fixedPorts,
  type OriginSettings,
  originEnvironment,
  portsFor,
  type ServedOrigins,
  servedOrigins,
  servedSettings,
} from '@/browser-origin.js'

// The variables the e2e gate hands the served addresses to Playwright in, as its workers read them back.
const SERVER_URL_VARIABLE: string = 'AIRNESS_SERVER_URL'
const AUXILIARY_SERVER_URLS_VARIABLE: string = 'AIRNESS_AUXILIARY_SERVER_URLS'

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
    const settings: OriginSettings = {
      serverUrl: 'http://localhost:3000',
      auxiliaryServers: [
        { command: 'node bare.js', url: 'http://localhost' },
        { command: 'node app.js', url: 'https://127.0.0.1:8443/app/' },
      ],
      serverUrlVariables: [],
    }
    expect(servedOrigins(settings, [41_000, 41_001, 41_002])).toEqual({
      serverUrl: 'http://localhost:41000',
      auxiliaryUrls: ['http://localhost:41001', 'https://127.0.0.1:41002/app/'],
      ports: [41_000, 41_001, 41_002],
    })
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
      [SERVER_URL_VARIABLE]: 'http://localhost:41000/app',
      [AUXILIARY_SERVER_URLS_VARIABLE]: '["http://localhost:41001"]',
    })
  })

  it("sets each variable an application reads an auxiliary server's origin from to the origin the run serves it on", () => {
    const settings: OriginSettings = {
      serverUrl: 'http://localhost:3000',
      auxiliaryServers: [
        { command: 'storybook dev', url: 'http://127.0.0.1:6006' },
        {
          command: 'node sibling.js',
          url: 'http://127.0.0.1:4000/admin',
          urlVariables: ['SIBLING_URL', 'SIBLING_ORIGIN'],
        },
      ],
      serverUrlVariables: [],
    }
    const environment: Readonly<Record<string, string>> = originEnvironment(
      settings,
      servedOrigins(settings, [41_000, 41_001, 41_002]),
    )
    expect(environment['SIBLING_URL']).toBe('http://127.0.0.1:41002')
    expect(environment['SIBLING_ORIGIN']).toBe('http://127.0.0.1:41002')
    expect(Object.keys(environment)).toHaveLength(4)
  })

  it('keeps the declared port of a fixed auxiliary server and hands the chosen ports to the others in order', () => {
    const settings: OriginSettings = {
      serverUrl: 'http://localhost:3000',
      auxiliaryServers: [
        { command: 'storybook dev', url: 'http://127.0.0.1:6006' },
        { command: 'node sibling.js', url: 'http://127.0.0.1:4000/admin', fixedPort: true },
        { command: 'node other.js', url: 'http://127.0.0.1:5000' },
      ],
      serverUrlVariables: [],
    }
    expect(fixedPorts(settings)).toEqual([4000])
    expect(portsFor(settings, [41_000, 41_001, 41_002])).toEqual([41_000, 41_001, 4000, 41_002])
    expect(servedOrigins(settings, portsFor(settings, [41_000, 41_001, 41_002])).auxiliaryUrls).toEqual([
      'http://127.0.0.1:41001',
      'http://127.0.0.1:4000/admin',
      'http://127.0.0.1:41002',
    ])
    expect(fixedPorts(declared)).toEqual([])
  })

  it('keeps the declared port of a fixed auxiliary server, or the default port of its scheme', () => {
    const settings: OriginSettings = {
      serverUrl: 'http://localhost:3000',
      auxiliaryServers: [
        { command: 'node named.js', url: 'http://localhost:3000', fixedPort: true },
        { command: 'node http.js', url: 'http://localhost', fixedPort: true },
        { command: 'node https.js', url: 'https://localhost/app', fixedPort: true },
      ],
      serverUrlVariables: [],
    }
    expect(fixedPorts(settings)).toEqual([3000, 80, 443])
    expect(portsFor(settings, [41_000])).toEqual([41_000, 3000, 80, 443])
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
      [SERVER_URL_VARIABLE]: 'http://localhost:41000',
      [AUXILIARY_SERVER_URLS_VARIABLE]: '["http://127.0.0.1:41001"]',
    }
    expect(() => servedSettings(declared, environment)).toThrow('one URL per declared auxiliary server')
    expect(() =>
      servedSettings(declared, {
        [SERVER_URL_VARIABLE]: 'http://localhost:1',
      }),
    ).toThrow('one URL per declared auxiliary server')
  })
})
