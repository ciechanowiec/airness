// Every Git a test starts reads the managed configuration, whatever the machine running the suite configured:
// its hooks, its signing, its ignore and attribute files and its default branch name stay out of a verdict.
import { chmodSync, existsSync, mkdirSync, mkdtempSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { type RunResult, run } from '@/exec.js'
import { tests } from '@/gate-tests.js'
import { isolatedGitEnvironment } from '@/git-isolation.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

const MANAGED: string = '.airness/test.gitconfig'
const REPOSITORY_VARIABLE: string = 'AIRNESS_REPOSITORY_ROOT'
const GLOBAL_VARIABLE: string = 'GIT_CONFIG_GLOBAL'
const NO_SYSTEM_VARIABLE: string = 'GIT_CONFIG_NOSYSTEM'
const FAILING_HOOK: string = '#!/bin/sh\nexit 1\n'
const HOOKS: readonly string[] = ['pre-commit', 'commit-msg', 'post-checkout', 'pre-merge-commit', 'post-merge']
const EXECUTABLE: number = 0o755
// A merge commit names itself and its two parents.
const MERGE_FIELDS: number = 3
// A member suite starts a Vitest of its own.
const SUITE_TIMEOUT_MS: number = 60_000

const git = (
  root: string,
  commandArguments: readonly string[],
  environment: Readonly<Record<string, string>> = {},
): string => {
  const result: RunResult = run('git', commandArguments, { cwd: root, env: environment })
  if (result.code !== 0) {
    throw new Error(result.output)
  }
  return result.stdout.trim()
}

const withDirectory = (use: (directory: string) => void): void => {
  const directory: string = realpathSync(mkdtempSync(path.join(tmpdir(), 'airness-git-isolation-')))
  try {
    use(directory)
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}

const withRepository = (use: (root: string) => void): void => {
  withDirectory((root: string): void => {
    git(root, ['init', '--quiet'])
    use(root)
  })
}

// Runs with one variable as given, restoring the value the run started with.
const withVariable = <T>(name: string, value: string | undefined, use: () => T): T => {
  const previous: string | undefined = process.env[name]
  if (value === undefined) {
    Reflect.deleteProperty(process.env, name)
  } else {
    process.env[name] = value
  }
  try {
    return use()
  } finally {
    if (previous === undefined) {
      Reflect.deleteProperty(process.env, name)
    } else {
      process.env[name] = previous
    }
  }
}

const commitFile = (root: string, file: string, message: string): void => {
  writeFileSync(path.join(root, file), `${file}\n`)
  git(root, ['add', '--all'])
  git(root, ['commit', '--quiet', '--message', message])
}

describe('the Git configuration a test reads', PROCESS_TIME, () => {
  it('points every Git this suite starts at the managed configuration and at no system one', () => {
    const managed: string = isolatedGitEnvironment(process.cwd())[GLOBAL_VARIABLE] ?? ''
    expect(process.env[GLOBAL_VARIABLE]).toBe(managed)
    expect(process.env[NO_SYSTEM_VARIABLE]).toBe('1')
    expect(existsSync(managed)).toBe(true)
  })

  it('commits, branches and merges past failing hooks the repository holds', () => {
    withRepository((root: string): void => {
      for (const hook of HOOKS) {
        writeFileSync(path.join(root, '.git', 'hooks', hook), FAILING_HOOK)
        chmodSync(path.join(root, '.git', 'hooks', hook), EXECUTABLE)
      }
      commitFile(root, 'base.txt', 'test(git): record the base')
      git(root, ['checkout', '--quiet', '-b', 'side'])
      commitFile(root, 'side.txt', 'test(git): record the side')
      git(root, ['checkout', '--quiet', '-'])
      commitFile(root, 'main.txt', 'test(git): record the main line')
      git(root, ['merge', '--quiet', '--no-ff', 'side', '--message', 'test(git): join the side'])
      expect(git(root, ['rev-list', '--parents', '--max-count=1', 'HEAD']).split(' ')).toHaveLength(MERGE_FIELDS)
    })
  })

  it('starts a new repository on main and records commits unsigned under the fixture identity', () => {
    withRepository((root: string): void => {
      commitFile(root, 'base.txt', 'test(git): record the base')
      expect(git(root, ['branch', '--show-current'])).toBe('main')
      expect(git(root, ['log', '--format=%G?|%ae', '--max-count=1'])).toBe('N|test@airness.invalid')
      expect(git(root, ['config', '--get', 'tag.gpgSign'])).toBe('false')
    })
  })

  it('reads neither the ignore file nor the attribute file the machine keeps for its user', () => {
    withDirectory((home: string): void => {
      mkdirSync(path.join(home, 'git'))
      writeFileSync(path.join(home, 'git', 'ignore'), 'secret.txt\n')
      writeFileSync(path.join(home, 'git', 'attributes'), '*.txt diff=hostile\n')
      withRepository((root: string): void => {
        writeFileSync(path.join(root, 'secret.txt'), 'secret\n')
        const machine: Readonly<Record<string, string>> = {
          XDG_CONFIG_HOME: home,
        }
        expect(git(root, ['status', '--porcelain', '--untracked-files=all'], machine)).toBe('?? secret.txt')
        expect(git(root, ['check-attr', 'diff', '--', 'secret.txt'], machine)).toBe('secret.txt: diff: unspecified')
      })
    })
  })

  it('names the managed file of the repository the build names', () => {
    withVariable(REPOSITORY_VARIABLE, '/repository', (): void => {
      expect(isolatedGitEnvironment(process.cwd())).toEqual({
        [NO_SYSTEM_VARIABLE]: '1',
        [GLOBAL_VARIABLE]: path.join('/repository', MANAGED),
      })
    })
  })

  it('names the managed file at the top of the repository a directory lies in', () => {
    withRepository((root: string): void => {
      mkdirSync(path.join(root, 'member'))
      withVariable(REPOSITORY_VARIABLE, undefined, (): void => {
        expect(isolatedGitEnvironment(path.join(root, 'member'))[GLOBAL_VARIABLE]).toBe(path.join(root, MANAGED))
      })
    })
  })

  it('names the file of a directory outside any repository, which keeps the machine files out all the same', () => {
    withDirectory((directory: string): void => {
      withVariable(REPOSITORY_VARIABLE, undefined, (): void => {
        expect(isolatedGitEnvironment(directory)[GLOBAL_VARIABLE]).toBe(path.join(directory, MANAGED))
      })
    })
  })
})

const MEMBER: Readonly<Record<string, string>> = {
  'package.json': `${JSON.stringify({ name: 'git-isolation', private: true, type: 'module' })}\n`,
  'vitest.config.mts': "import config from '@airness/node/vitest'\n\nexport default config\n",
  'src/total.ts': 'export const total = (left: number, right: number): number => left + right\n',
}

// The member's own spec, which judges the variables its run reads against the ones the harness names.
const memberSpec = (expected: string): string =>
  [
    "import { expect, it } from 'vitest'",
    "import { total } from '../src/total.js'",
    '',
    "it('adds two amounts under the managed Git configuration', () => {",
    '  expect(total(1, 2)).toBe(3)',
    `  expect(process.env['${GLOBAL_VARIABLE}']).toBe(${JSON.stringify(expected)})`,
    `  expect(process.env['${NO_SYSTEM_VARIABLE}']).toBe('1')`,
    '})',
    '',
  ].join('\n')

describe('the tests gate', PROCESS_TIME, () => {
  it(
    'runs a member suite under the managed configuration though the invoking environment names another',
    () => {
      const root: string = temporaryProject(MEMBER)
      try {
        mkdirSync(path.join(root, 'tests'))
        writeFileSync(
          path.join(root, 'tests', 'total.spec.ts'),
          memberSpec(isolatedGitEnvironment(root)[GLOBAL_VARIABLE] ?? ''),
        )
        withVariable(GLOBAL_VARIABLE, path.join(tmpdir(), 'hostile.gitconfig'), (): void => {
          withVariable(NO_SYSTEM_VARIABLE, undefined, (): void => {
            expect(tests(memberAt(root)).findings).toEqual([])
          })
        })
      } finally {
        removeProject(root)
      }
    },
    SUITE_TIMEOUT_MS,
  )
})
