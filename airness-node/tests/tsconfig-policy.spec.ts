import { describe, expect, it } from 'vitest'
import { REQUIRED_TSCONFIG_EXTENDS, tsconfigPathsFor, tsconfigProblems } from '@/tsconfig-policy.js'

const conforming = (extra: Readonly<Record<string, unknown>> = {}): string =>
  JSON.stringify({ extends: REQUIRED_TSCONFIG_EXTENDS, ...tsconfigPathsFor(false, []), ...extra })

describe('the project tsconfig.json', () => {
  it('passes a file that extends the shipped one and sets only project options', () => {
    expect(
      tsconfigProblems(conforming({ compilerOptions: { paths: { '@/*': ['./src/*'] }, types: [] } }), false, []),
    ).toEqual([])
  })

  it('fails a missing or unreadable file', () => {
    expect(tsconfigProblems(undefined, false, [])[0]).toContain('tsconfig.json is missing')
    expect(tsconfigProblems('{', false, [])[0]).toContain('is not valid JSON')
  })

  it('fails another extends, a strictness override and a narrowed include', () => {
    expect(
      tsconfigProblems(
        JSON.stringify({ extends: './base.json', compilerOptions: { strict: false }, include: ['src/**/*.ts'] }),
        false,
        [],
      ),
    ).toEqual([
      `tsconfig.json must declare "extends": "${REQUIRED_TSCONFIG_EXTENDS}"`,
      'tsconfig.json compilerOptions.strict: the harness owns this compiler option; remove the override',
      'tsconfig.json include: must be exactly ["**/*.ts","**/*.tsx","**/*.mts","**/*.cts"]',
      'tsconfig.json exclude: must be exactly ["node_modules","dist","target"]',
    ])
  })

  it('requires every nested member to be excluded, so a root never compiles a member under its own paths', () => {
    expect(tsconfigPathsFor(false, ['apps/web'])['exclude']).toEqual(['node_modules', 'dist', 'target', 'apps/web'])
    expect(tsconfigProblems(conforming(), false, ['apps/web'])).toEqual([
      'tsconfig.json exclude: must be exactly ["node_modules","dist","target","apps/web"]',
    ])
  })

  it('requires the Next type files of an application', () => {
    expect(tsconfigPathsFor(true, [])['include']).toContain('next-env.d.ts')
  })
})
