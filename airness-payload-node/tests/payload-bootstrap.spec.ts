import type { SpecSource } from '@airness/node/axe-coverage'
import { describe, expect, it } from 'vitest'
import { findBootstrapWrites } from '@/payload-bootstrap.js'
import type { LocatedViolation } from '@/relationship-cleanup.js'

const CONFIG_PATH: string = 'src/payload.config.ts'

const configWith = (hook: string): string =>
  ['export default buildConfig({', '  collections: [Users],', `  onInit: ${hook},`, '})'].join('\n')

const file = (path: string, source: string): SpecSource => ({ path, source })

const rulesOf = (files: readonly SpecSource[]): readonly string[] =>
  findBootstrapWrites(files).map((located: LocatedViolation): string => located.violation.rule)

const reasonsOf = (files: readonly SpecSource[]): readonly string[] =>
  findBootstrapWrites(files).map((located: LocatedViolation): string => located.violation.reason)

const SEEDER: string = 'export async function seedEverything(payload) { await payload.create({}) }'

describe('findBootstrapWrites over the operations that change stored state', () => {
  it.each(['updateGlobal', 'create'])('reports %s as a write', (operation: string) => {
    const config: string = configWith(`async (payload) => { await payload.${operation}({}) }`)
    const reasons: readonly string[] = reasonsOf([file(CONFIG_PATH, config)])
    expect(reasons).toHaveLength(1)
    expect(reasons[0]).toContain(`${operation}() here runs on every boot`)
  })

  // A read at boot is legitimate, so the derived subset must not carry one.
  it.each(['find', 'findGlobal'])('does not report %s, which only reads', (operation: string) => {
    const config: string = configWith(`async (payload) => { await payload.${operation}({}) }`)
    expect(rulesOf([file(CONFIG_PATH, config)])).toEqual([])
  })
})

describe('findBootstrapWrites over the spellings of a boot hook', () => {
  it('reads the body of an arrow hook', () => {
    expect(rulesOf([file(CONFIG_PATH, configWith('async (payload) => { await payload.create({}) }'))])).toEqual([
      'no-boot-time-writes',
    ])
  })

  // A destructured parameter opens a brace before the body does, so the body cannot be the first one.
  it('reads the body of a hook whose parameter is destructured', () => {
    const config: string = configWith('async ({ payload }) => { await payload.create({}) }')
    expect(rulesOf([file(CONFIG_PATH, config)])).toEqual(['no-boot-time-writes'])
  })

  // A hook written as a function expression opens no arrow, so the body is the first brace after it.
  it('reads the body of a hook written as a function expression', () => {
    const config: string = configWith('async function (payload) { await payload.create({}) }')
    expect(rulesOf([file(CONFIG_PATH, config)])).toEqual(['no-boot-time-writes'])
  })

  // A hook named by reference runs the function it names; the write is followed into the module that
  // declares it and located on the configuration's own line.
  it('reads a hook given by reference as the name it gives', () => {
    const config: string = ["import { seedEverything } from './seed'", configWith('seedEverything')].join('\n')
    const found: readonly LocatedViolation[] = findBootstrapWrites([
      file(CONFIG_PATH, config),
      file('src/seed.ts', SEEDER),
    ])
    expect(found).toHaveLength(1)
    expect(found[0]?.violation.line).toBe(2)
    expect(found[0]?.violation.reason).toContain('it calls seedEverything() from "./seed"')
  })

  it('reads the shorthand hook as a reference to onInit', () => {
    const config: string = [
      "import { onInit } from './seed'",
      'export default buildConfig({\n  collections: [Users],\n  onInit,\n})',
    ].join('\n')
    const seed: string = 'export const onInit = async (payload) => { await payload.create({}) }'
    expect(reasonsOf([file(CONFIG_PATH, config), file('src/seed.ts', seed)])[0]).toContain('it calls onInit()')
  })

  // The value ends at its own comma, so a later key's literal is never read as the hook's body.
  it('reads no later literal as the body of a hook given by reference', () => {
    const config: string = configWith('seedEverything,\n  admin: async (payload) => { await payload.create({}) }')
    expect(rulesOf([file(CONFIG_PATH, config)])).toEqual([])
  })

  it('reads a reference to a function the configuration declares as that body', () => {
    const config: string = [SEEDER.replace('export ', ''), configWith('seedEverything')].join('\n')
    expect(reasonsOf([file(CONFIG_PATH, config)])[0]).toContain('create() here runs on every boot')
  })

  it('reads the expression an arrow without a block returns as its body', () => {
    const config: string = configWith('async (payload) => payload.create({})')
    expect(reasonsOf([file(CONFIG_PATH, config)])[0]).toContain('create() here runs on every boot')
  })

  it.each(['onInit(payload) { await payload.create({}) }', 'async onInit(payload) { await payload.create({}) }'])(
    'reads the body of a hook written as the method %s',
    (method: string) => {
      const config: string = `export default buildConfig({\n  collections: [Users],\n  ${method},\n})`
      expect(rulesOf([file(CONFIG_PATH, config)])).toEqual(['no-boot-time-writes'])
    },
  )

  it('finds nothing when the hook value is neither a function nor a name', () => {
    const config: string = ["import { onInit } from './seed'", configWith('hooks.onInit')].join('\n')
    const seed: string = 'export const onInit = async (payload) => { await payload.create({}) }'
    expect(rulesOf([file(CONFIG_PATH, config), file('src/seed.ts', seed)])).toEqual([])
  })
})

describe('findBootstrapWrites', () => {
  // The repaired shape: a hook may repair what is missing, and a read is how it finds out.
  it('accepts a hook that only reads', () => {
    const config: string = configWith(
      'async (payload) => { await payload.find({ collection: "users", depth: 0, limit: 1 }) }',
    )
    expect(rulesOf([file(CONFIG_PATH, config)])).toEqual([])
  })

  it('accepts a configuration that declares no hook', () => {
    expect(rulesOf([file(CONFIG_PATH, 'export default buildConfig({ collections: [] })')])).toEqual([])
  })

  it('reports a write the hook performs itself', () => {
    const config: string = configWith('async (payload) => { await payload.updateGlobal({}) }')
    expect(rulesOf([file(CONFIG_PATH, config)])).toEqual(['no-boot-time-writes'])
  })

  // The shape a real project shipped: the hook calls an imported function that writes in its own body.
  it('reports a write the hook reaches in one call', () => {
    const config: string = [
      "import { seedContent } from './seed/seed'",
      configWith('async (payload) => { await seedContent(payload) }'),
    ].join('\n')
    const seed: string = 'export async function seedContent(payload) { await payload.updateGlobal({}) }'
    expect(rulesOf([file(CONFIG_PATH, config), file('src/seed/seed.ts', seed)])).toEqual(['no-boot-time-writes'])
  })

  it('names the reached function and its module, so the finding is actionable', () => {
    const config: string = [
      "import { seedContent } from './seed/seed'",
      configWith('async (payload) => { await seedContent(payload) }'),
    ].join('\n')
    const seed: string = 'export async function seedContent(payload) { await payload.updateGlobal({}) }'
    const found: readonly LocatedViolation[] = findBootstrapWrites([
      file(CONFIG_PATH, config),
      file('src/seed/seed.ts', seed),
    ])
    expect(found[0]?.violation.reason).toContain('seedContent')
    expect(found[0]?.violation.reason).toContain('./seed/seed')
  })

  it('reports against the configuration that schedules the write, not the module that performs it', () => {
    const config: string = [
      "import { seedContent } from './seed/seed'",
      configWith('async (payload) => { await seedContent(payload) }'),
    ].join('\n')
    const seed: string = 'export async function seedContent(payload) { await payload.updateGlobal({}) }'
    const found: readonly LocatedViolation[] = findBootstrapWrites([
      file(CONFIG_PATH, config),
      file('src/seed/seed.ts', seed),
    ])
    expect(found[0]?.path).toBe(CONFIG_PATH)
  })

  // A seed that writes two globals performs one operation twice. The repair is one change, so the
  // finding is one line - the real configuration this rule was written for writes exactly this way.
  it('reports one finding for a body that performs the same operation twice', () => {
    const config: string = [
      "import { seedContent } from './seed/seed'",
      configWith('async (payload) => { await seedContent(payload) }'),
    ].join('\n')
    const seed: string = [
      'export async function seedContent(payload) {',
      '  await payload.updateGlobal({ slug: "navigation" })',
      '  await payload.updateGlobal({ slug: "configuration" })',
      '}',
    ].join('\n')
    expect(rulesOf([file(CONFIG_PATH, config), file('src/seed/seed.ts', seed)])).toEqual(['no-boot-time-writes'])
  })

  it('accepts a reached function whose body only reads', () => {
    const config: string = [
      "import { warm } from './seed/seed'",
      configWith('async (payload) => { await warm(payload) }'),
    ].join('\n')
    const seed: string = 'export async function warm(payload) { await payload.find({}) }'
    expect(rulesOf([file(CONFIG_PATH, config), file('src/seed/seed.ts', seed)])).toEqual([])
  })

  // A module may hold a writer the hook never calls; only the export it reaches is judged.
  it('does not report an unrelated writer the hook never calls', () => {
    const config: string = [
      "import { warm } from './seed/seed'",
      configWith('async (payload) => { await warm(payload) }'),
    ].join('\n')
    const seed: string = [
      'export async function warm(payload) { await payload.find({}) }',
      'export async function reset(payload) { await payload.updateGlobal({}) }',
    ].join('\n')
    expect(rulesOf([file(CONFIG_PATH, config), file('src/seed/seed.ts', seed)])).toEqual([])
  })

  it('says nothing when the imported module is not one of this member files', () => {
    const config: string = [
      "import { seedContent } from 'some-plugin'",
      configWith('async (payload) => { await seedContent(payload) }'),
    ].join('\n')
    expect(rulesOf([file(CONFIG_PATH, config)])).toEqual([])
  })

  // Every spelling of a hook that hands the work to an imported seeder runs that seeder at boot.
  it.each([
    ['a reference', 'onInit: seedContent'],
    ['an arrow that returns the call', 'onInit: async (payload) => seedContent(payload)'],
    ['a destructuring arrow that returns the call', 'onInit: async ({ payload }) => seedContent(payload)'],
    ['a function expression', 'onInit: async function (payload) { await seedContent(payload) }'],
    ['a method', 'async onInit(payload) { await seedContent(payload) }'],
  ])('reports a write reached through %s', (_spelling: string, hook: string) => {
    const config: string = [
      "import { seedContent } from './seed/seed'",
      `export default buildConfig({\n  collections: [Users],\n  ${hook},\n})`,
    ].join('\n')
    const seed: string = 'export async function seedContent(payload) { await payload.updateGlobal({}) }'
    expect(rulesOf([file(CONFIG_PATH, config), file('src/seed/seed.ts', seed)])).toEqual(['no-boot-time-writes'])
  })

  it('reports a write reached through the shorthand hook', () => {
    const config: string = [
      "import { onInit } from './seed/seed'",
      'export default buildConfig({\n  collections: [Users],\n  onInit,\n})',
    ].join('\n')
    const seed: string = 'export const onInit = async (payload) => { await payload.create({}) }'
    expect(rulesOf([file(CONFIG_PATH, config), file('src/seed/seed.ts', seed)])).toEqual(['no-boot-time-writes'])
  })

  it('reports a write an arrow without a block performs itself', () => {
    const config: string = configWith("async ({ payload }) => payload.create({ collection: 'pages', data: {} })")
    expect(rulesOf([file(CONFIG_PATH, config)])).toEqual(['no-boot-time-writes'])
  })

  it('reports a write in a local function the hook names', () => {
    const config: string = [
      'async function seedEverything(payload) { await payload.updateGlobal({}) }',
      configWith('seedEverything'),
    ].join('\n')
    expect(rulesOf([file(CONFIG_PATH, config)])).toEqual(['no-boot-time-writes'])
  })

  // A seeder that opens its own instance writes through that instance rather than the parameter.
  it('reports a write through an instance the reached seeder opens itself', () => {
    const config: string = ["import { seedContent } from '@/seed'", configWith('seedContent')].join('\n')
    const seed: string = [
      'export const seedContent = async () => {',
      '  const cms = await getPayload({ config })',
      "  await cms.create({ collection: 'pages', data: {} })",
      '}',
    ].join('\n')
    expect(rulesOf([file(CONFIG_PATH, config), file('src/seed/index.ts', seed)])).toEqual(['no-boot-time-writes'])
  })

  it('accepts a hook that names an imported function which only reads', () => {
    const config: string = ["import { warm } from './seed/seed'", configWith('warm')].join('\n')
    const seed: string = 'export async function warm(payload) { await payload.find({}) }'
    expect(rulesOf([file(CONFIG_PATH, config), file('src/seed/seed.ts', seed)])).toEqual([])
  })

  it('ignores a hook written inside a comment', () => {
    const config: string = '// onInit: async (payload) => { await payload.updateGlobal({}) }'
    expect(rulesOf([file(CONFIG_PATH, config)])).toEqual([])
  })
})
