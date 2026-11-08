import type { SpecSource } from '@airness/node/axe-coverage'
import { describe, expect, it } from 'vitest'
import {
  bodyAfterParameters,
  calledNamesIn,
  candidatePathsFor,
  functionBodyIn,
  functionValueBody,
  moduleOfImport,
  type ReachedFunction,
  reachedFunction,
  reachedFunctions,
  valueEnd,
} from '@/source-reach.js'

const CONFIG_PATH: string = 'src/payload.config.ts'

const file = (path: string, source: string): SpecSource => ({ path, source })

describe('calledNamesIn', () => {
  it('names each called function once, without the statement keywords', () => {
    expect(calledNamesIn('if (ready) { await seed(a); await seed(b); return warm() }')).toEqual(['seed', 'warm'])
  })
})

describe('moduleOfImport', () => {
  it('reads the specifier a name is imported from', () => {
    expect(moduleOfImport("import { seedContent } from './seed/seed'", 'seedContent')).toBe('./seed/seed')
  })

  it('reads a renamed binding by the name the file uses', () => {
    expect(moduleOfImport("import { seed as seedContent } from '@/seed'", 'seedContent')).toBe('@/seed')
  })

  it('is undefined for a name the file never imports', () => {
    expect(moduleOfImport("import { other } from '@/seed'", 'seedContent')).toBeUndefined()
  })
})

describe('candidatePathsFor', () => {
  it('resolves the alias against the source root', () => {
    expect(candidatePathsFor('@/seed/seed', CONFIG_PATH)).toContain('src/seed/seed.ts')
  })

  // The real defect imported its seed relatively, so this form has to resolve or the rule misses it.
  it('resolves a relative specifier against the importing file', () => {
    expect(candidatePathsFor('./seed/seed', CONFIG_PATH)).toContain('src/seed/seed.ts')
  })

  it('climbs out of a directory for a parent-relative specifier', () => {
    expect(candidatePathsFor('../seed', 'src/config/payload.config.ts')).toContain('src/seed.ts')
  })

  it('offers the barrel as a candidate', () => {
    expect(candidatePathsFor('@/seed', CONFIG_PATH)).toContain('src/seed/index.ts')
  })

  it('names nothing for a package specifier', () => {
    expect(candidatePathsFor('payload', CONFIG_PATH)).toEqual([])
  })

  // A configuration at the member root has no directory to resolve against.
  it('resolves a relative specifier from a file that sits at the root', () => {
    expect(candidatePathsFor('./seed', 'payload.config.ts')).toContain('seed.ts')
  })

  it('collapses a redundant current-directory segment', () => {
    expect(candidatePathsFor('././seed', CONFIG_PATH)).toContain('src/seed.ts')
  })
})

describe('valueEnd', () => {
  it('ends a value at its own comma, past every comma nested in it', () => {
    const value: string = ' seed({ a: 1, b: 2 }), admin: {}'
    expect(value.slice(0, valueEnd(value))).toBe(' seed({ a: 1, b: 2 })')
  })

  it('ends a value at the bracket that closes the construct holding it', () => {
    expect(' seed\n}'.slice(0, valueEnd(' seed\n}'))).toBe(' seed\n')
  })

  it('ends a statement at a line break only when asked, and never before a member access', () => {
    const text: string = 'payload\n  .find({})\nconst next = 1'
    expect(text.slice(0, valueEnd(text, true))).toBe('payload\n  .find({})')
    expect(text.slice(0, valueEnd(text))).toBe(text)
  })
})

describe('functionValueBody', () => {
  it.each([
    ['an arrow with a block', 'async (payload) => { await warm(payload) }'],
    ['an arrow with a destructured parameter', 'async ({ payload }) => { await warm(payload) }'],
    ['an arrow with a bare parameter', 'payload => { warm(payload) }'],
    ['an arrow with a return type', 'async (payload): Promise<void> => { await warm(payload) }'],
    ['a function expression', 'async function (payload) { await warm(payload) }'],
  ])('reads the body of %s', (_spelling: string, value: string) => {
    expect(functionValueBody(value)).toContain('warm(payload)')
  })

  it('reads the expression an arrow without a block returns', () => {
    expect(functionValueBody('async (payload) => warm(payload)')).toBe('warm(payload)')
  })

  it('is undefined for a value that is no function', () => {
    expect(functionValueBody('seedEverything')).toBeUndefined()
    expect(functionValueBody('1')).toBeUndefined()
  })
})

describe('bodyAfterParameters', () => {
  it('skips a destructured parameter to the body', () => {
    expect(bodyAfterParameters('onInit({ payload }) { warm(payload) }', 0)).toBe(' warm(payload) ')
  })
})

describe('functionBodyIn', () => {
  it.each([
    'export async function seed(payload) { await payload.create({}) }',
    'export const seed = async (payload) => { await payload.create({}) }',
    'async function seed(payload) { await payload.create({}) }\nexport { seed }',
    'export const seed: (p: Payload) => Promise<void> = async (p) => { await p.create({}) }',
  ])('reads the body of every spelling of an exported function: %s', (source: string) => {
    expect(functionBodyIn(source, 'seed')).toContain('create')
  })

  // The brace of the options literal must not be read as the body, which would hide the call itself.
  it('reads the expression an exported arrow without a block returns, and nothing after it', () => {
    const source: string = [
      "export const seed = async (payload) => payload.create({ collection: 'pages' })",
      'export const other = () => payload.delete({})',
    ].join('\n')
    expect(functionBodyIn(source, 'seed')).toBe("payload.create({ collection: 'pages' })")
  })

  it('does not read a longer name that starts with the one asked for', () => {
    expect(functionBodyIn('export const seedAll = () => { run() }', 'seed')).toBeUndefined()
  })

  it('is undefined for a binding the module does not declare', () => {
    expect(functionBodyIn('export const other = 1', 'seed')).toBeUndefined()
  })

  // An export that opens no body at all: the declaration matches, and there is nothing to read.
  it('is undefined for an export that opens no body', () => {
    expect(functionBodyIn('export const seed = 1', 'seed')).toBeUndefined()
  })
})

describe('reachedFunction', () => {
  it('follows an imported name into the module of the member that declares it', () => {
    const code: string = "import { listPosts } from '@/lib/posts'"
    const posts: SpecSource = file('src/lib/posts.ts', 'export const listPosts = async () => { await find() }')
    const reached: ReachedFunction | undefined = reachedFunction(code, 'listPosts', 'src/endpoints/x.ts', [posts])
    expect([reached?.path, reached?.specifier, reached?.body]).toEqual([
      'src/lib/posts.ts',
      '@/lib/posts',
      ' await find() ',
    ])
  })

  it('is undefined for a name imported from a package', () => {
    expect(reachedFunction("import { getPayload } from 'payload'", 'getPayload', CONFIG_PATH, [])).toBeUndefined()
  })

  it('is undefined for a module that declares no such function', () => {
    const code: string = "import { listPosts } from '@/lib/posts'"
    expect(reachedFunction(code, 'listPosts', CONFIG_PATH, [file('src/lib/posts.ts', 'export {}')])).toBeUndefined()
  })
})

describe('reachedFunctions', () => {
  it('follows only the names the body calls', () => {
    const code: string = "import { warm, reset } from '@/seed'"
    const seed: SpecSource = file('src/seed.ts', 'export function warm() { a() }\nexport function reset() { b() }')
    expect(reachedFunctions(code, 'await warm()', CONFIG_PATH, [seed]).map((found) => found.name)).toEqual(['warm'])
  })
})
