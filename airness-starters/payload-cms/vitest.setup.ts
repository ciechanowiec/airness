// Vitest setup file.

// Load .env files (DATABASE_URL, PAYLOAD_SECRET, ...) before any test boots Payload.
import 'dotenv/config'

import fc from 'fast-check'

// Property-based tests (fast-check) run with a FIXED seed and run count, so the same inputs are
// generated on every run. Without this, fast-check defaults to a time-based seed - a volatile gate
// that passes or fails intermittently. Pinning it here makes every `fc.assert` deterministic by
// construction (a per-call seed override is additionally banned by ESLint).
fc.configureGlobal({ seed: 1337, numRuns: 100 })
