import path from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  asRecord,
  asStringRecord,
  declaredDependencies,
  type ParsedJson,
  parseJsonc,
  readJsonFile,
} from '@/json-shapes.js'
import { projectForTest } from './helpers/temporary-project.js'

describe('parseJsonc', () => {
  it('reads comments and trailing commas, and keeps a comma inside a string', () => {
    expect(parseJsonc('{ "a": "{ts,}", // note\n "b": [1, 2,], }').value).toStrictEqual({ a: '{ts,}', b: [1, 2] })
  })

  it("reports the parser's reason for text that is no JSON", () => {
    const parsed: ParsedJson = parseJsonc('{ not json')
    expect(parsed.value).toBeUndefined()
    expect(parsed.problem).toBeDefined()
  })
})

describe('readJsonFile', () => {
  it('reads a file, and an absent one as undefined', () => {
    const root: string = projectForTest({ 'a.json': '{ "a": 1 }' })
    expect(readJsonFile(path.join(root, 'a.json'))).toStrictEqual({ a: 1 })
    expect(readJsonFile(path.join(root, 'missing.json'))).toBeUndefined()
  })
})

describe('the manifest readers', () => {
  it('read a value that is no object as an empty one', () => {
    expect(asRecord(null)).toStrictEqual({})
    expect(asStringRecord({ a: 'x', b: 1 })).toStrictEqual({ a: 'x' })
  })

  it('read every installed dependency block, and no peer', () => {
    expect(
      declaredDependencies({
        dependencies: { a: '1' },
        devDependencies: { b: '2' },
        optionalDependencies: { c: '3' },
        peerDependencies: { d: '4' },
      }),
    ).toStrictEqual({ a: '1', b: '2', c: '3' })
  })
})
