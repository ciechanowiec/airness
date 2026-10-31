import { describe, expect, it } from 'vitest'
import { biomeSelection, isServerTemplate } from '@/biome-selection.js'
import { matchesGlob } from '@/file-roles.js'
import { globAlternatives } from '@/glob-alternatives.js'
import { findUnreachedExclusionsBySetting, readSettings, type Settings } from '@/settings.js'
import { allowedJavaScriptFiles, isCommonJsExport, memberFiles } from '@/source-selection.js'

const role = (pattern: string): { readonly pattern: string; readonly reason: string } => ({
  pattern,
  reason: 'generated fixture',
})

// An allowlist entry also declares the engine its file runs in.
const script = (pattern: string): { readonly pattern: string; readonly reason: string; readonly runtime: string } => ({
  ...role(pattern),
  runtime: 'node',
})

// A reader for files the selection must not open.
const unreadable = (file: string): string => {
  throw new Error(`${file} was read`)
}

describe('source selection', () => {
  it('selects formatter inputs without descending into generated directories or other members', () => {
    expect(
      biomeSelection(
        ['src/read.ts', 'src/generated/read.ts', 'app/src/read.ts', 'edge/read.cjs', 'notes.txt'],
        ['src/**/*', '**/*.cjs', 'app/**/*', '!src/generated'],
        ['app'],
      ),
    ).toEqual(['src/read.ts', 'edge/read.cjs'])
    expect(biomeSelection(['src/generated.ts', 'src/read.ts'], ['src/**/*', '!src/generated.ts'], [])).toEqual([
      'src/read.ts',
    ])
  })
  it('leaves Thymeleaf templates to the checks that understand them and keeps plain markup', () => {
    const files: Readonly<Record<string, string>> = {
      'src/main/resources/templates/page.html':
        '<html lang="en" xmlns:th="http://www.thymeleaf.org"><a th:href="@{/}">Home</a></html>',
      'src/main/resources/templates/fragment.html': '<label class="field" th:for="*{name}">Name</label>',
      'src/main/resources/templates/dialect.html': '<a data-th-href="@{/}">Home</a>',
      'src/main/resources/templates/block.html': '<th:block th:each="row : *{rows}"><p>Row</p></th:block>',
      'src/main/resources/static/offline.html': '<html lang="en"><a href="/">Home</a></html>',
      'src/main/resources/templates/row.html': '<p>A row with no template syntax</p>',
      'src/table.html': '<table><th>Heading</th></table>',
    }
    const read = (file: string): string => files[file] ?? ''
    expect(Object.keys(files).filter((file: string): boolean => isServerTemplate(file, read))).toEqual([
      'src/main/resources/templates/page.html',
      'src/main/resources/templates/fragment.html',
      'src/main/resources/templates/dialect.html',
      'src/main/resources/templates/block.html',
    ])
  })
  it('reads only markup when looking for template syntax', () => {
    expect(isServerTemplate('src/main/resources/static/logo.png', unreadable)).toBe(false)
    expect(isServerTemplate('src/view.ts', unreadable)).toBe(false)
  })
  it('admits only JavaScript and excludes nested member entry points', () => {
    expect(
      allowedJavaScriptFiles(
        ['edge/read.cjs', 'edge/view.jsx', 'edge/read.ts', 'app/edge/read.js'],
        ['edge/'],
        ['app'],
      ),
    ).toEqual(['edge/read.cjs', 'edge/view.jsx'])
    expect(memberFiles(['src/read.ts', 'app/src/read.ts', 'application/read.ts'], ['app'])).toEqual([
      'src/read.ts',
      'application/read.ts',
    ])
  })

  it('rejects JavaScript exceptions reaching only TypeScript', () => {
    const settings: Settings = readSettings({ airness: { javascriptAllowlist: [script('^edge/')] } })
    expect(findUnreachedExclusionsBySetting(settings.declaredExclusions, ['edge/read.ts'])).toHaveLength(1)
    expect(findUnreachedExclusionsBySetting(settings.declaredExclusions, ['edge/read.cjs'])).toEqual([])
  })

  it('rejects a generated-file declaration reaching no file', () => {
    const settings: Settings = readSettings({ airness: { generatedArtefacts: [role('lib/generated/**')] } })
    expect(settings.generatedArtefacts).toEqual(['lib/generated/**'])
    expect(findUnreachedExclusionsBySetting(settings.declaredExclusions, ['lib/read.ts'])[0]).toContain(
      'excludes nothing',
    )
    expect(findUnreachedExclusionsBySetting(settings.declaredExclusions, ['lib/generated/read.ts'])).toEqual([])
  })
})

describe('brace alternatives', () => {
  it('supports nested alternatives and literal framework directory names', () => {
    expect(globAlternatives('src/{one,{two,three}}.ts')).toEqual([
      'src/one.ts',
      'src/two.ts',
      'src/one.ts',
      'src/three.ts',
    ])
    expect(matchesGlob('src/{one,two}.ts', 'src/one.ts')).toBe(true)
    expect(matchesGlob('src/{one,two}.ts', 'src/three.ts')).toBe(false)
    expect(matchesGlob('src/app/(admin)/**', 'src/app/(admin)/custom.scss')).toBe(true)
    expect(matchesGlob('**/*.ts', '.generated/schema.ts')).toBe(true)
  })

  it.each(['src/{one,two.ts', 'src/one}.ts', 'src/{one}.ts', 'src/{one,}.ts', 'src/{1..3}.ts'])(
    'reports unsupported brace syntax with a concrete repair: %s',
    (pattern: string) => {
      expect(() => globAlternatives(pattern)).toThrow(/braces/)
    },
  )

  it('bounds the number of alternatives', () => {
    expect(() =>
      globAlternatives(`src/{${Array.from({ length: 257 }, (_, index) => String(index)).join(',')}}.ts`),
    ).toThrow(/split the declaration/)
  })
})

describe('CommonJS export policy', () => {
  it('accepts runtime export initialization without permitting other mutations', () => {
    expect(isCommonJsExport('=', 'module', 'exports', true)).toBe(true)
    expect(isCommonJsExport('=', 'exports', 'read', true)).toBe(true)
    expect(isCommonJsExport('=', 'module.exports', 'read', true)).toBe(true)
    expect(isCommonJsExport('+=', 'module', 'exports', true)).toBe(false)
    expect(isCommonJsExport('=', 'module', 'exports', false)).toBe(false)
    expect(isCommonJsExport('=', 'module', 'other', true)).toBe(false)
    expect(isCommonJsExport('=', 'record', 'exports', true)).toBe(false)
  })
})
