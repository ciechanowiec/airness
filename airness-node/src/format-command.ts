// Formatting is an explicit write operation. Its tools share the gates' selection and configuration.

import { biomeWrite } from './gate-biome.js'
import { cssWrite } from './gate-css.js'
import { eslintWrite } from './gate-eslint.js'
import type { GateResult } from './gate-result.js'
import { type Member, membersOf } from './project.js'

const formatMember = (member: Member): number => {
  console.info(`Formatting ${member.path}`)
  // ESLint fixes can change formatting, so the second Biome pass settles their output.
  const results: readonly GateResult[] = [biomeWrite(member), eslintWrite(member), biomeWrite(member), cssWrite(member)]
  for (const result of results) {
    console.info(result.output ?? result.summary)
  }
  return results.every((result: GateResult): boolean => result.ok) ? 0 : 1
}

/** Apply the shipped formatters to every member; full verification remains a separate Maven invocation. */
export const formatCommand = (directory: string): number => {
  const outcomes: readonly number[] = membersOf(directory).map((member: Member): number => formatMember(member))
  console.info('Formatting applied. Review the changes, then run mvn verify for the verification verdict.')
  return outcomes.every((code: number): boolean => code === 0) ? 0 : 1
}
