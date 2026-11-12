import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { describe, expect, it } from 'vitest'
import { type AxeRunOptions, axeRunOptions, contrastRules, TARGET_SIZE_RULE, targetSizeHelp } from '@/axe-rules.js'
import { asRecord, asText, isArray } from '@/json-shapes.js'
import type { AccessibilityLevels } from '@/settings.js'

// Both costly rules at AA, as every project is held to them, and both raised to AAA, as a project opts in.
const AA_LEVELS: AccessibilityLevels = { targetSize: 'AA', contrast: 'AA' }
const AAA_LEVELS: AccessibilityLevels = { targetSize: 'AAA', contrast: 'AAA' }

// The rules a scan turns on, read off the options it hands axe rather than off a second list.
const enabledRules = (options: AxeRunOptions): readonly string[] =>
  Object.entries(options.rules)
    .filter(([, setting]: readonly [string, { readonly enabled: boolean }]): boolean => setting.enabled)
    .map(([rule]: readonly [string, unknown]): string => rule)
const AXE_ENABLED_RULES: readonly string[] = enabledRules(axeRunOptions([]))
const AAA_ENABLED_RULES: readonly string[] = enabledRules(axeRunOptions([], AAA_LEVELS))
const TARGET_SIZE_CHECK: string = 'target-size'
const TARGET_OFFSET_CHECK: string = 'target-offset'

// The axe-core the sweep injects is the one `@axe-core/playwright` resolves, not one this package names, so
// it is loaded from there: the ids below are checked against the very rules a scan runs, and a typo, or an
// axe upgrade that renames a rule, fails here instead of silently leaving the rule out.
const fromHere: NodeJS.Require = createRequire(import.meta.url)
const axe: Record<string, unknown> = asRecord(
  createRequire(fromHere.resolve('@axe-core/playwright'))('axe-core') as unknown,
)
const audit: Record<string, unknown> = asRecord(axe['_audit'])
const utilities: Record<string, unknown> = asRecord(axe['utils'])

const rules: readonly unknown[] = isArray(audit['rules']) ? audit['rules'] : []
const ruleOf = (id: string): Record<string, unknown> =>
  asRecord(rules.find((rule: unknown): boolean => asRecord(rule)['id'] === id))
const tagsOf = (id: string): readonly unknown[] => {
  const tags: unknown = ruleOf(id)['tags']
  return isArray(tags) ? tags : []
}
const checkIdsOf = (id: string): readonly string[] => {
  const checks: unknown = ruleOf(id)['any']
  return isArray(checks) ? checks.map((check: unknown): string => asText(asRecord(check)['id'])) : []
}

// axe's own decision whether a rule runs, and with which check options, given the options a scan hands
// it. Both are exported on `axe.utils`, so the test asks axe rather than restating its precedence.
type RuleShouldRun = (rule: unknown, context: unknown, options: unknown) => boolean
type CheckOption = (check: unknown, ruleId: string, options: unknown) => unknown
const shouldRuleRun: RuleShouldRun = utilities['ruleShouldRun'] as RuleShouldRun
const getCheckOption: CheckOption = utilities['getCheckOption'] as CheckOption
const doesRun = (id: string, options: unknown): boolean => shouldRuleRun(ruleOf(id), { page: true }, options)
const checkOption = (id: string, options: unknown): Record<string, unknown> =>
  asRecord(getCheckOption(asRecord(audit['checks'])[id], 'target-size', options))

// The one file the sweep and the Java audit both read.
const shippedFile: Record<string, unknown> = asRecord(
  JSON.parse(readFileSync(new URL('../configs/axe-options.json', import.meta.url), 'utf8')) as unknown,
)

describe('the shipped axe options file', () => {
  it('is what every scan hands axe, rule for rule and check for check', () => {
    expect(axeRunOptions([])).toEqual({ rules: shippedFile['rules'], checks: shippedFile['checks'] })
  })

  it('names only rules and checks the pinned axe-core defines, the raised levels included', () => {
    const raised: readonly Record<string, unknown>[] = Object.values(asRecord(shippedFile['levels'])).map(
      (rule: unknown): Record<string, unknown> => asRecord(asRecord(rule)['AAA']),
    )
    const shippedRules: readonly string[] = [shippedFile, ...raised].flatMap((part: unknown): readonly string[] =>
      Object.keys(asRecord(asRecord(part)['rules'])),
    )
    const shippedChecks: readonly string[] = [shippedFile, ...raised].flatMap((part: unknown): readonly string[] =>
      Object.keys(asRecord(asRecord(part)['checks'])),
    )
    const checks: Record<string, unknown> = asRecord(audit['checks'])
    for (const id of shippedRules) {
      expect(ruleOf(id)['id'], id).toBe(id)
    }
    for (const id of shippedChecks) {
      expect(asRecord(checks[id])['id'], id).toBe(id)
    }
  })

  it('refuses a rule switched without a boolean, rather than running axe without it', () => {
    expect(() => axeRunOptions([], AA_LEVELS, { rules: { 'target-size': { enabled: 'yes' } } })).toThrow(
      'configs/axe-options.json switches the rule target-size with no boolean enabled',
    )
  })

  it('refuses a check whose switch or options are of the wrong shape', () => {
    for (const setting of [{ enabled: 1 }, { options: 44 }]) {
      expect(() => axeRunOptions([], AA_LEVELS, { checks: { 'target-size': setting } })).toThrow(
        'configs/axe-options.json sets the check target-size',
      )
    }
  })

  it('carries only the keys a check names, since axe reads a present enabled as a switch', () => {
    const options: AxeRunOptions = axeRunOptions([], AA_LEVELS, {
      checks: { 'target-size': { options: { minSize: 44 } }, 'target-offset': { enabled: false } },
    })
    expect(Object.keys(options.checks['target-size'] ?? {})).toEqual(['options'])
    expect(Object.keys(options.checks['target-offset'] ?? {})).toEqual(['enabled'])
  })

  it('reads a file that names no rule or check as adding nothing', () => {
    expect(axeRunOptions(['region'], AA_LEVELS, {})).toEqual({ rules: { region: { enabled: false } }, checks: {} })
  })

  it('refuses a raised level the file does not name, rather than measuring it at AA', () => {
    expect(() => axeRunOptions([], { targetSize: 'AAA', contrast: 'AA' }, {})).toThrow(
      'configs/axe-options.json names no AAA level for targetSize',
    )
  })

  it('names the contrast probes rules axe runs by default or the file turns on at their level', () => {
    for (const id of contrastRules('AAA')) {
      expect(doesRun(id, {}) || AAA_ENABLED_RULES.includes(id), id).toBe(true)
    }
  })

  it('probes the AA ratio alone at AA, and the AAA ratio too once a project raises it', () => {
    expect(contrastRules('AA')).toEqual(['color-contrast'])
    expect(contrastRules('AAA')).toEqual(['color-contrast', 'color-contrast-enhanced'])
  })
})

describe('the axe rule set', () => {
  it('turns on the cheap AAA, target-size and wanted experimental rules for every project', () => {
    expect(AXE_ENABLED_RULES).toEqual([
      'identical-links-same-purpose',
      'meta-refresh-no-exceptions',
      'target-size',
      'css-orientation-lock',
      'p-as-heading',
      'td-has-header',
      'table-fake-caption',
    ])
  })

  it('adds the enhanced contrast for a project that raises the contrast of text', () => {
    expect(AAA_ENABLED_RULES).toEqual([...AXE_ENABLED_RULES, 'color-contrast-enhanced'])
    expect(doesRun('color-contrast-enhanced', axeRunOptions([]))).toBe(false)
    expect(doesRun('color-contrast-enhanced', axeRunOptions([], { targetSize: 'AA', contrast: 'AAA' }))).toBe(true)
  })

  it('names only rules the pinned axe-core defines', () => {
    for (const id of [...AAA_ENABLED_RULES, ...contrastRules('AAA')]) {
      expect(ruleOf(id)['id'], id).toBe(id)
    }
  })

  it('names only rules axe would not run by default, so each entry widens the set', () => {
    for (const id of AAA_ENABLED_RULES) {
      expect(doesRun(id, {}), id).toBe(false)
    }
  })

  it('turns on every rule it names under the options a scan hands axe', () => {
    for (const id of AXE_ENABLED_RULES) {
      expect(doesRun(id, axeRunOptions([])), id).toBe(true)
    }
  })

  it('keeps the default rules of every tag, including best practice and other standards', () => {
    for (const id of ['color-contrast', 'region', 'landmark-one-main', 'heading-order', 'image-alt']) {
      expect(doesRun(id, axeRunOptions([])), id).toBe(true)
    }
    expect(axeRunOptions([])).not.toHaveProperty('runOnly')
  })

  it('leaves out the experimental rules that report only review items', () => {
    for (const id of ['hidden-content', 'focus-order-semantics']) {
      expect(tagsOf(id), id).toContain('experimental')
      expect(AXE_ENABLED_RULES).not.toContain(id)
      expect(doesRun(id, axeRunOptions([])), id).toBe(false)
    }
  })

  it('lets a location exclude a rule, even one the set turns on', () => {
    const options: AxeRunOptions = axeRunOptions(['region', 'target-size'], AAA_LEVELS)
    expect(doesRun('region', options)).toBe(false)
    expect(doesRun('target-size', options)).toBe(false)
    expect(doesRun('color-contrast-enhanced', options)).toBe(true)
  })

  it('runs exactly the named rules of a scan narrowed with withRules', () => {
    const narrowed: unknown = {
      ...axeRunOptions([], AAA_LEVELS),
      runOnly: { type: 'rule', values: [...contrastRules('AAA')] },
    }
    expect(doesRun('color-contrast', narrowed)).toBe(true)
    expect(doesRun('color-contrast-enhanced', narrowed)).toBe(true)
    expect(doesRun('target-size', narrowed)).toBe(false)
  })
})

describe('the target size', () => {
  it('measures the checks the pinned target-size rule is made of', () => {
    expect(checkIdsOf('target-size')).toEqual([TARGET_SIZE_CHECK, TARGET_OFFSET_CHECK])
  })

  it('names exactly the checks of the target-size rule once a project raises it, and none at AA', () => {
    expect(Object.keys(axeRunOptions([], AAA_LEVELS).checks)).toEqual([TARGET_SIZE_CHECK, TARGET_OFFSET_CHECK])
    expect(axeRunOptions([]).checks).toEqual({})
  })

  it('measures a target against the 24 CSS pixels of AA, with the room around it, by default', () => {
    expect(checkOption(TARGET_SIZE_CHECK, axeRunOptions([]))).toMatchObject({ options: { minSize: 24 } })
    expect(checkOption(TARGET_OFFSET_CHECK, axeRunOptions([]))).toMatchObject({ enabled: true })
  })

  it('measures a raised target against 44 CSS pixels', () => {
    expect(checkOption(TARGET_SIZE_CHECK, axeRunOptions([], { targetSize: 'AAA', contrast: 'AA' }))).toMatchObject({
      enabled: true,
      options: { minSize: 44 },
    })
  })

  it('names the size under the option the pinned check reads', () => {
    expect(checkOption(TARGET_SIZE_CHECK, {})).toMatchObject({ options: { minSize: 24 } })
  })

  it('gives an undersized target no pass for the room around it once a project raises the size', () => {
    expect(checkOption(TARGET_OFFSET_CHECK, {})).toMatchObject({ enabled: true })
    expect(checkOption(TARGET_OFFSET_CHECK, axeRunOptions([], AAA_LEVELS))).toMatchObject({ enabled: false })
  })

  // axe selects no target that sits in a line of text, so the 2.5.5 inline exception holds before any
  // check measures; the options switch the rule and its checks and replace nothing else of it.
  it('keeps the inline exception, which the rule applies before any check', () => {
    const matches: unknown = ruleOf('target-size')['matches']
    expect(typeof matches === 'function' ? matches.name : '').toBe('widgetNotInline')
    expect(axeRunOptions([]).rules['target-size']).toEqual({ enabled: true })
  })
})

describe('the title of a target-size finding', () => {
  it('keeps the title axe writes at AA, which names the size the check measures', () => {
    expect(targetSizeHelp()).toBeUndefined()
  })

  it('names the 44 pixels a raised check measures, not the 24 of the title axe writes', () => {
    expect(targetSizeHelp(AAA_LEVELS)).toBe('Every pointer target must be at least 44 by 44 CSS pixels')
  })

  it('follows the size the file sets', () => {
    const file: unknown = { checks: { [TARGET_SIZE_RULE]: { options: { minSize: 48 } } } }
    expect(targetSizeHelp(AA_LEVELS, file)).toBe('Every pointer target must be at least 48 by 48 CSS pixels')
  })

  it('refuses a file that sets options without a numeric size, rather than printing a title without one', () => {
    for (const checks of [
      { [TARGET_SIZE_RULE]: { options: {} } },
      { [TARGET_SIZE_RULE]: { options: { minSize: '44' } } },
    ]) {
      expect(() => targetSizeHelp(AA_LEVELS, { checks })).toThrow(
        'configs/axe-options.json sets no numeric minSize for the check target-size',
      )
    }
  })
})
