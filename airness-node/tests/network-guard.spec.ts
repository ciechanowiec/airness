import dns from 'node:dns'
import { createServer } from 'node:http'
import { describe, expect, it } from 'vitest'
import { PROCESS_TIME } from './helpers/process-time.js'

describe('the worker network guard', PROCESS_TIME, () => {
  it('blocks external fetches before they reach a socket for each supported input form', () => {
    expect(() => fetch('https://example.invalid/')).toThrow('A test reaches no network beyond the machine')
    expect(() => fetch(new URL('https://example.invalid/'))).toThrow('A test reaches no network beyond the machine')
    expect(() => fetch(new Request('https://example.invalid/'))).toThrow('A test reaches no network beyond the machine')
  })

  it('blocks DNS resolver escape routes and cannot be removed from socket or DNS operations', () => {
    expect(() => dns.promises.resolveSrv('example.invalid')).toThrow('A test reaches no network beyond the machine')
    expect(() => new dns.promises.Resolver().resolveSrv('example.invalid')).toThrow(
      'A test reaches no network beyond the machine',
    )
    expect(Object.getOwnPropertyDescriptor(dns, 'lookup')?.configurable).toBe(false)
    expect(Object.getOwnPropertyDescriptor(dns, 'lookup')?.writable).toBe(false)
  })

  it('still reaches a real HTTP service on the same machine', async () => {
    const server: ReturnType<typeof createServer> = createServer((_, response): void => {
      response.end('local')
    })
    await new Promise<void>((resolve): void => {
      server.listen(0, '127.0.0.1', resolve)
    })
    try {
      const address: ReturnType<typeof server.address> = server.address()
      if (address === null || typeof address === 'string') {
        throw new Error('No loopback port was allocated')
      }
      const response: Response = await fetch(`http://127.0.0.1:${String(address.port)}/`)
      expect(await response.text()).toBe('local')
    } finally {
      await new Promise<void>((resolve, reject): void => {
        server.close((error): void => {
          if (error === undefined) {
            resolve()
          } else {
            reject(error)
          }
        })
      })
    }
  })
})
