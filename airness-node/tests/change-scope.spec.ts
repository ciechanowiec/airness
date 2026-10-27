import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { isScopeFailure, sweepScopeFor, withScopeFile } from '@/change-scope.js'
import type { SweepScope } from '@/change-scope-policy.js'
import { type GateResult, type RunResult, run } from '@/exec.js'
import { type Member, membersOf } from '@/project.js'
import { isScopeCommand, scopeCommand } from '@/scope-command.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

const MANIFEST: string = JSON.stringify({ name: 'scope-app', dependencies: { next: '16.4.2' } })
const FILES: Readonly<Record<string, string>> = {
  'package.json': MANIFEST,
  'tsconfig.json': JSON.stringify({ compilerOptions: { strict: true }, include: ['src/**/*.ts'] }),
  'src/app/first/page.ts':
    "import { title } from '../../title.js'\nexport default function Page(): string { return title }\n",
  'src/app/second/page.ts': "export default function Page(): string { return 'Second' }\n",
  'src/app/layout.ts': "export default function Layout(): string { return 'layout' }\n",
  'src/title.ts': "export const title: string = 'First'\n",
}

const git = (root: string, arguments_: readonly string[]): string => {
  const result: RunResult = run('git', arguments_, { cwd: root })
  if (result.code !== 0) {
    throw new Error(result.output)
  }
  return result.stdout.trim()
}

const memberOf = (root: string): Member => {
  const member: Member | undefined = membersOf(root)[0]
  if (member === undefined) {
    throw new Error('The fixture has no node member')
  }
  return member
}

const withHistory = async (body: (root: string) => void | Promise<void>): Promise<void> => {
  const root: string = temporaryProject(FILES)
  const previousRoot: string | undefined = process.env['AIRNESS_REPOSITORY_ROOT']
  const previousNodes: string | undefined = process.env['AIRNESS_NODE_ROOTS']
  try {
    process.env['AIRNESS_REPOSITORY_ROOT'] = root
    process.env['AIRNESS_NODE_ROOTS'] = root
    git(root, ['init', '--quiet'])
    git(root, ['config', 'user.name', 'Fixture'])
    git(root, ['config', 'user.email', 'fixture@example.invalid'])
    git(root, ['add', '.'])
    git(root, ['commit', '--quiet', '-m', 'test(scope): establish the remote baseline'])
    git(root, ['remote', 'add', 'origin', '.'])
    git(root, ['update-ref', 'refs/remotes/origin/main', 'HEAD'])
    await body(root)
  } finally {
    if (previousRoot === undefined) {
      delete process.env['AIRNESS_REPOSITORY_ROOT']
    } else {
      process.env['AIRNESS_REPOSITORY_ROOT'] = previousRoot
    }
    if (previousNodes === undefined) {
      delete process.env['AIRNESS_NODE_ROOTS']
    } else {
      process.env['AIRNESS_NODE_ROOTS'] = previousNodes
    }
    removeProject(root)
  }
}

describe('scope from a real Git history and import graph', PROCESS_TIME, () => {
  it('finds affected routes from committed, staged and untracked inputs', async () => {
    await withHistory((root: string): void => {
      writeFileSync(path.join(root, 'src/title.ts'), "export const title: string = 'Changed'\n")
      git(root, ['add', '.'])
      git(root, ['commit', '--quiet', '-m', 'test(scope): change the imported title'])
      const scope: SweepScope | GateResult = sweepScopeFor(memberOf(root))
      expect(scope).toMatchObject({ isComplete: false, changed: ['src/title.ts'], routes: [{ target: '/first' }] })
      writeFileSync(
        path.join(root, 'src/app/second/page.ts'),
        "export default function Page(): string { return 'Changed' }\n",
      )
      git(root, ['add', '.'])
      expect(sweepScopeFor(memberOf(root))).toMatchObject({ routes: [{ target: '/first' }, { target: '/second' }] })
      writeFileSync(path.join(root, 'src/unknown.ts'), 'export const unknownInput: number = 1\n')
      expect(sweepScopeFor(memberOf(root))).toHaveProperty('changed', expect.arrayContaining(['src/unknown.ts']))
      writeFileSync(path.join(root, 'src/unmapped.config'), 'global input')
      expect(sweepScopeFor(memberOf(root))).toMatchObject({ isComplete: true })
    })
  })

  it('broadens Maven and backend changes and records deleted inputs', async () => {
    await withHistory((root: string): void => {
      rmSync(path.join(root, 'src/title.ts'))
      writeFileSync(
        path.join(root, 'src/app/first/page.ts'),
        "export default function Page(): string { return 'Local' }\n",
      )
      expect(sweepScopeFor(memberOf(root))).toMatchObject({ changed: ['src/app/first/page.ts', 'src/title.ts'] })
      writeFileSync(path.join(root, 'pom.xml'), '<project/>')
      expect(sweepScopeFor(memberOf(root))).toMatchObject({ isComplete: true })
    })
  })

  it('sweeps everything without a baseline or a supported static route map', async () => {
    await withHistory((root: string): void => {
      git(root, ['remote', 'remove', 'origin'])
      expect(sweepScopeFor(memberOf(root))).toMatchObject({ isComplete: true })
      git(root, ['remote', 'add', 'origin', '.'])
      git(root, ['update-ref', 'refs/remotes/origin/main', 'HEAD'])
      writeFileSync(path.join(root, 'package.json'), JSON.stringify({ airness: { serve: 'start' } }))
      expect(sweepScopeFor(memberOf(root))).toMatchObject({ isComplete: true })
    })
  })

  it('discovers Git and node roots when Maven supplies neither, and broadens unsupported Next routes', async () => {
    await withHistory((root: string): void => {
      delete process.env['AIRNESS_REPOSITORY_ROOT']
      delete process.env['AIRNESS_NODE_ROOTS']
      expect(sweepScopeFor(memberOf(root))).toMatchObject({ isComplete: false })
      rmSync(path.join(root, 'src/app/layout.ts'))
      expect(sweepScopeFor(memberOf(root))).toMatchObject({ isComplete: true })
    })
  })

  it('includes a sibling CMS reached through an auxiliary server and a workspace dependency', async () => {
    await withHistory((root: string): void => {
      writeFileSync(path.join(root, 'pnpm-workspace.yaml'), 'packages:\n  - web\n  - cms\n  - bridge\n')
      for (const member of ['web', 'cms', 'bridge']) {
        mkdirSync(path.join(root, member, 'src/app'), { recursive: true })
      }
      writeFileSync(path.join(root, 'cms/package.json'), JSON.stringify({ name: 'cms' }))
      writeFileSync(
        path.join(root, 'bridge/package.json'),
        JSON.stringify({ name: 'bridge', dependencies: { cms: '1.0.0' } }),
      )
      writeFileSync(
        path.join(root, 'web/package.json'),
        JSON.stringify({
          name: 'web',
          dependencies: { next: '16.4.2', bridge: '1.0.0' },
          airness: {
            auxiliaryServers: [
              { cwd: '../bridge', command: 'serve', url: 'http://localhost:4000' },
              { command: 'story', url: 'http://localhost:6006' },
            ],
          },
        }),
      )
      writeFileSync(path.join(root, 'web/tsconfig.json'), '{}')
      writeFileSync(path.join(root, 'web/src/app/layout.ts'), 'export const layout: string = "layout"\n')
      writeFileSync(path.join(root, 'web/src/app/page.ts'), 'export const page: string = "page"\n')
      git(root, ['add', '.'])
      git(root, ['commit', '--quiet', '-m', 'test(scope): declare the related workspace members'])
      git(root, ['update-ref', 'refs/remotes/origin/main', 'HEAD'])
      writeFileSync(path.join(root, 'cms/content.json'), '{}')
      const web: Member | undefined = membersOf(root).find((member: Member): boolean => member.path === 'web')
      if (web === undefined) {
        throw new Error('The web member is missing')
      }
      expect(sweepScopeFor(web)).toMatchObject({ isComplete: true, changed: ['cms/content.json'] })
    })
  })

  it('fails a Git diff that cannot read a worktree instead of treating it as no change', async () => {
    await withHistory((root: string): void => {
      const member: Member = memberOf(root)
      git(root, ['config', 'core.bare', 'true'])
      expect(sweepScopeFor(member)).toMatchObject({
        ok: false,
        summary: 'the change since the baseline could not be read',
      })
    })
  })

  it('fails instead of passing an unreadable import graph', async () => {
    await withHistory((root: string): void => {
      writeFileSync(path.join(root, 'tsconfig.json'), '{')
      const scope: SweepScope | GateResult = sweepScopeFor(memberOf(root))
      expect(isScopeFailure(scope)).toBe(true)
      expect(scopeCommand(root, false)).toBe(1)
    })
  })

  it('explains the scope as text or JSON and refuses malformed diagnostic inputs', async () => {
    await withHistory((root: string): void => {
      expect(scopeCommand(root, false)).toBe(0)
      expect(scopeCommand(root, true)).toBe(0)
      expect(scopeCommand(path.join(root, 'absent'), false)).toBe(1)
    })
    expect(isScopeCommand('scope', [])).toBe(true)
    expect(isScopeCommand('scope', ['--json'])).toBe(true)
    expect(isScopeCommand('scope', ['--unknown'])).toBe(false)
    expect(isScopeCommand('gate', [])).toBe(false)
    expect(isScopeCommand('scope', ['--json', '--json'])).toBe(false)
  })

  it('removes temporary scope evidence even when its consumer throws', async () => {
    const scope: SweepScope = {
      isComplete: true,
      baseline: undefined,
      branch: undefined,
      changed: [],
      reasons: ['no baseline'],
    }
    const file: string = await withScopeFile(scope, (written: string): Promise<string> => {
      expect(JSON.parse(readFileSync(written, 'utf8'))).toMatchObject({ reasons: ['no baseline'] })
      return Promise.resolve(written)
    })
    expect(existsSync(file)).toBe(false)
    await expect(
      withScopeFile(scope, (): Promise<never> => Promise.reject(new Error('consumer failed'))),
    ).rejects.toThrow('consumer failed')
  })
})
