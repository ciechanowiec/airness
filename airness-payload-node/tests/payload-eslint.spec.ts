// The Payload ESLint configuration composes onto the node layer's: the harness admits a stack layer only when its
// export starts with the node layer's own blocks, and a Payload selector added to `no-restricted-syntax` must not
// drop a selector the node layer set for the same files.

import path from 'node:path'
import nodeConfig from '@airness/node/eslint'
import { describe, expect, it } from 'vitest'
import config from '@/eslint.js'

const RESTRICTED_SYNTAX: string = 'no-restricted-syntax'

type ConfigBlock = (typeof nodeConfig)[number]

const restrictedSyntaxOf = (block: ConfigBlock): readonly unknown[] | undefined => {
  const setting: unknown = block.rules?.[RESTRICTED_SYNTAX]
  return Array.isArray(setting) ? setting : undefined
}

const selectorsOf = (setting: readonly unknown[]): readonly unknown[] =>
  setting.slice(1).map((option: unknown): unknown => (option as { readonly selector?: unknown }).selector)

describe('the Payload ESLint configuration', () => {
  it('starts with the node layer blocks themselves, which is the only shape the harness composes', () => {
    expect(config.slice(0, nodeConfig.length).every((block, index) => block === nodeConfig[index])).toBe(true)
  })

  it('extends every node restricted-syntax setting, in the node order, with the req destructuring selectors', () => {
    const nodeSettings: readonly ConfigBlock[] = nodeConfig.filter((block) => restrictedSyntaxOf(block) !== undefined)
    const extensions: readonly ConfigBlock[] = config.slice(nodeConfig.length, nodeConfig.length + nodeSettings.length)
    expect(nodeSettings.length).toBeGreaterThan(0)
    for (const [index, node] of nodeSettings.entries()) {
      const extension: ConfigBlock | undefined = extensions[index]
      expect([extension?.files, extension?.ignores]).toEqual([node.files, node.ignores])
      const extended: readonly unknown[] = selectorsOf(restrictedSyntaxOf(extension ?? {}) ?? [])
      expect(extended).toEqual(expect.arrayContaining([...selectorsOf(restrictedSyntaxOf(node) ?? [])]))
      expect(extended.filter((selector) => String(selector).includes("key.name='payload'"))).toHaveLength(3)
    }
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
