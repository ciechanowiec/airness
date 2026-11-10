// The Local API catalogue held against the Payload the layer pins. Every operation a release adds is either
// catalogued, and so judged by every Local API rule, or excluded here with its reason; an operation that is
// neither fails the suite, so the next one cannot slip past the rules the way duplicate() did.
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import type { BasePayload } from 'payload'
import ts from 'typescript'
import { describe, expect, it } from 'vitest'
import { pinnedVersionOf } from '@/payload-pins.js'
import { LOCAL_API_CALLS } from '@/payload-policy.js'

// The operations the catalogue leaves out on purpose. Keyed by the instance's own members, so a name Payload
// drops stops compiling here rather than lingering as an exclusion of nothing.
const EXCLUDED: Readonly<Partial<Record<keyof BasePayload, string>>> = {
  auth: 'resolves the caller from the headers it is handed and reads no document on behalf of anyone else',
  forgotPassword: 'starts a reset for an address and returns nothing of the account it names',
  init: 'boots the instance; it is no operation on stored content',
  login: 'authenticates with the credentials it is handed, which are its access decision',
  resetPassword: 'its options type requires overrideAccess, so the type checker refuses the omission',
  unlock: 'its options type requires overrideAccess, so the type checker refuses the omission',
  verifyEmail: 'consumes the verification token it is handed, which is its access decision',
}

const resolvePackage: NodeJS.Require = createRequire(import.meta.url)
const payloadEntry: string = resolvePackage.resolve('payload')
const payloadRoot: string = path.dirname(path.dirname(payloadEntry))

const declarationOfPayload = (): ts.SourceFile => {
  const file: string = path.join(path.dirname(payloadEntry), 'index.d.ts')
  return ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest)
}

// Payload names the options object of every Local API operation `options`; the job queue's members take
// `args` and the lifecycle members take none.
const OPTIONS_PARAMETER: string = 'options'

const hasOptionsFirst = (parameters: ts.NodeArray<ts.ParameterDeclaration> | undefined): boolean => {
  const first: ts.ParameterDeclaration | undefined = parameters?.[0]
  return first !== undefined && ts.isIdentifier(first.name) && first.name.text === OPTIONS_PARAMETER
}

const isOptionsOperation = (member: ts.ClassElement): boolean => {
  if (ts.isMethodDeclaration(member)) {
    return hasOptionsFirst(member.parameters)
  }
  return (
    ts.isPropertyDeclaration(member) &&
    member.type !== undefined &&
    ts.isFunctionTypeNode(member.type) &&
    hasOptionsFirst(member.type.parameters)
  )
}

// Every member of the installed BasePayload that takes an options object, read from its declaration.
const optionsOperations = (): readonly string[] => {
  const payloadClass: ts.ClassDeclaration | undefined = declarationOfPayload().statements.find(
    (statement: ts.Statement): statement is ts.ClassDeclaration =>
      ts.isClassDeclaration(statement) && statement.name?.text === 'BasePayload',
  )
  const names: readonly string[] = (payloadClass?.members ?? [])
    .filter((member: ts.ClassElement): boolean => isOptionsOperation(member))
    .map((member: ts.ClassElement): string =>
      member.name !== undefined && ts.isIdentifier(member.name) ? member.name.text : '',
    )
  return [...new Set(names)].toSorted((left: string, right: string): number => left.localeCompare(right))
}

const catalogued: readonly string[] = LOCAL_API_CALLS.map((call: string): string => call.slice(1, -1))

describe('the Local API catalogue against the pinned Payload', () => {
  it('installs the Payload version the layer pins', () => {
    const manifest: unknown = JSON.parse(readFileSync(path.join(payloadRoot, 'package.json'), 'utf8'))
    expect(manifest).toMatchObject({ name: 'payload', version: pinnedVersionOf('payload') })
  })

  it('reads the operations from the installed declaration', () => {
    expect(optionsOperations()).toEqual(expect.arrayContaining(['create', 'find', 'update']))
  })

  it('catalogues or excludes every operation that takes options', () => {
    const unclassified: readonly string[] = optionsOperations().filter(
      (operation: string): boolean => !(catalogued.includes(operation) || Object.hasOwn(EXCLUDED, operation)),
    )
    expect(unclassified).toEqual([])
  })

  it('catalogues only operations the pinned Payload declares', () => {
    expect(catalogued.filter((operation: string): boolean => !optionsOperations().includes(operation))).toEqual([])
  })

  it('excludes only operations the catalogue leaves out', () => {
    const excluded: readonly string[] = Object.keys(EXCLUDED)
    expect(excluded.filter((operation: string): boolean => catalogued.includes(operation))).toEqual([])
    expect(excluded.filter((operation: string): boolean => !optionsOperations().includes(operation))).toEqual([])
  })
})
