// The axe rule set every scan runs: the sweep's resting, disclosure and interaction passes, and the scans a
// project's own specifications start through `scopedAxe`. It is read from `configs/axe-options.json`, the one
// definition the Java `AxeAudit` of airness-web-evidence reads too, so a crawled page, a page a specification
// scanned and a page a Java test audited cannot be asked different questions. That file says which rules it
// turns on and why; this module only reads it and adds the exclusions of the place a scan runs.
//
// The rules are switched on by id under the run's `rules` option, never by tag through `runOnly`: a tag list
// replaces axe's default set, while an id under `rules` adds to it and touches nothing else.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { asRecord, isRecord } from './json-shapes.js'
import { harnessDirectory } from './project.js'

/** The rules a contrast probe of one hovered or focused element runs: the AA and the AAA ratio. */
export const AXE_CONTRAST_RULES: readonly string[] = ['color-contrast', 'color-contrast-enhanced']

/** Whether one axe rule or check runs. */
interface AxeSwitch {
  readonly enabled: boolean
}

/** The options of one axe check, as axe's run options name them. */
interface AxeCheckSetting {
  readonly enabled?: boolean
  readonly options?: Readonly<Record<string, unknown>>
}

/**
 * The run options every scan hands axe.
 *
 * `checks` is accepted by `axe.run` and `axe.runPartial` and applied by axe's own `getCheckOption`, but
 * axe's published type for the run options leaves it out, so it is declared here. A value of this type
 * is still a value of axe's type: TypeScript checks surplus properties only on a literal written in place.
 */
export interface AxeRunOptions {
  readonly rules: Readonly<Record<string, AxeSwitch>>
  readonly checks: Readonly<Record<string, AxeCheckSetting>>
}

const OPTIONS_FILE: string = 'axe-options.json'

const refused = (reason: string): Error => new Error(`configs/${OPTIONS_FILE} ${reason}`)

const ruleSwitch = ([rule, setting]: readonly [string, unknown]): readonly [string, AxeSwitch] => {
  const enabled: unknown = asRecord(setting)['enabled']
  if (typeof enabled !== 'boolean') {
    throw refused(`switches the rule ${rule} with no boolean enabled`)
  }
  return [rule, { enabled }]
}

const checkSetting = ([check, setting]: readonly [string, unknown]): readonly [string, AxeCheckSetting] => {
  const { enabled, options }: Record<string, unknown> = asRecord(setting)
  if (!((enabled === undefined || typeof enabled === 'boolean') && (options === undefined || isRecord(options)))) {
    throw refused(`sets the check ${check} with neither a boolean enabled nor an options object`)
  }
  // A key written as undefined is still a key to axe, which reads `enabled` by its presence: an
  // `enabled: undefined` would switch the check off. Only what the file names is carried.
  return [check, { ...(typeof enabled === 'boolean' && { enabled }), ...(isRecord(options) && { options }) }]
}

// Read once, when the first scan loads this module. A malformed file fails every scan loudly rather than
// running axe with fewer rules than it names.
const SHIPPED_PATH: string = path.join(harnessDirectory(), 'configs', OPTIONS_FILE)
const SHIPPED_FILE: unknown = JSON.parse(readFileSync(SHIPPED_PATH, 'utf8')) as unknown

const parsed = (file: unknown): AxeRunOptions => {
  const rules: readonly (readonly [string, unknown])[] = Object.entries(asRecord(asRecord(file)['rules']))
  const checks: readonly (readonly [string, unknown])[] = Object.entries(asRecord(asRecord(file)['checks']))
  return {
    rules: Object.fromEntries(rules.map((entry: readonly [string, unknown]) => ruleSwitch(entry))),
    checks: Object.fromEntries(checks.map((entry: readonly [string, unknown]) => checkSetting(entry))),
  }
}

/** The rule that measures a pointer target, whose title axe writes for one size whatever the check measures. */
export const TARGET_SIZE_RULE: string = 'target-size'

/**
 * The title a target-size finding prints, naming the size the shipped check measures.
 *
 * axe titles the rule with the 24 CSS pixels of WCAG 2.5.8 and a spacing allowance, and keeps that title
 * when the check is set to another size, so a finding would ask for less than the scan measured.
 * @param file the parsed options file, the shipped one unless a test hands it another.
 * @returns the title.
 */
export const targetSizeHelp = (file: unknown = SHIPPED_FILE): string => {
  const size: unknown = parsed(file).checks[TARGET_SIZE_RULE]?.options?.['minSize']
  if (typeof size !== 'number') {
    throw refused(`sets no numeric minSize for the check ${TARGET_SIZE_RULE}`)
  }
  return `Every pointer target must be at least ${String(size)} by ${String(size)} CSS pixels`
}

/**
 * The run options of a scan at one location: the shipped rule set and check options, and the location's
 * exclusions.
 *
 * A rule the location excludes stays excluded even when the shipped file turns it on: the exclusions are
 * spread last. A scan narrowed to named rules with `withRules` runs those rules alone, as axe reads
 * `runOnly` before `rules`, while the check options still set the size they measure.
 * @param excluded the rules that do not apply where the scan runs.
 * @param file the parsed options file, the shipped one unless a test hands it another.
 * @returns fresh options, to pass to the builder before any narrowing, which writes into them.
 */
export const axeRunOptions = (excluded: readonly string[], file: unknown = SHIPPED_FILE): AxeRunOptions => {
  const shipped: AxeRunOptions = parsed(file)
  return {
    rules: {
      ...shipped.rules,
      ...Object.fromEntries(excluded.map((rule: string): readonly [string, AxeSwitch] => [rule, { enabled: false }])),
    },
    checks: shipped.checks,
  }
}
