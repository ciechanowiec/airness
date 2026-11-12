// What a `delta` run's change scope makes of discovery: which routes and stories the passes are handed, each
// with the reason the change reaches it, and the scope record the summary prints.
import type { DiscoveredStory } from './browser-discovery.js'
import { storyLocation } from './browser-discovery.js'
import {
  isSweepScope,
  SWEEP_SCOPE_ENVIRONMENT_VARIABLE,
  type SweepScope,
  scopeHeadline,
  storyFileOf,
} from './change-scope-policy.js'
import { projectSettings } from './project-settings.js'
import { readJson, type ScopeRecord, type SweepLocation } from './sweep-shared.js'

/** The scope a `delta` run named, or why it could not be read. */
export interface NamedScope {
  readonly scope: SweepScope | undefined
  readonly problem: string | undefined
}

// A scope that cannot be read is a problem the summary fails on, and the sweep covers everything.
export const namedScope = (): NamedScope => {
  const file: string | undefined = process.env[SWEEP_SCOPE_ENVIRONMENT_VARIABLE]
  if (file === undefined) {
    return { scope: undefined, problem: undefined }
  }
  const scope: SweepScope | undefined = readJson(file, isSweepScope)
  return {
    scope,
    problem:
      scope === undefined ? `the change scope ${file} could not be read, so the sweep covered everything` : undefined,
  }
}

/** Something discovery found, and why the scope sweeps it. */
export interface Chosen<Item> {
  readonly item: Item
  readonly reason: string
}

export const chosen = <Item>(
  items: readonly Item[],
  reasonOf: (item: Item) => string | undefined,
): readonly Chosen<Item>[] =>
  items.flatMap((item: Item): readonly Chosen<Item>[] => {
    const reason: string | undefined = reasonOf(item)
    return reason === undefined ? [] : [{ item, reason }]
  })

// The directory a story's Storybook runs in, relative to this application, which its import path is
// relative to.
const storyDirectory = (story: DiscoveredStory): string =>
  projectSettings.auxiliaryServers.find((server): boolean => new URL(server.url).origin === story.server)?.cwd ?? '.'

export const storyFile = (story: DiscoveredStory): string =>
  story.importPath === '' ? '' : storyFileOf(story.importPath, storyDirectory(story))

/** What discovery found, and what of it the scope sweeps. */
export interface ScopedFinds {
  readonly routes: readonly Chosen<SweepLocation>[]
  readonly stories: readonly Chosen<DiscoveredStory>[]
  readonly everyRoute: number
  readonly everyStory: number
}

export const scopeRecordOf = (scope: SweepScope | undefined, finds: ScopedFinds): ScopeRecord => {
  if (scope === undefined) {
    return {
      isScoped: false,
      lines: ['complete: no change scope was given, so every route and story is swept'],
    }
  }
  const changed: readonly string[] = scope.changed.map((file: string): string => `  changed ${file}`)
  if (scope.isComplete) {
    return {
      isScoped: false,
      lines: [scopeHeadline(scope), ...changed, '  swept every route and story'],
    }
  }
  const unreached: string =
    `  not swept: ${String(finds.everyRoute - finds.routes.length)} of ${String(finds.everyRoute)} routes and ` +
    `${String(finds.everyStory - finds.stories.length)} of ${String(finds.everyStory)} stories, which the change ` +
    'does not reach'
  return {
    isScoped: true,
    lines: [
      scopeHeadline(scope),
      ...changed,
      ...finds.routes.map((route: Chosen<SweepLocation>): string => `  swept ${route.item.location}: ${route.reason}`),
      ...finds.stories.map(
        (story: Chosen<DiscoveredStory>): string => `  swept ${storyLocation(story.item)}: ${story.reason}`,
      ),
      unreached,
    ],
  }
}
