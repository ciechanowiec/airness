import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { GATES } from '@/gates.js'

// The repository beside this package: the parent that binds the gates, and the fixture run that asserts them.
const repository: string = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

const gateIds: readonly string[] = GATES.map((gate): string => gate.id)

const boundIds = (): readonly string[] => {
  const pom: string = readFileSync(path.join(repository, 'airness-parent-payload', 'pom.xml'), 'utf8')
  return [...pom.matchAll(/<gate>([a-z-]+)<\/gate>/gu)].map((match: RegExpExecArray): string => String(match[1]))
}

const commandIds = (): readonly string[] => {
  const pom: string = readFileSync(path.join(repository, 'airness-parent-payload', 'pom.xml'), 'utf8')
  return [...pom.matchAll(/<command>airness-payload gate ([a-z-]+)<\/command>/gu)].map(
    (match: RegExpExecArray): string => String(match[1]),
  )
}

const assertedIds = (): readonly string[] => {
  const script: string = readFileSync(path.join(repository, 'airness-it', 'cases', 'payload.sh'), 'utf8')
  const list: string = /payload_gates='([^']*)'/u.exec(script)?.[1] ?? ''
  return list.split(/\s+/u).filter((id: string): boolean => id.length > 0)
}

describe('the Payload gate list', () => {
  it('is bound by airness-parent-payload, one node-gate execution per gate, in registry order', () => {
    expect(boundIds()).toStrictEqual(gateIds)
    expect(commandIds()).toStrictEqual(gateIds)
  })

  it('is the list the Payload fixtures assert, in registry order', () => {
    expect(assertedIds()).toStrictEqual(gateIds)
  })
})
