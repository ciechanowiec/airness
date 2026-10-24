import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { readWorkspaceFile } from '@/workspace-file.js'
import { topLevelListItems } from '@/yaml-blocks.js'

describe('the workspace file as policy reads it', () => {
  it('reads an empty document as no settings, and an absent key as no entries', () => {
    expect(topLevelListItems('', 'packages')).toEqual([])
    expect(topLevelListItems('other: 1\n', 'packages')).toEqual([])
  })

  it('refuses malformed YAML and a document that is no mapping', () => {
    expect(() => topLevelListItems('packages: [\n', 'packages')).toThrow('correct the YAML before verification')
    expect(() => topLevelListItems('- apps\n', 'packages')).toThrow('document: must be a mapping')
  })
})

describe('the workspace file as pnpm reads it', () => {
  it('reads the settings, and nothing from an absent or malformed file', () => {
    const root: string = mkdtempSync(path.join(tmpdir(), 'airness-workspace-'))
    try {
      expect(readWorkspaceFile(root)).toBeUndefined()
      writeFileSync(path.join(root, 'pnpm-workspace.yaml'), 'allowBuilds: {}\n')
      expect(readWorkspaceFile(root)).toEqual({ allowBuilds: {} })
      writeFileSync(path.join(root, 'pnpm-workspace.yaml'), 'allowBuilds: [\n')
      expect(readWorkspaceFile(root)).toBeUndefined()
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })
})
