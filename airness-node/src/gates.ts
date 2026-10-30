// The gates this package ships. Each one judges one member at a time; `node-gate` runs each in every
// declared node project through `airness-node gate <id> --result <file>`.

import type { GateContext, GateResult } from './exec.js'
import { architecture } from './gate-architecture.js'
import { biome } from './gate-biome.js'
import { build } from './gate-build.js'
import { bundle } from './gate-bundle.js'
import { configReferences } from './gate-config-references.js'
import { css } from './gate-css.js'
import { endToEnd } from './gate-e2e.js'
import { eslint } from './gate-eslint.js'
import { installScripts } from './gate-install-scripts.js'
import { javascriptAllowlist } from './gate-javascript-allowlist.js'
import { knip } from './gate-knip.js'
import { npmInventory } from './gate-npm-inventory.js'
import { npmSbom } from './gate-npm-sbom.js'
import { oxlint } from './gate-oxlint.js'
import { releaseAge } from './gate-release-age.js'
import { sourceRules } from './gate-source-rules.js'
import { tailwindTokens } from './gate-tailwind-tokens.js'
import { tests } from './gate-tests.js'
import { typeCoverage } from './gate-type-coverage.js'
import { types } from './gate-types.js'
import { wiring } from './gate-wiring.js'
import type { Member } from './project.js'

/** One gate: its stable id, what it checks, and the rule it applies to one member. */
export interface Gate {
  /** Stable identifier, which the result file repeats. */
  readonly id: string
  /** What the gate checks, shown in the build log. */
  readonly title: string
  readonly run: (member: Member, context: GateContext) => GateResult | Promise<GateResult>
}

/** Every gate this package ships, in the order the parent binds them. */
export const GATES: readonly Gate[] = [
  { id: 'wiring', title: 'harness wiring', run: wiring },
  { id: 'types', title: 'strict type check', run: types },
  { id: 'biome', title: 'formatting and fast lint', run: biome },
  { id: 'oxlint', title: 'native JavaScript and TypeScript rules', run: oxlint },
  { id: 'eslint', title: 'type-aware lint', run: (member: Member): GateResult => eslint(member) },
  { id: 'source-rules', title: 'source rules', run: sourceRules },
  { id: 'config-refs', title: 'config references', run: configReferences },
  { id: 'javascript-allowlist', title: 'no hand-written JavaScript', run: javascriptAllowlist },
  { id: 'css', title: 'style sheets', run: css },
  { id: 'tailwind-tokens', title: 'token-bound Tailwind values', run: tailwindTokens },
  { id: 'arch', title: 'module architecture', run: architecture },
  { id: 'type-coverage', title: 'type coverage', run: typeCoverage },
  { id: 'knip', title: 'dead code and unused dependencies', run: (member: Member): GateResult => knip(member) },
  { id: 'tests', title: 'suite and coverage', run: tests },
  { id: 'install-scripts', title: 'install-script decisions', run: installScripts },
  { id: 'release-age', title: 'release-age floor', run: releaseAge },
  { id: 'npm-inventory', title: 'npm dependency evidence', run: npmInventory },
  { id: 'npm-sbom', title: 'npm software bill of materials', run: npmSbom },
  { id: 'build', title: 'production build', run: build },
  { id: 'bundle', title: 'client bundle budget', run: bundle },
  { id: 'e2e', title: 'end-to-end suite', run: endToEnd },
]

/**
 * Look up one gate by identifier.
 * @param id the gate id.
 * @returns the gate, or undefined when this package ships none by that id.
 */
export const gateById = (id: string): Gate | undefined => GATES.find((gate: Gate): boolean => gate.id === id)
