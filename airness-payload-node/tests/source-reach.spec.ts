import type { SpecSource } from '@airness/node/axe-coverage'
import { describe, expect, it } from 'vitest'
import {
  bodyAfterParameters,
  functionBodyIn,
  functionValueBody,
  type ReachedFunction,
  reachedFunction,
  reachedFunctions,
  valueEnd,
} from '@/source-reach.js'

const CONFIG_PATH: string = 'src/payload.config.ts'

const file = (path: string, source: string): SpecSource => ({ path, source })

// A module that declares one function under a name, for a specifier to resolve to.
const declaring = (path: string, name: string): SpecSource =>
  file(path, `export const ${name} = async () => { await run() }`)

const reachedPathOf = (specifier: string, fromPath: string, files: readonly SpecSource[]): string | undefined =>
  reachedFunction(`import { seed } from '${specifier}'`, 'seed', fromPath, files)?.path

describe('reachedFunctions over the names a body calls', () => {
  it('follows each called function once, without the statement keywords', () => {
    const code: string = "import { seed, warm, ready } from '@/seed'"
    const module: SpecSource = file(
      'src/seed.ts',
      ['seed', 'warm', 'ready'].map((name: string): string => `export function ${name}() { a() }`).join('\n'),
    )
    const body: string = 'if (ready) { await seed(a); await seed(b); return warm() }'
    expect(reachedFunctions(code, body, CONFIG_PATH, [module]).map((found) => found.name)).toEqual(['seed', 'warm'])
  })
})

describe('reachedFunction over the import that binds a name', () => {
  it('reads the specifier a name is imported from', () => {
    const code: string = "import { seedContent } from './seed/seed'"
    const reached: ReachedFunction | undefined = reachedFunction(code, 'seedContent', CONFIG_PATH, [
      declaring('src/seed/seed.ts', 'seedContent'),
    ])
    expect(reached?.specifier).toBe('./seed/seed')
  })

  it('reads a renamed binding by the name the file uses', () => {
    const code: string = "import { seed as seedContent } from '@/seed'"
    const module: SpecSource = file(
      'src/seed.ts',
      'const seedContent = () => { run() }\nexport { seedContent as seed }',
    )
    expect(reachedFunction(code, 'seedContent', CONFIG_PATH, [module])?.specifier).toBe('@/seed')
  })

  it('is undefined for a name the file never imports', () => {
    const code: string = "import { other } from '@/seed'"
    expect(reachedFunction(code, 'seedContent', CONFIG_PATH, [declaring('src/seed.ts', 'seedContent')])).toBeUndefined()
  })
})

describe('reachedFunction over the paths a specifier names', () => {
  it('resolves the alias against the source root', () => {
    expect(reachedPathOf('@/seed/seed', CONFIG_PATH, [declaring('src/seed/seed.ts', 'seed')])).toBe('src/seed/seed.ts')
  })

  // The real defect imported its seed relatively, so this form has to resolve or the rule misses it.
  it('resolves a relative specifier against the importing file', () => {
    expect(reachedPathOf('./seed/seed', CONFIG_PATH, [declaring('src/seed/seed.ts', 'seed')])).toBe('src/seed/seed.ts')
  })

  it('climbs out of a directory for a parent-relative specifier', () => {
    const files: readonly SpecSource[] = [declaring('src/seed.ts', 'seed')]
    expect(reachedPathOf('../seed', 'src/config/payload.config.ts', files)).toBe('src/seed.ts')
  })

  it('offers the barrel as a candidate', () => {
    expect(reachedPathOf('@/seed', CONFIG_PATH, [declaring('src/seed/index.ts', 'seed')])).toBe('src/seed/index.ts')
  })

  it('names nothing for a package specifier, whatever the member holds', () => {
    const files: readonly SpecSource[] = [
      declaring('payload.ts', 'seed'),
      declaring('src/payload.ts', 'seed'),
      declaring('payload/index.ts', 'seed'),
      declaring('src/payload/index.ts', 'seed'),
    ]
    expect(reachedPathOf('payload', CONFIG_PATH, files)).toBeUndefined()
  })

  // A configuration at the member root has no directory to resolve against.
  it('resolves a relative specifier from a file that sits at the root', () => {
    expect(reachedPathOf('./seed', 'payload.config.ts', [declaring('seed.ts', 'seed')])).toBe('seed.ts')
  })

  it('collapses a redundant current-directory segment', () => {
    expect(reachedPathOf('././seed', CONFIG_PATH, [declaring('src/seed.ts', 'seed')])).toBe('src/seed.ts')
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
