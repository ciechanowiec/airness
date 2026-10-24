// Variants of the Biome and Oxlint gates the conforming and planted projects of the gate specs do not reach.
import { describe, expect, it } from 'vitest'
import { biome } from '@/gate-biome.js'
import { oxlint } from '@/gate-oxlint.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

const TOOL_TIMEOUT_MS: number = 60_000

const withProject = (files: Readonly<Record<string, string>>, use: (root: string) => void): void => {
  const root: string = temporaryProject(files)
  try {
    use(root)
  } finally {
    removeProject(root)
  }
}

describe('biome', PROCESS_TIME, () => {
  it('passes a member that owns no file Biome judges', () => {
    withProject({ 'notes.md': 'notes\n' }, (root: string): void => {
      expect(biome(memberAt(root)).summary).toBe('no owned file is selected for Biome')
    })
  })
})

describe('oxlint', PROCESS_TIME, () => {
  it('passes a member with no source to analyze', () => {
    withProject({ 'notes.md': 'notes\n' }, (root: string): void => {
      expect(oxlint(memberAt(root)).summary).toBe('no eligible source remains to analyze')
    })
  })

  it('refuses a native suppression that names no governed rule', () => {
    withProject({ 'src/total.ts': '// oxlint-disable-next-line\nexport const total = 1\n' }, (root: string): void => {
      expect(oxlint(memberAt(root)).findings.join('\n')).toContain('src/total.ts: ')
    })
  })

  it(
    'analyzes the JSX of an application under the accessibility rules, beside the core rules',
    () => {
      withProject(
        {
          'src/total.ts': 'export const total = 1\n',
          'src/picture.tsx': 'export const Picture = (): unknown => <img src="a.png" />\n',
        },
        (root: string): void => {
          expect(oxlint({ ...memberAt(root), hasOwnRuntime: true }).findings.join('\n')).toContain('alt-text')
        },
      )
      withProject(
        {
          'src/total.ts': 'export const total = 1\n',
          'src/picture.tsx': 'export const Picture = (): unknown => <img src="a.png" alt="A picture" />\n',
        },
        (root: string): void => {
          expect(oxlint({ ...memberAt(root), hasOwnRuntime: true }).summary).toContain('; ')
        },
      )
    },
    // Two Oxlint runs, each over a working tree read through git, under the load of the whole suite.
    TOOL_TIMEOUT_MS,
  )

  it('passes a suppression ESLint owns, which it leaves to ESLint', () => {
    withProject(
      { 'src/total.ts': '// eslint-disable-next-line no-console -- the command prints\nconsole.info(1)\n' },
      (root: string): void => {
        expect(oxlint(memberAt(root)).ok).toBe(true)
      },
    )
  })
})
