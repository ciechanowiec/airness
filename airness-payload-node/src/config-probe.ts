// How a gate runs one of this package's probes inside a member: through tsx with the member's own tsconfig, so
// `payload` and the configuration's imports resolve from the project, with the member as the working directory.
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { type RunResult, runNode } from '@airness/node/exec'
import { failed, type GateResult } from '@airness/node/gate-result'
import { readJson } from '@airness/node/project'
import { payloadConfigPathOf } from './payload-defaults.js'
import { analysisEnvironment, type PayloadMember } from './payload-member.js'

const configFileOf = (root: string): string | undefined => {
  const tsconfig: string = path.join(root, 'tsconfig.json')
  return existsSync(tsconfig) ? path.join(root, payloadConfigPathOf(readJson(tsconfig))) : undefined
}

/**
 * The member's Payload configuration, or the failure that it names none that exists.
 * @param target the member being judged.
 * @returns the configuration's absolute path, or a failed result.
 */
export const payloadConfigFileOf = (target: PayloadMember): string | GateResult => {
  const configFile: string | undefined = configFileOf(target.member.root)
  return configFile !== undefined && existsSync(configFile)
    ? configFile
    : failed('the Payload configuration could not be found', [
        `${target.member.path}: no configuration at ${configFile ?? 'tsconfig.json'}; a Payload member names it ` +
          'under compilerOptions.paths["@payload-config"] in tsconfig.json',
      ])
}

/**
 * The interpreter arguments that run a probe of this package's executable inside a member: tsx with the member's
 * tsconfig, so its path aliases resolve, then the executable and the probe's own command line.
 * @param root the member's directory.
 * @param probe the command line after the executable: the probe's words, then its arguments.
 * @returns the arguments after `node --no-deprecation`.
 */
export const probeArguments = (root: string, probe: readonly string[]): readonly string[] => [
  fileURLToPath(import.meta.resolve('tsx/cli')),
  '--tsconfig',
  path.join(root, 'tsconfig.json'),
  fileURLToPath(new URL('bin.js', import.meta.url)),
  ...probe,
]

/**
 * Run a probe of this package's executable inside the member. It runs where `NODE_ENV` is production, as the
 * deployed application does, which also keeps Payload from regenerating the project's types as it starts.
 * @param target the member being judged.
 * @param probe the command line after the executable: the probe's words, then its arguments.
 * @param timeoutMs how long the probe may take before it is stopped.
 * @returns what the probe exited with and printed.
 */
export const runConfigProbe = (target: PayloadMember, probe: readonly string[], timeoutMs: number): RunResult =>
  runNode('--no-deprecation', probeArguments(target.member.root, probe), {
    cwd: target.member.root,
    env: {
      ...analysisEnvironment(target),
      NODE_ENV: 'production',
    },
    timeoutMs,
  })
