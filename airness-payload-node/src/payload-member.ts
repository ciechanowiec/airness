// One member of the node project a Payload gate runs in, as the node layer reads it, with the facts only the
// Payload layer asks about: whether the member is a Payload application, and the Payload settings it declared.
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { parseEnv } from 'node:util'
import type { Member } from '@airness/node/project'
import { declaredDependencies } from './json-shapes.js'
import { type PayloadSettings, readPayloadSettings } from './payload-settings.js'

/** The package whose declaration makes a member a Payload application. */
export const PAYLOAD_PACKAGE: string = 'payload'

/** A member, with what the Payload gates read about it. */
export interface PayloadMember {
  /** The member as the node layer reads it: its directory, manifest and node settings. */
  readonly member: Member
  /** Whether the member declares Payload, which is what makes it a Payload application. */
  readonly isPayload: boolean
  /** The Payload settings the member declared. */
  readonly payload: PayloadSettings
}

/**
 * Read a member as the Payload gates see it.
 * @param member the member as the node layer reads it.
 * @returns the member with its Payload facts.
 */
export const payloadMemberOf = (member: Member): PayloadMember => ({
  member,
  isPayload: Object.hasOwn(declaredDependencies(member.packageJson), PAYLOAD_PACKAGE),
  payload: readPayloadSettings(member.packageJson),
})

/** The environment files a run reads, highest precedence first; `.env.local` overrides `.env`. */
const RUN_ENVIRONMENT_FILES: readonly string[] = ['.env.local', '.env']

/**
 * The environment of a gate that imports the configuration without running the application: the process's
 * own values win over the member's environment files, which win over the analysis placeholders. Only the
 * names the process does not already carry are returned, since the process environment is inherited anyway.
 * @param target the member.
 * @returns the variables to add to the gate's environment.
 */
export const analysisEnvironment = (target: PayloadMember): Readonly<Record<string, string>> => {
  const files: readonly Readonly<Record<string, string | undefined>>[] = RUN_ENVIRONMENT_FILES.map(
    (file: string): string => path.join(target.member.root, file),
  )
    .filter((file: string): boolean => existsSync(file))
    .map((file: string): Readonly<Record<string, string | undefined>> => parseEnv(readFileSync(file, 'utf8')))
  // The earliest source wins, and Object.fromEntries keeps the last entry for a repeated name.
  return Object.fromEntries(
    [...files, target.payload.analysisEnv]
      .flatMap((source: Readonly<Record<string, string | undefined>>): readonly [string, string | undefined][] =>
        Object.entries(source),
      )
      .filter(
        (entry: readonly [string, string | undefined]): entry is [string, string] =>
          entry[1] !== undefined && process.env[entry[0]] === undefined,
      )
      .toReversed(),
  )
}
