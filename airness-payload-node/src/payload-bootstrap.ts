// Writes a boot hook performs, or reaches in one call.
//
// `onInit` runs on every boot of every container: a restart, a new replica and a redeploy each run it.
// A write there is not a seed but a schedule, and what it schedules is overwriting whatever an editor
// last saved. Repairing what is MISSING at boot is legitimate; holding an opinion about content is not.
//
// The rule follows one call out of the hook, because that is where the defect lives. A configuration
// hands the work to an imported function which performs the writes in its own body, so a same-file rule
// would read the hook, find no Local API call in it, and report nothing at all. How the call is followed,
// and why it stops at one, is `source-reach.ts`.

import type { SpecSource } from '@airness/node/axe-coverage'
import { NOT_FOUND, occurrences, stripComments } from '@airness/node/source-text'
import { type FoundRootConfig, rootConfigsIn } from './payload-database.js'
import { LOCAL_API_CALLS, payloadReceiverIn } from './payload-policy.js'
import { depthOneMemberIndex, depthOneValue, type PayloadViolation } from './payload-source.js'
import type { LocatedViolation } from './relationship-cleanup.js'
import {
  bodyAfterParameters,
  functionBodyIn,
  functionValueBody,
  type ReachedFunction,
  reachedFunction,
  reachedFunctions,
  valueEnd,
} from './source-reach.js'

/** The operations that change stored state, named as the catalogue spells them. */
const MUTATION_NAMES: ReadonlySet<string> = new Set([
  'create',
  'delete',
  'duplicate',
  'restoreGlobalVersion',
  'restoreVersion',
  'update',
  'updateGlobal',
])

/**
 * The Local API operations that change stored state.
 *
 * Derived from the one catalogue rather than written out again: a second list is a second thing to keep
 * in step, and the two would disagree the first time Payload gained an operation. A read at boot is
 * legitimate and deliberately absent from this set.
 */
const LOCAL_API_MUTATIONS: readonly string[] = LOCAL_API_CALLS.filter((call: string): boolean =>
  MUTATION_NAMES.has(call.slice(1, -1)),
)

/**
 * A configuration's boot hook: the body it writes in place, or the function it names instead.
 *
 * A hook may be written as an arrow or a function expression, as an arrow that returns one call, as a
 * method, or by reference - `onInit: seed`, or the shorthand `onInit` - and each spelling runs the same
 * code at boot. A reference to a function the configuration's own file declares is read as that body; one
 * to an imported function is left for the caller to follow, since only the caller holds the other files.
 */
interface BootstrapHook {
  /** The body the hook runs, or the expression an arrow returns; undefined when it names an import. */
  readonly body: string | undefined
  /** The name the hook is given by, when it names a function rather than writing one. */
  readonly reference: string | undefined
  readonly line: number
}

const HOOK: string = 'onInit'
const REFERENCE: RegExp = /^[a-z_$][\w$]*$/i
// The method form, `async onInit(payload) { ... }`, and the shorthand `onInit`, at the configuration's depth.
const METHOD_HOOK: RegExp = new RegExp(String.raw`^[{,]\s*(?:async\s+)?${HOOK}\s*\(`)
const SHORTHAND_HOOK: RegExp = new RegExp(String.raw`^[{,]\s*${HOOK}\s*[,}]`)

const referenceHook = (code: string, reference: string, line: number): BootstrapHook => ({
  body: functionBodyIn(code, reference),
  reference,
  line,
})

// The hook's value runs to the comma or brace that ends it, so neither a later key nor a later literal is
// read as its body.
const hookOfValue = (code: string, value: string, line: number): BootstrapHook | undefined => {
  const text: string = value.slice(0, valueEnd(value)).trim()
  if (REFERENCE.test(text)) {
    return referenceHook(code, text, line)
  }
  const body: string | undefined = functionValueBody(text)
  return body === undefined ? undefined : { body, reference: undefined, line }
}

const hookOf = (code: string, config: FoundRootConfig): BootstrapHook | undefined => {
  const value: string | undefined = depthOneValue(config.body, HOOK)
  if (value !== undefined) {
    return hookOfValue(code, value, config.line)
  }
  const method: number = depthOneMemberIndex(config.body, METHOD_HOOK)
  if (method !== NOT_FOUND) {
    const body: string | undefined = bodyAfterParameters(config.body, method)
    return body === undefined ? undefined : { body, reference: undefined, line: config.line }
  }
  return depthOneMemberIndex(config.body, SHORTHAND_HOOK) === NOT_FOUND
    ? undefined
    : referenceHook(code, HOOK, config.line)
}

/**
 * The boot hook every root configuration in a source declares.
 * @param code the file's text, with comments already stripped.
 * @returns one entry per configuration that declares `onInit` as a function or a reference to one.
 */
const bootstrapHooksIn = (code: string): readonly BootstrapHook[] =>
  rootConfigsIn(code).flatMap((config: FoundRootConfig): readonly BootstrapHook[] => {
    const hook: BootstrapHook | undefined = hookOf(code, config)
    return hook === undefined ? [] : [hook]
  })

// A Local API mutation performed on a Payload receiver, anywhere in a body.
//
// Distinct operations, not occurrences: a seed that writes two globals performs `updateGlobal` twice,
// and reporting it twice would print the same sentence twice for one repair. What the reader has to act
// on is that the body writes at all.
const mutationsIn = (body: string): readonly string[] => {
  const receiver: RegExp = payloadReceiverIn(body)
  return [
    ...new Set(
      LOCAL_API_MUTATIONS.flatMap((call: string): readonly string[] =>
        occurrences(body, call)
          .filter((found: number): boolean => receiver.test(body.slice(0, found)))
          .map((): string => call.slice(1, -1)),
      ),
    ),
  ]
}

const RULE: string = 'no-boot-time-writes'
const REPAIR: string =
  'move the write behind a command the project runs deliberately, such as a seed script invoked by ' +
  'its own package script'

const directReason = (call: string): string =>
  `onInit: must not write through the Local API; ${call}() here runs on every boot of every ` +
  'container, so a restart, a new replica and a redeploy each overwrite whatever an editor last ' +
  `saved - ${REPAIR}`

const reachedReason = (name: string, specifier: string, call: string): string =>
  `onInit: must not reach a Local API write; it calls ${name}() from "${specifier}", which runs ` +
  `${call}() in its own body, so every boot of every container overwrites whatever an editor last ` +
  `saved - ${REPAIR}`

const reachedViolations = (hook: BootstrapHook, reached: ReachedFunction): readonly PayloadViolation[] =>
  mutationsIn(reached.body).map(
    (call: string): PayloadViolation => ({
      line: hook.line,
      rule: RULE,
      reason: reachedReason(reached.name, reached.specifier, call),
    }),
  )

// One call out of the hook: the name has to be imported, the module has to be one of this member's own
// files, and the function it names has to perform the write itself. A hook that names an imported function
// reaches it without a call of its own, so that function is the one hop.
const reachedWrites = (
  code: string,
  hook: BootstrapHook,
  file: SpecSource,
  files: readonly SpecSource[],
): readonly PayloadViolation[] => {
  const reached: readonly ReachedFunction[] =
    hook.body === undefined
      ? [reachedFunction(code, hook.reference ?? '', file.path, files)].filter(
          (found: ReachedFunction | undefined): found is ReachedFunction => found !== undefined,
        )
      : reachedFunctions(code, hook.body, file.path, files)
  return reached.flatMap((found: ReachedFunction): readonly PayloadViolation[] => reachedViolations(hook, found))
}

/**
 * Report every Local API write a boot hook performs, or reaches in one call.
 *
 * Reported against the configuration that schedules the write rather than the module that performs it:
 * the module may be a legitimate seed, and what makes it a defect is being wired into `onInit`.
 * @param files every source file of the member, as read.
 * @returns one violation per write, located on the configuration that declares the hook.
 */
export const findBootstrapWrites = (files: readonly SpecSource[]): readonly LocatedViolation[] =>
  files.flatMap((file: SpecSource): readonly LocatedViolation[] => {
    const code: string = stripComments(file.source)
    return bootstrapHooksIn(code).flatMap((hook: BootstrapHook): readonly LocatedViolation[] =>
      [
        ...mutationsIn(hook.body ?? '').map(
          (call: string): PayloadViolation => ({
            line: hook.line,
            rule: RULE,
            reason: directReason(call),
          }),
        ),
        ...reachedWrites(code, hook, file, files),
      ].map((violation: PayloadViolation): LocatedViolation => ({ path: file.path, violation })),
    )
  })
