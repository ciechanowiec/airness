import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http'
import { createServer as createSecureServer } from 'node:https'
import type { AddressInfo, Server as NetServer } from 'node:net'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { type Lookup, publicationTimesOf } from '@/registry.js'
import { type LocalCertificate, localCertificate, trusting } from './helpers/local-certificate.js'
import { PROCESS_TIME } from './helpers/process-time.js'

// A registry on a local port, answering the way the npm registry answers a package document request, and
// answering the private package only to the token it was given.
const DOCUMENTS: Readonly<Record<string, unknown>> = {
  '/left-pad': { time: { '1.0.0': '2020-01-01T00:00:00.000Z' } },
  '/@scope%2fnamed': { time: { '2.0.0': '2021-06-01T00:00:00.000Z' } },
}
const PRIVATE: string = '/@corp%2fui'
const TOKEN: string = 'corp-token'
const NOT_FOUND: number = 404
const UNAUTHORIZED: number = 401

const answer = (response: ServerResponse, status: number, document: unknown): void => {
  response.writeHead(status, { 'content-type': 'application/json' })
  response.end(JSON.stringify(document))
}

// Another origin, the same host on another port, which answers only a request that carries no credential.
const MOVED: string = '/@corp%2fmoved'
const FOUND: number = 302
const elsewhereServer: Server = createServer((incoming: IncomingMessage, response: ServerResponse): void => {
  const isAnonymous: boolean = incoming.headers.authorization === undefined
  answer(response, isAnonymous ? 200 : UNAUTHORIZED, { time: { '3.0.0': '2022-01-01T00:00:00.000Z' } })
})

const registryServer: Server = createServer((incoming: IncomingMessage, response: ServerResponse): void => {
  const location: string = incoming.url ?? '/'
  const redirects: Readonly<Record<string, string>> = {
    '/loop': '/loop',
    '/bad': `${['http', ''].join(':')}//[bad`,
    '/@corp%2fsame': PRIVATE,
    '/upgrade': `https://127.0.0.1:${portOf(secureServer)}/left-pad`,
    '/ftp': 'ftp://127.0.0.1/left-pad',
  }
  const target: string | undefined = redirects[location]
  if (target !== undefined) {
    response.writeHead(FOUND, { location: target })
    response.end()
    return
  }
  if (location === MOVED) {
    response.writeHead(FOUND, { location: `${['http', ''].join(':')}//127.0.0.1:${portOf(elsewhereServer)}${MOVED}` })
    response.end()
    return
  }
  if (location === PRIVATE) {
    const isAuthorized: boolean = incoming.headers.authorization === `Bearer ${TOKEN}`
    answer(response, isAuthorized ? 200 : UNAUTHORIZED, { time: { '3.0.0': '2022-01-01T00:00:00.000Z' } })
    return
  }
  const document: unknown = DOCUMENTS[location]
  answer(response, document === undefined ? NOT_FOUND : 200, document ?? { error: 'not found' })
})

// A proxy on a local port that answers every request itself, with a time the registry never gives, so only
// a lookup that went through it reads that time.
const PROXY_TIME: string = '2019-01-01T00:00:00.000Z'
const proxyServer: Server = createServer((_: IncomingMessage, response: ServerResponse): void => {
  answer(response, 200, { time: { '1.0.0': PROXY_TIME } })
})

// The same registry over HTTPS, with a certificate only the specs that trust it accept, which redirects
// within HTTPS and down to plain HTTP.
const CERTIFICATE: LocalCertificate = localCertificate()
const secureServer: NetServer = createSecureServer(
  CERTIFICATE,
  (incoming: IncomingMessage, response: ServerResponse): void => {
    const redirects: Readonly<Record<string, string>> = {
      '/upgraded': '/left-pad',
      '/downgraded': `${registry()}left-pad`,
    }
    const target: string | undefined = redirects[incoming.url ?? '/']
    if (target !== undefined) {
      response.writeHead(FOUND, { location: target })
      response.end()
      return
    }
    const document: unknown = DOCUMENTS[incoming.url ?? '/']
    answer(response, document === undefined ? NOT_FOUND : 200, document ?? { error: 'not found' })
  },
)

const portOf = (server: NetServer): string => String((server.address() as AddressInfo).port)
const registry = (): string => `http://127.0.0.1:${portOf(registryServer)}/`

beforeAll(async (): Promise<void> => {
  for (const server of [registryServer, proxyServer, elsewhereServer, secureServer]) {
    await new Promise<void>((resolve: () => void): void => {
      server.listen(0, '127.0.0.1', resolve)
    })
  }
})

afterAll(async (): Promise<void> => {
  for (const server of [registryServer, proxyServer, elsewhereServer, secureServer]) {
    await new Promise<void>((resolve: () => void): void => {
      server.close((): void => {
        resolve()
      })
    })
  }
})

describe('publication times', PROCESS_TIME, () => {
  it('reads when each release was published from its own registry, a scoped name included', async () => {
    const lookups: readonly Lookup[] = await publicationTimesOf([
      { name: 'left-pad', version: '1.0.0', registry: registry() },
      { name: '@scope/named', version: '2.0.0', registry: registry() },
    ])
    expect(lookups.map((lookup: Lookup): number | undefined => lookup.publishedAt)).toEqual([
      Date.parse('2020-01-01T00:00:00.000Z'),
      Date.parse('2021-06-01T00:00:00.000Z'),
    ])
  })

  it('sends the token the configuration names for the registry, and fails without it', async () => {
    const [authorized] = await publicationTimesOf([
      { name: '@corp/ui', version: '3.0.0', registry: registry(), token: TOKEN },
    ])
    expect(authorized?.publishedAt).toBe(Date.parse('2022-01-01T00:00:00.000Z'))
    const [refused] = await publicationTimesOf([{ name: '@corp/ui', version: '3.0.0', registry: registry() }])
    expect(refused?.problem).toBe(`${registry()} answered 401 Unauthorized`)
  })

  it('never forwards the token to the other origin a registry redirects to', async () => {
    const [moved] = await publicationTimesOf([
      { name: '@corp/moved', version: '3.0.0', registry: registry(), token: TOKEN },
    ])
    expect(moved?.problem, 'the other origin answers only a request that carries no credential').toBeUndefined()
    expect(moved?.publishedAt).toBe(Date.parse('2022-01-01T00:00:00.000Z'))
  })

  it('keeps the token within one origin, and follows no endless or unreadable redirect', async () => {
    const [same] = await publicationTimesOf([
      { name: '@corp/same', version: '3.0.0', registry: registry(), token: TOKEN },
    ])
    expect(same?.publishedAt).toBe(Date.parse('2022-01-01T00:00:00.000Z'))
    const lookups: readonly Lookup[] = await publicationTimesOf([
      { name: 'loop', version: '1.0.0', registry: registry() },
      { name: 'bad', version: '1.0.0', registry: registry() },
    ])
    expect(lookups.map((lookup: Lookup): string | undefined => lookup.problem)).toEqual([
      expect.stringContaining('is not followed'),
      expect.stringContaining('to an address that does not parse'),
    ])
  })

  it('follows a redirect to HTTPS from anywhere, and never one from HTTPS to plain HTTP or to another scheme', async () => {
    const secure: string = `https://127.0.0.1:${portOf(secureServer)}/`
    const lookups: readonly Lookup[] = await trusting(CERTIFICATE.cert, async () =>
      publicationTimesOf([
        { name: 'upgraded', version: '1.0.0', registry: secure },
        { name: 'upgrade', version: '1.0.0', registry: registry() },
        { name: 'downgraded', version: '1.0.0', registry: secure },
        { name: 'ftp', version: '1.0.0', registry: registry() },
      ]),
    )
    expect(lookups.map((lookup: Lookup): number | string => lookup.publishedAt ?? lookup.problem)).toEqual([
      Date.parse('2020-01-01T00:00:00.000Z'),
      Date.parse('2020-01-01T00:00:00.000Z'),
      expect.stringContaining(`redirected to ${registry()}left-pad, which is not followed`),
      expect.stringContaining('redirected to ftp://127.0.0.1/left-pad, which is not followed'),
    ])
  })

  it('says why a time could not be read, rather than passing over the release', async () => {
    const lookups: readonly Lookup[] = await publicationTimesOf([
      { name: 'left-pad', version: '9.9.9', registry: registry() },
      { name: 'missing', version: '1.0.0', registry: registry() },
    ])
    expect(lookups.map((lookup: Lookup): string | undefined => lookup.problem)).toEqual([
      `${registry()} names no publication time for this version`,
      `${registry()} answered 404 Not Found`,
    ])
  })

  it('says why an unreachable registry could not be read', async () => {
    const [lookup] = await publicationTimesOf([{ name: 'left-pad', version: '1.0.0', registry: 'http://127.0.0.1:9/' }])
    expect(lookup?.problem).toContain('fetch failed')
  })

  it('reaches the registry through the proxy the environment names', async () => {
    process.env['HTTP_PROXY'] = `http://127.0.0.1:${portOf(proxyServer)}`
    process.env['NO_PROXY'] = ''
    try {
      const [lookup] = await publicationTimesOf([{ name: 'left-pad', version: '1.0.0', registry: registry() }])
      expect(lookup?.publishedAt).toBe(Date.parse(PROXY_TIME))
    } finally {
      delete process.env['HTTP_PROXY']
      delete process.env['NO_PROXY']
    }
  })
})
