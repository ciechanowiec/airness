// Vitest setup file.

// Load .env files (DATABASE_URL, PAYLOAD_SECRET, ...) before any test boots Payload.
import 'dotenv/config'

import { cleanup } from '@testing-library/react'
import fc from 'fast-check'
import { afterEach } from 'vitest'

// Property-based tests (fast-check) run with a FIXED seed and run count, so the same inputs are
// generated on every run. Without this, fast-check defaults to a time-based seed - a volatile gate
// that passes or fails intermittently. Pinning it here makes every `fc.assert` deterministic by
// construction (a per-call seed override is additionally banned by ESLint).
fc.configureGlobal({ seed: 1337, numRuns: 100 })

// Testing Library unmounts what a component spec rendered only through a global `afterEach`, which this suite does
// not expose, so each spec would otherwise find the previous spec's markup still in the document.
afterEach(() => {
  cleanup()
})
