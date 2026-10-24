import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http'
import type { AddressInfo } from 'node:net'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { type Lookup, publicationTimesOf } from '@/registry.js'
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

const registryServer: Server = createServer((incoming: IncomingMessage, response: ServerResponse): void => {
  const location: string = incoming.url ?? '/'
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

const portOf = (server: Server): string => String((server.address() as AddressInfo).port)
const registry = (): string => `http://127.0.0.1:${portOf(registryServer)}/`

beforeAll(async (): Promise<void> => {
  for (const server of [registryServer, proxyServer]) {
    await new Promise<void>((resolve: () => void): void => {
      server.listen(0, '127.0.0.1', resolve)
    })
  }
})

afterAll(async (): Promise<void> => {
  for (const server of [registryServer, proxyServer]) {
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
