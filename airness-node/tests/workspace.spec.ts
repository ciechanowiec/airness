import { describe, expect, it } from 'vitest'
import { analysisBoundaries, eslintArguments, hasOwnRuntime, nestedWithin, readWorkspacePackages } from '@/workspace.js'

describe('workspace members', () => {
  it('reads the packages a workspace file selects', () => {
    expect(readWorkspacePackages('packages:\n  - apps/*\n  - "!apps/legacy"\n')).toEqual(['apps/*', '!apps/legacy'])
    expect(readWorkspacePackages('')).toEqual([])
  })

  it('refuses a packages entry that is not a string rather than dropping it', () => {
    expect(() => readWorkspacePackages('packages:\n  - 1\n')).toThrow('a sequence of strings')
  })

  it('names the members nested inside a member relative to it', () => {
    const every: readonly string[] = ['.', 'apps/web', 'apps/web/widgets', 'tools']
    expect(nestedWithin('.', every)).toEqual(['apps/web', 'apps/web/widgets', 'tools'])
    expect(nestedWithin('apps/web', every)).toEqual(['widgets'])
    expect(nestedWithin('tools', every)).toEqual([])
  })

  it('decides an application from what the manifest declares', () => {
    expect(hasOwnRuntime({ dependencies: { next: '16.0.0' } })).toBe(true)
    expect(hasOwnRuntime({ devDependencies: { vitest: '5.0.5' } })).toBe(false)
  })
})

describe('the ESLint invocation', () => {
  it('names the harness configuration, fails on a warning, and stops at every nested member', () => {
    const argumentsOf: readonly string[] = eslintArguments('/harness/dist/eslint.js', ['apps/web'])
    expect(argumentsOf.slice(0, 4)).toEqual(['.', '--config', '/harness/dist/eslint.js', '--max-warnings=0'])
    expect(argumentsOf.slice(-2)).toEqual(['--ignore-pattern', 'apps/web/**'])
    expect(analysisBoundaries(['a', 'b/c'])).toEqual(['a/**', 'b/c/**'])
  })
})
