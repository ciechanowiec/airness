// The Payload ESLint configuration composes onto the node layer's: the harness admits a stack layer only when its
// export starts with the node layer's own blocks, and a Payload selector added to `no-restricted-syntax` must not
// drop a selector the node layer set for the same files.

import path from 'node:path'
import nodeConfig from '@airness/node/eslint'
import { ESLint, Linter } from 'eslint'
import { describe, expect, it } from 'vitest'
import config from '@/eslint.js'

const RESTRICTED_SYNTAX: string = 'no-restricted-syntax'

// The opening of the message every request-body selector carries.
const REQUEST_DATA_MESSAGE: string = 'Read req.data only in an endpoint handler'

type ConfigBlock = (typeof nodeConfig)[number]

const restrictedSyntaxOf = (block: ConfigBlock): readonly unknown[] | undefined => {
  const setting: unknown = block.rules?.[RESTRICTED_SYNTAX]
  return Array.isArray(setting) ? setting : undefined
}

const selectorsOf = (setting: readonly unknown[]): readonly unknown[] =>
  setting.slice(1).map((option: unknown): unknown => (option as { readonly selector?: unknown }).selector)

const nodeSettings: readonly ConfigBlock[] = nodeConfig.filter((block) => restrictedSyntaxOf(block) !== undefined)

/** What a Payload extension block does with the node setting it follows. */
interface Extension {
  readonly scope: readonly unknown[]
  readonly keepsNode: boolean
  readonly payloadSelectors: number
  readonly readsBody: boolean
}

const extensionOf = (block: ConfigBlock, node: ConfigBlock | undefined): Extension => {
  const setting: readonly unknown[] = restrictedSyntaxOf(block) ?? []
  const extended: readonly unknown[] = selectorsOf(setting)
  return {
    scope: [block.files, block.ignores],
    keepsNode: selectorsOf(restrictedSyntaxOf(node ?? {}) ?? []).every((selector) => extended.includes(selector)),
    payloadSelectors: extended.filter((selector) => String(selector).includes("name='payload'")).length,
    readsBody: JSON.stringify(setting).includes(REQUEST_DATA_MESSAGE),
  }
}

describe('the Payload ESLint configuration', () => {
  it('starts with the node layer blocks themselves, which is the only shape the harness composes', () => {
    expect(config.slice(0, nodeConfig.length).every((block, index) => block === nodeConfig[index])).toBe(true)
  })

  // Each node setting is followed by its extension and by the extension's twin, which adds the request-body
  // selectors for every file but the endpoint handlers.
  it('extends every node restricted-syntax setting, in the node order, with the request selectors', () => {
    const extensions: readonly ConfigBlock[] = config.slice(
      nodeConfig.length,
      nodeConfig.length + 2 * nodeSettings.length,
    )
    expect(nodeSettings.length).toBeGreaterThan(0)
    expect(
      extensions.map((block: ConfigBlock, index: number) => extensionOf(block, nodeSettings[Math.floor(index / 2)])),
    ).toEqual(
      nodeSettings.flatMap((node: ConfigBlock) => [
        { scope: [node.files, node.ignores], keepsNode: true, payloadSelectors: 5, readsBody: false },
        {
          scope: [node.files, [...(node.ignores ?? []), 'src/endpoints/**']],
          keepsNode: true,
          payloadSelectors: 5,
          readsBody: true,
        },
      ]),
    )
  })

  // Every equivalent spelling of a TypeScript source is governed: a page reading NEXT_PUBLIC_SERVER_URL is
  // the read the environment gate must see, and the App Router writes pages in .tsx.
  it.each(['src/app/(frontend)/page.tsx', 'src/lib/site.ts', 'src/lib/site.mts', 'src/lib/site.cts'])(
    'refuses a process.env read in %s',
    (file: string) => {
      const environmentBlocks: readonly ConfigBlock[] = config.filter((block) =>
        JSON.stringify(block.rules?.['no-restricted-properties'] ?? []).includes('"property":"env"'),
      )
      const globs: readonly string[] = environmentBlocks.flatMap((block) => (block.files ?? []) as readonly string[])
      expect(globs.some((glob: string) => path.matchesGlob(file, glob))).toBe(true)
    },
  )

  // A handler that names its parameter `request` hands out the same request-scoped instance as `req`.
  it.each(['req', 'request'])('refuses payload destructured out of %s', (name: string) => {
    const payloadSelectors: readonly string[] = config
      .flatMap((block) => selectorsOf(restrictedSyntaxOf(block) ?? []))
      .map(String)
      .filter((selector: string) => selector.includes("key.name='payload'"))
    const requestPatterns: readonly RegExp[] = payloadSelectors.map((selector: string) => {
      const literal: string = /=\/(.+?)\/\]/u.exec(selector)?.[1] ?? '(?!)'
      return new RegExp(literal, 'u')
    })
    expect(requestPatterns.length).toBeGreaterThan(0)
    expect(requestPatterns.every((pattern: RegExp) => pattern.test(name))).toBe(true)
    expect(requestPatterns.some((pattern: RegExp) => pattern.test(`${name}s`))).toBe(false)
  })
})

// The request selectors run by ESLint itself, with the parser the shipped configuration parses TypeScript with, so
// each spelling below is judged by the selector engine that judges a project rather than by reading the text.
const requestOptions: readonly unknown[] = [
  ...new Map(
    config
      .flatMap((block: ConfigBlock): readonly unknown[] => restrictedSyntaxOf(block)?.slice(1) ?? [])
      .filter((option: unknown): boolean => JSON.stringify(option).includes('req.payload.<operation>'))
      .map((option: unknown): readonly [string, unknown] => [JSON.stringify(option), option]),
  ).values(),
]

const isParser = (value: unknown): value is Linter.Parser =>
  typeof value === 'object' && value !== null && ('parse' in value || 'parseForESLint' in value)

const typeScriptParser: Linter.Parser | undefined = config
  .map((block: ConfigBlock): unknown => block.languageOptions?.['parser'])
  .find((parser: unknown): parser is Linter.Parser => isParser(parser))

const optionsCarrying = (text: string): readonly unknown[] => [
  ...new Map(
    config
      .flatMap((block: ConfigBlock): readonly unknown[] => restrictedSyntaxOf(block)?.slice(1) ?? [])
      .filter((option: unknown): boolean => JSON.stringify(option).includes(text))
      .map((option: unknown): readonly [string, unknown] => [JSON.stringify(option), option]),
  ).values(),
]

const requestDataOptions: readonly unknown[] = optionsCarrying(REQUEST_DATA_MESSAGE)

const linesReportedBy = (options: readonly unknown[], code: string): readonly number[] =>
  new Linter({ configType: 'flat' })
    .verify(
      code,
      [
        {
          files: ['**/*.ts'],
          languageOptions: typeScriptParser === undefined ? {} : { parser: typeScriptParser },
          rules: { [RESTRICTED_SYNTAX]: ['error', ...options] },
        },
      ],
      'src/hooks/audit.ts',
    )
    .map((message: Linter.LintMessage): number => message.line)

const reportedLines = (code: string): readonly number[] => linesReportedBy(requestOptions, code)

// The restricted-syntax setting ESLint resolves for a file of a project, block by block as a project run does.
const resolvedSyntax = async (file: string): Promise<string> => {
  const eslint: ESLint = new ESLint({ overrideConfigFile: true, overrideConfig: [...config] })
  const resolved: unknown = await eslint.calculateConfigForFile(file)
  return JSON.stringify((resolved as { readonly rules?: Record<string, unknown> }).rules?.[RESTRICTED_SYNTAX])
}

describe('the Payload request selectors', () => {
  it('runs the selectors of every way out of the request', () => {
    expect([requestOptions.length, typeof typeScriptParser]).toEqual([5, 'object'])
  })

  // An alias named payload loses no-unthreaded-req; any other name loses every Local API rule.
  it.each([
    'const payload = req.payload',
    'const cms = req.payload',
    'const cms = request.payload',
    'const cms = args.req.payload',
    'const cms = context.request.payload',
    "const cms = req['payload']",
    'const cms = req?.payload',
    'const cms: Payload = req.payload',
    'const cms = req.payload as Payload',
    'const cms = req.payload satisfies Payload',
    'const cms = req.payload!',
    'let cms: Payload\ncms = req.payload',
    'cms = request.payload as Payload',
    'const { payload } = req',
    'const handler = ({ req: { payload } }) => payload',
  ])('refuses the request-scoped instance taken out as %s', (code: string) => {
    expect(reportedLines(code)).toEqual([code.split('\n').length])
  })

  it.each([
    "await req.payload.find({ collection: 'orders', req, depth: 0 })",
    "const orders = await req.payload.find({ collection: 'orders', req, depth: 0 })",
    "const orders = req.payload.find({ collection: 'orders', req, depth: 0 })",
    'const cms = job.payload',
    'const cms = req.payloadConfig',
    'listOrders(req.payload)',
    'req.payload = cms',
  ])('accepts %s, which keeps the instance on the request', (code: string) => {
    expect(reportedLines(code)).toEqual([])
  })
})

// The request body in a hook: what the root afterError hook of a sign-in audit read, and every other way to read it.
describe('the Payload request-body selectors', () => {
  it('runs the selectors of every way to read the body', () => {
    expect(requestDataOptions).toHaveLength(4)
  })

  it.each([
    'const email = req.data.email',
    'const email = request.data?.email',
    'const email = args.req.data.email',
    "const email = req['data'].email",
    'const email = req?.data?.email',
    'const body = (req as PayloadRequest).data',
    'const body = req!.data',
    'const { data } = req',
    'const { data: body } = args.req',
    'let data: unknown\n;({ data } = req)',
    'const hook = ({ req: { data } }) => data',
    "const body = Reflect.get(req, 'data')",
    "const body = Reflect.get(args.req, 'data')",
  ])('refuses the body read as %s', (code: string) => {
    expect(linesReportedBy(requestDataOptions, code)).toEqual([code.split('\n').length])
  })

  it.each([
    'const hook = ({ data }) => data',
    'const hook = ({ data, req }) => ({ ...data, user: req.user })',
    'const after = (args) => args.data',
    'const email = req.context.email',
    'const data = req.context.data',
    'const body = job.data',
    "const body = Reflect.get(req, 'context')",
    'const { user } = req',
  ])("accepts %s, which reads the operation's own arguments or the request context", (code: string) => {
    expect(linesReportedBy(requestDataOptions, code)).toEqual([])
  })

  // A helper a hook calls and an access function read the same body, so the ban follows the request rather than
  // a directory of hooks.
  it.each(['src/hooks/audit.ts', 'src/lib/audit.ts', 'src/access/owner.ts', 'src/collections/Users.ts'])(
    'holds %s to the request-body selectors',
    async (file: string) => {
      expect(await resolvedSyntax(file)).toContain(REQUEST_DATA_MESSAGE)
    },
  )

  // An endpoint handler is the one module whose input the body is.
  it('leaves an endpoint handler free to read its body and holds it to the request selectors', async () => {
    const resolved: string = await resolvedSyntax('src/endpoints/sign-in.ts')
    expect([resolved.includes(REQUEST_DATA_MESSAGE), resolved.includes('req.payload.<operation>')]).toEqual([
      false,
      true,
    ])
  })
})
