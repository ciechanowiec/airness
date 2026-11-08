// The Payload gates, each bound by airness-parent-payload as one airness:node-gate execution. Registry order is
// the order the parent binds them in: the precondition first, then the source reads, then the gates that
// import the configuration.

import type { GateResult } from '@airness/node/gate-result'
import { adminViews } from './gate-admin-views.js'
import { payloadBlocklist } from './gate-payload-blocklist.js'
import { payloadDefaults } from './gate-payload-defaults.js'
import { payloadEnvironment } from './gate-payload-environment.js'
import { payloadGenerated } from './gate-payload-generated.js'
import { payloadMigrations } from './gate-payload-migrations.js'
import { payloadSourceRules } from './gate-payload-source-rules.js'
import { preflight } from './gate-preflight.js'
import type { PayloadMember } from './payload-member.js'

/** One Payload gate. */
export interface Gate {
  /** The gate id, which the result file repeats and `airness:node-gate` checks. */
  readonly id: string
  /** What the gate checks, shown with its verdict. */
  readonly title: string
  /** Judge one member; every member of the node project is given for the gates that ask about all of them. */
  readonly run: (target: PayloadMember, members: readonly PayloadMember[]) => GateResult
}

/** Every Payload gate, in the order airness-parent-payload binds them. */
export const GATES: readonly Gate[] = [
  { id: 'preflight', title: 'supported Payload application', run: preflight },
  { id: 'payload-blocklist', title: 'packages the Payload layer refuses', run: payloadBlocklist },
  { id: 'payload-source-rules', title: 'Payload source rules', run: payloadSourceRules },
  { id: 'admin-views', title: 'custom admin views scanned', run: adminViews },
  { id: 'payload-environment', title: 'validated environment coherence', run: payloadEnvironment },
  { id: 'payload-generated', title: 'generated Payload artefacts', run: payloadGenerated },
  { id: 'payload-defaults', title: 'access decided in the built configuration', run: payloadDefaults },
  { id: 'payload-migrations', title: 'every schema change recorded by a migration', run: payloadMigrations },
]

/**
 * Look a gate up by its id.
 * @param id the gate id.
 * @returns the gate, or undefined for an id no gate has.
 */
export const gateById = (id: string): Gate | undefined => GATES.find((gate: Gate): boolean => gate.id === id)
