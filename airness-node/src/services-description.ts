// What a services run tells the command it runs when the image gate asks for it: the run's prefix and network,
// the variables it handed over, and where each published port leads. The image gate runs the project's image as a
// container beside the services, and a container cannot reach a port Docker published on the host's loopback, so
// it is told the service container and port behind each published one, which the run's network resolves.
import { asRecord, asText, isArray, parseJsonc } from './json-shapes.js'

/**
 * The variable the image gate sets for the services wrapper: the services start as a fresh deployment finds them,
 * with no preparation run against them, and the run describes them to the command.
 */
export const IMAGE_VERIFICATION_VARIABLE: string = 'AIRNESS_IMAGE_VERIFICATION'

/** The value of {@link IMAGE_VERIFICATION_VARIABLE} that turns the mode on. */
export const IMAGE_VERIFICATION_ON: string = '1'

/** The variable a run in that mode hands the command its description in, as JSON. */
export const SERVICES_DESCRIPTION_VARIABLE: string = 'AIRNESS_SERVICES'

/** One port a service published on the host's loopback, and the container port behind it. */
export interface PublishedPort {
  /** The loopback port the handed-over variables name. */
  readonly port: number
  /** The service container, by the name the run's network resolves. */
  readonly host: string
  /** The port the service listens on inside its container. */
  readonly containerPort: number
}

/** What a run started and handed over. */
export interface ServicesDescription {
  /** The prefix of every name the run creates, which its removal finds them by. */
  readonly prefix: string
  /** The run's private network. */
  readonly network: string
  /** The variables the plan handed the command. */
  readonly variables: readonly string[]
  readonly published: readonly PublishedPort[]
}

const isPort = (value: unknown): value is number =>
  typeof value === 'number' && Number.isSafeInteger(value) && value > 0

const publishedOf = (raw: unknown): readonly PublishedPort[] | undefined => {
  if (!isArray(raw)) {
    return undefined
  }
  const read: readonly (PublishedPort | undefined)[] = raw.map((entry: unknown): PublishedPort | undefined => {
    const record: Record<string, unknown> = asRecord(entry)
    const { port, containerPort } = record
    const host: string = asText(record['host'])
    return isPort(port) && isPort(containerPort) && host.length > 0 ? { port, host, containerPort } : undefined
  })
  return read.every((entry: PublishedPort | undefined): entry is PublishedPort => entry !== undefined)
    ? read
    : undefined
}

/**
 * Read a description from the variable a run handed it in.
 * @param raw the variable's value, undefined when the run handed none.
 * @returns the description, or undefined when there is none or it is malformed.
 */
export const parseServicesDescription = (raw: string | undefined): ServicesDescription | undefined => {
  if (raw === undefined) {
    return undefined
  }
  const record: Record<string, unknown> = asRecord(parseJsonc(raw).value)
  const prefix: string = asText(record['prefix'])
  const network: string = asText(record['network'])
  const variables: unknown = record['variables']
  const published: readonly PublishedPort[] | undefined = publishedOf(record['published'])
  const isNamed: boolean = prefix.length > 0 && network.length > 0
  return isNamed &&
    published !== undefined &&
    isArray(variables) &&
    variables.every((name: unknown): name is string => typeof name === 'string')
    ? { prefix, network, variables, published }
    : undefined
}
