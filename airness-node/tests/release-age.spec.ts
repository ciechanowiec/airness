import { describe, expect, it } from 'vitest'
import { releaseAgeSettingFindings, youngReleaseFindings } from '@/release-age.js'

const DAY_MS: number = 86_400_000
const HOUR_MS: number = 3_600_000
const NOW: number = Date.parse('2026-10-24T12:00:00Z')

describe('the release-age settings', () => {
  it('passes a strict floor of a day that excludes only the harness', () => {
    expect(
      releaseAgeSettingFindings({
        minimumReleaseAge: 1440,
        minimumReleaseAgeStrict: true,
        minimumReleaseAgeExclude: ['@airness/node', '@airness/node@1.4.0'],
      }),
    ).toEqual([])
  })

  it('fails a missing file, a short floor, a lenient floor and an exclusion of anything else', () => {
    expect(releaseAgeSettingFindings(undefined)).toHaveLength(2)
    expect(
      releaseAgeSettingFindings({
        minimumReleaseAge: 60,
        minimumReleaseAgeStrict: false,
        minimumReleaseAgeExclude: ['left-pad'],
      }),
    ).toEqual([
      'pnpm-workspace.yaml minimumReleaseAge: must be at least 1440 (a day, in minutes), so pnpm refuses a ' +
        'release younger than that',
      'pnpm-workspace.yaml minimumReleaseAgeStrict: must be true; without it pnpm installs a release younger than ' +
        'the floor and writes an exclusion for it rather than refusing',
      'pnpm-workspace.yaml minimumReleaseAgeExclude: names left-pad; only a harness package may be excluded from ' +
        'the floor, so wait for the release to age instead',
    ])
  })
})

describe('young releases', () => {
  it('names a release inside the floor with its age, and passes one older than it', () => {
    expect(
      youngReleaseFindings(
        [
          { name: 'fresh', version: '1.0.0', publishedAt: NOW - 5 * HOUR_MS },
          { name: 'aged', version: '1.0.0', publishedAt: NOW - DAY_MS },
        ],
        NOW,
      ),
    ).toEqual([
      'fresh@1.0.0 was published 5 hour(s) ago; a release must be 24 hours old before it is installed, so pin an ' +
        'older version or wait',
    ])
  })
})
