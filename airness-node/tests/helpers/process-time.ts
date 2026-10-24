// The time a spec that starts a process may take. Vitest's five-second default is a budget for code in the
// test's own process; a spec that starts git, a gate or an analyzer pays a process start and the machine's
// load on top, and a loaded build host exceeded the default on a spec that was correct. The harness's own
// suites pass this to each suite that starts processes, and leave the configuration the harness ships alone.

/** The options of a suite whose tests start processes. */
export const PROCESS_TIME: { readonly timeout: number } = { timeout: 120_000 }
