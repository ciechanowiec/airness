// Require cleanup when a required relationship would otherwise prevent deletion of the referenced document.

import type { SpecSource } from '@airness/node/axe-coverage'
import { enclosingLiteral, occurrences, stripComments } from '@airness/node/source-text'
import { type FoundPayloadConfig, payloadConfigsIn } from './payload-configs.js'
import { DRAFTS_ENABLED, depthOneBlockKeys, depthOneValue, type PayloadViolation } from './payload-source.js'

/** A violation together with the file it was found in, because this rule reads more than one. */
export interface LocatedViolation {
  readonly path: string
  readonly violation: PayloadViolation
}

// The two field types that put a foreign key on the table rather than in the join table. `upload` is
// one of them: it is a relationship to the media collection wearing a different name.
const KEYED_FIELD_TYPES: readonly string[] = ['relationship', 'upload']

// Biome normalises a project to single quotes; nothing obliges a project to have run it yet.
const QUOTE_STYLES: readonly string[] = ["'", '"']

const TRUE_VALUE: RegExp = /^\s*true\b/
const QUOTED_VALUE: RegExp = /^\s*['"`]([^'"`]+)['"`]/
const CLEANUP_HOOK: string = 'beforeDelete'

/**
 * One collection or global as this rule needs it: what it is called, what it declares, where it was
 * found, and whether it is a global, which can hold a relationship but is never its target.
 */
interface FoundCollection {
  readonly path: string
  readonly slug: string
  readonly body: string
  readonly line: number
  readonly global: boolean
}

const declaresTrue = (field: string, key: string): boolean => TRUE_VALUE.test(depthOneValue(field, key) ?? '')

// The slug a `relationTo` names, and only when it names exactly one. An array is the polymorphic form,
// which Payload stores in the join table instead and which therefore carries no column to constrain.
const singleTarget = (field: string): string | undefined =>
  QUOTED_VALUE.exec(depthOneValue(field, 'relationTo') ?? '')?.[1]

const slugOf = (body: string): string | undefined => QUOTED_VALUE.exec(depthOneValue(body, 'slug') ?? '')?.[1]

// A collection or global with drafts enabled gets no null constraint anywhere, so none of its required
// fields can reach the contradiction.
const declaresDrafts = (body: string): boolean => DRAFTS_ENABLED.test(depthOneValue(body, 'versions') ?? '')

// Which literal in a file IS a collection or a global is `payload-configs.ts`'s question, and this rule
// asks it there rather than carrying a second copy of the answer. Both kinds are read. A global is a
// holder: the adapter builds a table for it like for a collection, and a required single-target
// relationship or upload on it gets the same NOT NULL column against the same ON DELETE SET NULL key. A
// literal with no readable slug is dropped, because the whole rule is a lookup by slug.
//
// Comments are blanked before anything is read, the way every other Payload rule reads a file. The keys
// this rule looks for sit at depth one, and a depth-one key is recognised by the delimiter in front of
// it - so a comment between the previous entry and `hooks:` hid the hook, and the rule reported a
// collection that was guarded. `stripComments` preserves offsets and newlines, so the reported line is
// still the line of the source.
const collectionsIn = (file: SpecSource): readonly FoundCollection[] =>
  payloadConfigsIn(stripComments(file.source)).flatMap((found: FoundPayloadConfig): readonly FoundCollection[] => {
    const slug: string | undefined = slugOf(found.body)
    return slug === undefined
      ? []
      : [{ path: file.path, slug, body: found.body, line: found.line, global: found.kind.kind === 'global' }]
  })

// Every field of one collection or global that puts a NOT NULL column against a collection's primary key.
// The field literal is recovered from the offset of its own `type` key, so a relationship nested inside
// an array or a group is read exactly like one declared at the top: both get a column, on their own
// table, with the same pair of settings.
const constrainedTargets = (collection: FoundCollection): readonly string[] => {
  if (declaresDrafts(collection.body)) {
    return []
  }
  return KEYED_FIELD_TYPES.flatMap((fieldType: string): readonly string[] =>
    QUOTE_STYLES.flatMap((quote: string): readonly number[] =>
      occurrences(collection.body, `${quote}${fieldType}${quote}`),
    )
      .map((at: number): string | undefined => {
        const field: string | undefined = enclosingLiteral(collection.body, at)
        return field === undefined || !isConstrained(field, fieldType) ? undefined : singleTarget(field)
      })
      .filter((slug: string | undefined): slug is string => slug !== undefined),
  )
}

// The four conditions the adapter itself applies, read back off the field. `hasMany` moves the
// relationship into the join table; `admin.condition` and an array `relationTo` each drop the null
// constraint; and without `required` there is nothing to contradict.
const isConstrained = (field: string, fieldType: string): boolean =>
  QUOTED_VALUE.exec(depthOneValue(field, 'type') ?? '')?.[1] === fieldType &&
  declaresTrue(field, 'required') &&
  !declaresTrue(field, 'hasMany') &&
  !depthOneBlockKeys(field, 'admin').includes('condition') &&
  singleTarget(field) !== undefined

const declaresCleanup = (target: FoundCollection): boolean =>
  depthOneBlockKeys(target.body, 'hooks').includes(CLEANUP_HOOK)

const reason = (target: string): string =>
  `a required relationship to \`${target}\` gives this table a NOT NULL column against a foreign key ` +
  `Payload declares ON DELETE SET NULL, so deleting a ${target} row aborts on this table's ` +
  `constraint; declare a ${CLEANUP_HOOK} hook on ${target} that removes its dependants first`

/**
 * Every required single-value relationship, on a collection or a global, whose target collection cannot
 * be deleted because of it.
 *
 * Reads the whole candidate set at once, because the collection a `relationTo` names is declared in
 * another file. A target this set does not declare - a collection a plugin contributes, say - is left
 * alone rather than reported, since nothing here can see whether it takes its dependants down.
 * @param files every source file the gate collected, as a path and its text.
 * @returns one violation per unguarded relationship, located in the file that declares it.
 */
export const findUnguardedRelationships = (files: readonly SpecSource[]): readonly LocatedViolation[] => {
  const collections: readonly FoundCollection[] = files.flatMap((file: SpecSource): readonly FoundCollection[] =>
    collectionsIn(file),
  )
  // A `relationTo` names a collection only, and a global may share a collection's slug.
  const bySlug: ReadonlyMap<string, FoundCollection> = new Map(
    collections
      .filter((collection: FoundCollection): boolean => !collection.global)
      .map((collection: FoundCollection): [string, FoundCollection] => [collection.slug, collection]),
  )
  return collections.flatMap((collection: FoundCollection): readonly LocatedViolation[] =>
    constrainedTargets(collection)
      .map((slug: string): FoundCollection | undefined => bySlug.get(slug))
      .filter((target: FoundCollection | undefined): target is FoundCollection => target !== undefined)
      .filter((target: FoundCollection): boolean => !declaresCleanup(target))
      .map(
        (target: FoundCollection): LocatedViolation => ({
          path: collection.path,
          violation: {
            line: collection.line,
            rule: 'require-relationship-cleanup',
            reason: reason(target.slug),
          },
        }),
      ),
  )
}
