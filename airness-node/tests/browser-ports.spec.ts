import { createServer, type Server } from 'node:net'
import { describe, expect, it } from 'vitest'
import { freePorts } from '@/browser-origin.js'
import { PROCESS_TIME } from './helpers/process-time.js'

const canBind = async (port: number): Promise<boolean> =>
  await new Promise<boolean>((resolve) => {
    const server: Server = createServer()
    server.once('error', () => {
      resolve(false)
    })
    server.listen(port, '127.0.0.1', () => {
      server.close(() => {
        resolve(true)
      })
    })
  })

describe('free loopback ports', PROCESS_TIME, () => {
  it('finds distinct ports and releases them for the servers they are meant for', async () => {
    const ports: readonly number[] = await freePorts(3)
    expect(new Set<number>(ports).size).toBe(3)
    for (const port of ports) {
      expect(port).toBeGreaterThan(0)
      expect(await canBind(port)).toBe(true)
    }
  })

  it('never offers a port the caller already holds for its own servers', async () => {
    const earlier: readonly number[] = await freePorts(4)
    const later: readonly number[] = await freePorts(4, earlier)
    expect(later.filter((port: number): boolean => earlier.includes(port))).toEqual([])
  })

  it('answers a request for none with none', async () => {
    expect(await freePorts(0)).toEqual([])
  })
})
