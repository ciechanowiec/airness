import { CLOCK_ENVIRONMENT_VARIABLE, REAL_CLOCK } from './browser-run.js'
import type { Settings } from './settings.js'

interface Context {
  readonly settings: Settings
}

const CLOCK_PRELOAD: string = import.meta.resolve('@airness/node/clock')

/**
 * The environment that starts an application process at the verification clock.
 * @param context the member.
 * @param nodeOptions the `NODE_OPTIONS` the process needs anyway.
 * @returns `NODE_OPTIONS` with the clock preload, and the instant; only `NODE_OPTIONS` for the real clock.
 */
export const clockedEnvironment = (context: Context, nodeOptions: string): Readonly<Record<string, string>> =>
  context.settings.clock === REAL_CLOCK
    ? { NODE_OPTIONS: nodeOptions }
    : {
        NODE_OPTIONS: `${nodeOptions} --import=${CLOCK_PRELOAD}`,
        [CLOCK_ENVIRONMENT_VARIABLE]: context.settings.clock,
      }
