import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { readJsonFile } from '@/json-file.js'
import { projectForTest } from './helpers/temporary-project.js'

describe('readJsonFile', () => {
  it('reads a file with comments and trailing commas, and an absent one as undefined', () => {
    const root: string = projectForTest({ 'a.json': '{ "a": 1, // note\n "b": [2,], }', 'broken.json': '{ not json' })
    expect(readJsonFile(path.join(root, 'a.json'))).toStrictEqual({ a: 1, b: [2] })
    expect(readJsonFile(path.join(root, 'broken.json'))).toBeUndefined()
    expect(readJsonFile(path.join(root, 'missing.json'))).toBeUndefined()
  })
})
