// No mocks. Tests run against real objects and real services (a real framework instance, a real
// database, real in-process servers) - never test doubles. These are the Vitest entry points that
// create mocks/stubs/spies, banned build-wide, in every spelling that reaches them.
//
// Vitest exports its utility object under two names, `vi` and `vitest`, and either can be imported under
// a name of the author's choosing, reached through a namespace import, or bound to a local first. A ban
// that names one object reaches one spelling of five, so the properties below name both exports, and the
// shapes after them refuse every way of reaching the object under another name.
import type { RestrictedSyntax } from './eslint-security.js'

/** One `no-restricted-properties` entry: the member access to reject, and what to say instead. */
export interface RestrictedProperty {
  readonly object: string
  readonly property: string
  readonly message: string
}

/** What every mock finding says. */
export const NO_MOCKS_MESSAGE: string =
  'No mocks/stubs/spies. Test against real objects and real services instead (see AGENTS.md).'

const MOCKING_METHODS: readonly string[] = [
  'fn',
  'mock',
  'doMock',
  'unmock',
  'mocked',
  'spyOn',
  'stubGlobal',
  'stubEnv',
  'importMock',
]

// The two names Vitest exports its utility object under.
const UTILITY_EXPORTS: readonly string[] = ['vi', 'vitest']

// The ban as ENTRIES rather than as a finished rule setting: a scoped block that sets
// `no-restricted-properties` REPLACES this rather than adding to it, so it spreads these instead.
/** The mocking methods of Vitest's utility object, under each name Vitest exports it. */
export const NO_MOCK_PROPERTIES: readonly RestrictedProperty[] = UTILITY_EXPORTS.flatMap(
  (object: string): readonly RestrictedProperty[] =>
    MOCKING_METHODS.map((property: string): RestrictedProperty => ({ object, property, message: NO_MOCKS_MESSAGE })),
)

const UTILITY_NAME: string = `/^(?:${UTILITY_EXPORTS.join('|')})$/`
const MOCKING_NAME: string = `/^(?:${MOCKING_METHODS.join('|')})$/`

// A member key written as a name or, computed, as a string literal: `.vi` and `['vi']` alike.
const keyed = (path: string, name: string): string => `:matches([${path}.name=${name}], [${path}.value=${name}])`

const quoted = (name: string): string => `'${name}'`

// A node of a type and attributes bound to another name, by a declaration or by an assignment.
const bindings = (type: string, attributes: string): readonly string[] => [
  `VariableDeclarator > ${type}.init${attributes}`,
  `AssignmentExpression > ${type}.right${attributes}`,
]

const VITEST_SOURCE: string = "ImportDeclaration[source.value='vitest']"

// `vi` names nothing but Vitest's object. `vitest` is also what a project calls its ESLint plugin or a version
// it reads, so a binding of that name is refused only in a file that takes it from Vitest: by its named
// export, or by a namespace import whose member it is.
const NAMED_VITEST: string = `Program:has(${VITEST_SOURCE} > ImportSpecifier${keyed('imported', quoted('vitest'))}) `
const NAMESPACED_VITEST: string = `Program:has(${VITEST_SOURCE} > ImportNamespaceSpecifier) `

const RENAMED_MESSAGE: string =
  "No mocks/stubs/spies. Import Vitest's utility object under its own name, so the mock ban reads it " +
  '(see AGENTS.md).'

const ALIASED_MESSAGE: string =
  "No mocks/stubs/spies. Use Vitest's utility object under its own name rather than through an alias, " +
  'so the mock ban reads it (see AGENTS.md).'

const aliased = (selectors: readonly string[]): readonly RestrictedSyntax[] =>
  selectors.map((selector: string): RestrictedSyntax => ({ selector, message: ALIASED_MESSAGE }))

const renamedImport = (name: string): RestrictedSyntax => ({
  selector: `${VITEST_SOURCE} > ImportSpecifier${keyed('imported', quoted(name))}:not([local.name=${quoted(name)}])`,
  message: RENAMED_MESSAGE,
})

const VI_KEY: string = keyed('property', quoted('vi'))
const VITEST_KEY: string = keyed('property', quoted('vitest'))

/** The spellings that reach Vitest's mocking methods under a name the properties above cannot see. */
export const NO_MOCK_SYNTAX: readonly RestrictedSyntax[] = [
  // `import { vi as double } from 'vitest'` and `import { vitest as double } from 'vitest'`.
  ...UTILITY_EXPORTS.map((name: string): RestrictedSyntax => renamedImport(name)),
  // `Vitest.vi.fn()` through `import * as Vitest from 'vitest'`, the key named or computed at either step.
  {
    selector: `MemberExpression${keyed('property', MOCKING_NAME)} > MemberExpression.object${keyed('property', UTILITY_NAME)}`,
    message: NO_MOCKS_MESSAGE,
  },
  // `const double = vi`, `const double = Vitest.vi`, `double = vi`, and the same of `vitest` in a file that
  // takes it from Vitest. `const { fn } = vi` is a property read the properties above already refuse.
  ...aliased([
    ...bindings('Identifier', "[name='vi']"),
    ...bindings('MemberExpression', VI_KEY),
    ...bindings('Identifier', "[name='vitest']").map((selector: string): string => NAMED_VITEST + selector),
    ...bindings('MemberExpression', VITEST_KEY).map((selector: string): string => NAMESPACED_VITEST + selector),
  ]),
  // `const { vi: double } = Vitest`, which takes the object out of a namespace under another name, and the
  // same of `vitest` out of a namespace import or a dynamic import of Vitest.
  ...aliased([
    `ObjectPattern > Property${keyed('key', quoted('vi'))}:not([value.name='vi'])`,
    `${NAMESPACED_VITEST}ObjectPattern > Property${keyed('key', quoted('vitest'))}:not([value.name='vitest'])`,
    "VariableDeclarator:matches([init.source.value='vitest'], [init.argument.source.value='vitest']) > " +
      `ObjectPattern.id > Property${keyed('key', quoted('vitest'))}:not([value.name='vitest'])`,
  ]),
]
