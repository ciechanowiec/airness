import { existsSync } from 'node:fs'
import path from 'node:path'
import { isScopeFailure, sweepScopeFor } from './change-scope.js'
import { type SweepScope, scopeHeadline, scopeLines } from './change-scope-policy.js'
import type { GateResult } from './exec.js'
import { isBrowserApp } from './facets.js'
import { type Member, membersOf } from './project.js'

const JSON_INDENT: number = 2
interface ScopedMember {
  readonly member: string
  readonly scope: SweepScope | GateResult
}

/** Whether this is the scope diagnostic with a supported output option. */
export const isScopeCommand = (command: string | undefined, rest: readonly string[]): boolean =>
  command === 'scope' && (rest.length === 0 || (rest.length === 1 && rest[0] === '--json'))

const printScope = (entry: ScopedMember): void => {
  const isFailure: boolean = isScopeFailure(entry.scope)
  const summary: string = isScopeFailure(entry.scope) ? entry.scope.summary : scopeHeadline(entry.scope)
  const lines: readonly string[] = isScopeFailure(entry.scope) ? entry.scope.findings : scopeLines(entry.scope)
  console.info(`${entry.member}: ${isFailure ? 'failed: ' : ''}${summary}`)
  for (const line of lines) {
    console.info(`  ${line}`)
  }
}

/** Explain what the next delta sweep will cover, without claiming a verification verdict. */
export const scopeCommand = (directory: string, isJson: boolean): number => {
  try {
    if (!existsSync(path.join(directory, 'package.json'))) {
      throw new Error('The scope diagnostic needs a node project')
    }
    const scopes: readonly ScopedMember[] = membersOf(directory)
      .filter(isBrowserApp)
      .map((member: Member): ScopedMember => ({ member: member.path, scope: sweepScopeFor(member) }))
    printScopes(scopes, isJson)
    return scopes.some((entry: ScopedMember): boolean => isScopeFailure(entry.scope)) ? 1 : 0
  } catch (error: unknown) {
    console.error(error instanceof Error ? error.message : String(error))
    return 1
  }
}

const printScopes = (scopes: readonly ScopedMember[], isJson: boolean): void => {
  if (isJson) {
    console.info(JSON.stringify(scopes, null, JSON_INDENT))
    return
  }
  for (const scope of scopes) {
    printScope(scope)
  }
}
