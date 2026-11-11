// One member of the node project a Payload gate runs in, as the node layer reads it, with the facts only the
// Payload layer asks about: whether the member is a Payload application, and the Payload settings it declared.
import { analysisEnvironment as placeholdersUnderLocalEnvironment } from '@airness/node/environment-files'
import { declaredDependencies } from '@airness/node/json-shapes'
import type { Member } from '@airness/node/project'
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

/**
 * The environment of a gate that imports the configuration without running the application: the process's
 * own values win over the member's environment files, which win over the analysis placeholders. Only the
 * names the process does not already carry are returned, since the process environment is inherited anyway.
 * @param target the member.
 * @returns the variables to add to the gate's environment.
 */
export const analysisEnvironment = (target: PayloadMember): Readonly<Record<string, string>> =>
  placeholdersUnderLocalEnvironment(target.member.root, target.payload.analysisEnv)
