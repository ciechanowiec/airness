import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  type Member,
  membersOf,
  packageDirectory,
  readJson,
  readText,
  resolvedVersion,
  resolveTool,
} from '@/project.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

const WORKSPACE: Readonly<Record<string, string>> = {
  'package.json': '{"name":"root"}\n',
  'pnpm-workspace.yaml': 'packages:\n  - apps/*\n',
  'apps/web/package.json': '{"name":"web","dependencies":{"next":"16.0.0"}}\n',
  'apps/docs/package.json': '{"name":"docs"}\n',
  'apps/docs/node_modules/dependency/package.json': '{"name":"dependency"}\n',
}

const pathsOf = (members: readonly Member[]): readonly string[] => members.map((member: Member): string => member.path)

describe('the members of a node project', PROCESS_TIME, () => {
  it('is the project alone without a workspace file', () => {
    const root: string = temporaryProject({ 'package.json': '{"name":"solo"}\n' })
    try {
      const [member] = membersOf(root)
      expect(member).toEqual(expect.objectContaining({ path: '.', root, projectRoot: root, siblingPaths: [] }))
    } finally {
      removeProject(root)
    }
  })

  it('selects the workspace members from the files a commit carries, each knowing what it is', () => {
    const root: string = temporaryProject(WORKSPACE)
    try {
      const members: readonly Member[] = membersOf(root)
      expect(pathsOf(members)).toEqual(['.', 'apps/docs', 'apps/web'])
      expect(members[0]?.siblingPaths).toEqual(['apps/docs', 'apps/web'])
      expect(members.find((member: Member): boolean => member.path === 'apps/web')?.hasOwnRuntime).toBe(true)
      expect(members[1]?.projectRoot).toBe(root)
    } finally {
      removeProject(root)
    }
  })

  it('walks the directory outside a git checkout, passing over installed and hidden directories', () => {
    const root: string = mkdtempSync(path.join(tmpdir(), 'airness-members-'))
    try {
      const files: Readonly<Record<string, string>> = {
        ...WORKSPACE,
        'apps/.cache/package.json': '{}\n',
        'apps/deep/a/b/c/package.json': '{}\n',
      }
      for (const [file, content] of Object.entries(files)) {
        mkdirSync(path.dirname(path.join(root, file)), { recursive: true })
        writeFileSync(path.join(root, file), content)
      }
      expect(pathsOf(membersOf(root))).toEqual(['.', 'apps/docs', 'apps/web'])
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })
})

describe('the harness tools', PROCESS_TIME, () => {
  it('resolves a tool by its package and bin, and the version it resolved to', () => {
    expect(resolveTool('typescript', 'tsc')).toMatch(/typescript[/\\]bin[/\\]tsc$/u)
    expect(resolveTool('knip')).toMatch(/knip/u)
    expect(packageDirectory('typescript')).toMatch(/typescript$/u)
    expect(resolvedVersion('typescript')).toMatch(/^\d+\.\d+\.\d+$/u)
  })

  it('refuses a bin the package does not ship, and a package the harness does not depend on', () => {
    expect(() => resolveTool('typescript', 'nothing')).toThrow('could not resolve the "nothing" executable')
    expect(() => resolveTool('no-such-package-anywhere')).toThrow('could not locate the manifest')
  })

  it('reads an absent file as undefined', () => {
    expect(readText('/nonexistent/file.txt')).toBeUndefined()
    expect(readJson('/nonexistent/file.json')).toBeUndefined()
  })
})
