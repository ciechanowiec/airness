// Every custom admin view is scanned for accessibility by a specification of the project's own.
//
// The accessibility sweep skips the admin panel, because the panel is Payload's markup and the crawl carries no
// credential. A custom view is the project's markup behind that exemption, and nothing else in the harness looks
// at it. The harness cannot scan it, since it cannot sign in and does not know which container is the project's
// rather than the framework's, so it requires the project to have scanned it instead.

import type { SpecSource } from '@airness/node/axe-coverage'
import { failed, type GateResult, notApplicable, passed } from '@airness/node/gate-result'
import { type DeclaredAdminView, findDeclaredAdminViews, findUnscannedAdminViews } from './admin-view-coverage.js'
import type { PayloadMember } from './payload-member.js'
import type { PayloadViolation } from './payload-source.js'
import { payloadSourcesOf } from './payload-sources.js'

// Where a member's specifications live, by the node layer's convention.
const SPEC_ROOT: string = 'tests/'

/**
 * Fail on every custom admin view no specification of the project scans.
 * @param target the member being judged.
 * @returns the gate result.
 */
export const adminViews = (target: PayloadMember): GateResult => {
  if (!target.isPayload) {
    return notApplicable('this member is no Payload application')
  }
  const files: readonly SpecSource[] = payloadSourcesOf(target)
  const specs: readonly SpecSource[] = files.filter((file: SpecSource): boolean => file.path.startsWith(SPEC_ROOT))
  // A configuration is looked for outside `tests/` alone, so a fixture written inside a specification is not
  // read as a view this project serves.
  const declared: readonly (readonly [SpecSource, readonly DeclaredAdminView[]])[] = files
    .filter((file: SpecSource): boolean => !file.path.startsWith(SPEC_ROOT))
    .map((file: SpecSource): readonly [SpecSource, readonly DeclaredAdminView[]] => [
      file,
      findDeclaredAdminViews(file.source),
    ])
  const findings: readonly string[] = declared.flatMap(
    ([file, views]: readonly [SpecSource, readonly DeclaredAdminView[]]): readonly string[] =>
      findUnscannedAdminViews(views, specs, files).map(
        (violation: PayloadViolation): string =>
          `${file.path}:${String(violation.line)} [${violation.rule}] ${violation.reason}`,
      ),
  )
  if (findings.length > 0) {
    return failed(`${String(findings.length)} custom admin view(s) are not scanned`, findings)
  }
  const total: number = declared.reduce(
    (count: number, [, views]: readonly [SpecSource, readonly DeclaredAdminView[]]): number => count + views.length,
    0,
  )
  return passed(
    total === 0
      ? 'this project declares no custom admin view'
      : `${String(total)} custom admin view(s) are scanned for accessibility`,
  )
}
