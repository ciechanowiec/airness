// The time a spec that starts a process may take. Vitest's five-second default is a budget for code in the
// test's own process; a spec that starts git or Node pays a process start and the machine's load on top.

/** The options of a suite whose tests start processes. */
export const PROCESS_TIME: { readonly timeout: number } = { timeout: 120_000 }
