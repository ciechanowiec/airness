// Global setup runs in Vitest's Node host, outside every project worker. Install the same real
// network boundary before a project starts a local server or opens its database there.
import './vitest-setup.js'

/** Vitest requires a setup entry even when the guarded module import performs the preparation. */
export const setup = (): undefined => undefined
