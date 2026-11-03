// The versions the Payload layer decides for a Payload application: Payload, the packages Payload declares as
// peers, every first-party Payload package, and the Next.js and React it renders with. @airness/payload imports none
// of them, so it cannot pin them by declaring a dependency, and declaring them would install a second Payload
// into a project that already has one. This list is that one source instead, and the preflight gate holds
// what the application installed to it.

/** The prefix of every first-party Payload package, which Payload releases at its own version. */
const PAYLOAD_SCOPE: string = '@payloadcms/'

/** The Payload version every first-party Payload package must equal. */
const PAYLOAD_VERSION: string = '3.92.1'

/** The packages a Payload application must declare, each at the version the layer decides. */
export const REQUIRED_PINS: Readonly<Record<string, string>> = {
  payload: PAYLOAD_VERSION,
  // Held on 16: graphql 17 exists, and Payload declares graphql ^16.8.1 as a peer.
  graphql: '16.14.2',
  sharp: '0.35.5',
  next: '16.4.3',
  react: '19.3.1',
  'react-dom': '19.3.1',
}

/** The packages a Payload application may declare, held to the layer's version when it does. */
const OPTIONAL_PINS: Readonly<Record<string, string>> = {
  '@types/react': '19.3.0',
  '@types/react-dom': '19.3.0',
}

/** What a member declares and what it installed for one package. */
export interface InstalledPackage {
  readonly name: string
  /** The installed version, or undefined when the package is not installed. */
  readonly version: string | undefined
}

/**
 * The version the layer decides for a package, if it decides one.
 * @param name the package name.
 * @returns the pinned version, or undefined for a package the layer leaves to the project.
 */
export const pinnedVersionOf = (name: string): string | undefined =>
  name.startsWith(PAYLOAD_SCOPE) ? PAYLOAD_VERSION : (REQUIRED_PINS[name] ?? OPTIONAL_PINS[name])

/**
 * Every pin a Payload member breaks.
 * @param declared the names the member declares in any dependency block.
 * @param installed what the member installed for every pinned name it declares or must declare.
 * @returns one finding per missing or mismatched package, each naming its repair.
 */
export const findPinViolations = (
  declared: readonly string[],
  installed: readonly InstalledPackage[],
): readonly string[] => {
  const missing: readonly string[] = Object.keys(REQUIRED_PINS)
    .filter((name: string): boolean => !declared.includes(name))
    .map(
      (name: string): string =>
        `${name} is not declared; a Payload application declares it at exactly ${String(REQUIRED_PINS[name])}`,
    )
  const mismatched: readonly string[] = installed.flatMap((entry: InstalledPackage): readonly string[] => {
    const pinned: string | undefined = pinnedVersionOf(entry.name)
    if (pinned === undefined || !declared.includes(entry.name)) {
      return []
    }
    if (entry.version === undefined) {
      return [`${entry.name} is declared but not installed; run pnpm install`]
    }
    return entry.version === pinned
      ? []
      : [`${entry.name} resolves to ${entry.version}, but the Payload layer runs ${pinned}; declare exactly ${pinned}`]
  })
  return [...missing, ...mismatched]
}
