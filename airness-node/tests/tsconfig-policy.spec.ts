import { describe, expect, it } from 'vitest'
import { tsconfigProblems } from '@/tsconfig-policy.js'

const EXTENDS: string = '@airness/node/tsconfig.json'
const LIBRARY_INCLUDE: readonly string[] = ['**/*.ts', '**/*.tsx', '**/*.mts', '**/*.cts']
const LIBRARY_EXCLUDE: readonly string[] = ['node_modules', 'dist', 'target']

const conforming = (extra: Readonly<Record<string, unknown>> = {}): string =>
  JSON.stringify({ extends: EXTENDS, include: LIBRARY_INCLUDE, exclude: LIBRARY_EXCLUDE, ...extra })

describe('the project tsconfig.json', () => {
  it('passes a file that extends the shipped one and sets only project options', () => {
    expect(
      tsconfigProblems(conforming({ compilerOptions: { paths: { '@/*': ['./src/*'] }, types: [] } }), false, []),
    ).toEqual([])
  })

  it('fails a missing or unreadable file', () => {
    expect(tsconfigProblems(undefined, false, [])).toEqual([
      `tsconfig.json is missing; write one that extends ${EXTENDS}`,
    ])
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
      `tsconfig.json must declare "extends": "${EXTENDS}"`,
      'tsconfig.json compilerOptions.strict: the harness owns this compiler option; remove the override',
      'tsconfig.json include: must be exactly ["**/*.ts","**/*.tsx","**/*.mts","**/*.cts"]',
      'tsconfig.json exclude: must be exactly ["node_modules","dist","target"]',
    ])
  })

  it('requires every nested member to be excluded, so a root never compiles a member under its own paths', () => {
    expect(tsconfigProblems(conforming(), false, ['apps/web'])).toEqual([
      'tsconfig.json exclude: must be exactly ["node_modules","dist","target","apps/web"]',
    ])
    expect(tsconfigProblems(conforming({ exclude: [...LIBRARY_EXCLUDE, 'apps/web'] }), false, ['apps/web'])).toEqual([])
  })

  it('requires the Next type files of an application', () => {
    expect(tsconfigProblems(conforming(), true, [])).toEqual([
      'tsconfig.json include: must be exactly ' +
        '["next-env.d.ts","**/*.ts","**/*.tsx","**/*.mts","**/*.cts",".next/types/**/*.ts",".next/dev/types/**/*.ts"]',
      'tsconfig.json exclude: must be exactly ["node_modules","target"]',
    ])
  })

  it('brings a Storybook configuration directory into the project, which a recursive include passes over', () => {
    expect(tsconfigProblems(conforming(), true, [], true)[0]).toMatch(
      /^tsconfig\.json include: must be exactly \["next-env\.d\.ts",.*,"\.storybook\/\*\*\/\*\.ts","\.storybook\/\*\*\/\*\.tsx"\]$/u,
    )
    expect(tsconfigProblems(conforming(), false, [], true)).toEqual([
      'tsconfig.json include: must be exactly ["**/*.ts","**/*.tsx","**/*.mts","**/*.cts",".storybook/**/*.ts",".storybook/**/*.tsx"]',
    ])
  })
})
