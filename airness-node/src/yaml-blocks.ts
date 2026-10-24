// Decode workspace configuration without coupling policy to filesystem reads or YAML presentation.
import { parseDocument } from 'yaml'
import { isArray, isRecord } from './json-shapes.js'

const WORKSPACE_FILE: string = 'pnpm-workspace.yaml'
const MAX_ALIAS_COUNT: number = 100
const PLAIN_MAPPING_PROTOTYPE: unknown = Object.getPrototypeOf({})

const invalidValue = (key: string, expected: string): TypeError =>
  new TypeError(`${WORKSPACE_FILE} ${key}: must be ${expected}; correct the workspace configuration`)

const isMapping = (value: unknown): value is Record<string, unknown> => {
  if (!isRecord(value)) {
    return false
  }
  const prototype: unknown = Object.getPrototypeOf(value)
  return prototype === PLAIN_MAPPING_PROTOTYPE || prototype === null
}

/** Read a workspace mapping, rejecting malformed evidence rather than treating it as absent. */
const readWorkspaceDocument = (file: string): Readonly<Record<string, unknown>> => {
  try {
    const document: ReturnType<typeof parseDocument> = parseDocument(file)
    if (document.errors.length > 0 || document.warnings.length > 0) {
      throw new TypeError([...document.errors, ...document.warnings].map((error): string => error.message).join('; '))
    }
    const value: unknown = document.toJS({ maxAliasCount: MAX_ALIAS_COUNT })
    if (document.contents === null) {
      return {}
    }
    if (!isMapping(value)) {
      throw invalidValue('document', 'a mapping')
    }
    return value
  } catch (error: unknown) {
    const reason: string = error instanceof Error ? error.message : String(error)
    throw new TypeError(`${WORKSPACE_FILE}: ${reason}; correct the YAML before verification`, { cause: error })
  }
}

/** Read every sequence entry; an invalid entry cannot silently disappear from policy. */
export const topLevelListItems = (file: string, key: string): readonly string[] => {
  const value: unknown = readWorkspaceDocument(file)[key]
  if (value === undefined) {
    return []
  }
  if (!(isArray(value) && value.every((entry: unknown): entry is string => typeof entry === 'string'))) {
    throw invalidValue(key, 'a sequence of strings')
  }
  return value
}
