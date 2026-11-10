import { createServer, type Server } from 'node:net'
import { describe, expect, it } from 'vitest'
import { playwrightImageOf } from '@/browser-run.js'
import { judgeRun } from '@/browser-verdict.js'
import { endToEnd } from '@/gate-e2e.js'
import type { GateResult } from '@/gate-result.js'
import { type Member, resolvedVersion } from '@/project.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

// Only the decisions the gate takes before it starts a browser - whether it applies, whether the member
// says how it is served, which addresses a run serves on - and how it reads a run. Playwright itself is exercised
// by the Maven integration fixtures. A member whose declared preparation fails stops the gate after every one of
// those decisions and before any browser starts, so the gate reaching it shows each decision let the run go on.

const withProject = async (
  files: Readonly<Record<string, string>>,
  body: (root: string) => Promise<void>,
): Promise<void> => {
  const root: string = temporaryProject(files)
  try {
    await body(root)
  } finally {
    removeProject(root)
  }
}

// The preparation that fails, and the summary the gate reports once it ran.
const FAILING_PRETEST: readonly string[] = [process.execPath, '-e', 'process.exit(3)']
const PRETEST_FAILED: string = 'the declared pretest command failed'

describe('the end-to-end gate', PROCESS_TIME, () => {
  it('does not apply to a member that is not a browser application', async () => {
    await withProject({ 'package.json': '{}' }, async (root: string) => {
      const result: GateResult = await endToEnd(memberAt(root))
      expect(result.isApplicable).toBe(false)
      expect(result.ok).toBe(true)
    })
  })

  it('takes a page at the root for no application, as decision 12 names none', async () => {
    await withProject({ 'index.html': '<!doctype html>' }, async (root: string) => {
      const result: GateResult = await endToEnd(memberAt(root, { devDependencies: { vite: '7.0.0' } }))
      expect(result.isApplicable).toBe(false)
    })
  })

  it('refuses a Next.js application with no build of this tree before serving it', async () => {
    await withProject({}, async (root: string) => {
      const result: GateResult = await endToEnd({
        ...memberAt(root, { dependencies: { next: '16.4.2' } }),
        hasOwnRuntime: true,
      })
      expect(result.summary).toBe('no current production build to serve')
    })
  })

  it('fails a declared serve that names no script in package.json', async () => {
    await withProject({}, async (root: string) => {
      const result: GateResult = await endToEnd(memberAt(root, { airness: { serve: 'start' } }))
      expect(result.ok).toBe(false)
      expect(result.summary).toBe('the browser application cannot be served')
      expect(result.findings.join('\n')).toContain('"start"')
    })
  })
})

describe('serving a browser application', PROCESS_TIME, () => {
  it('needs no serve script for a Next.js application', async () => {
    await withProject({}, async (root: string) => {
      const result: GateResult = await endToEnd({
        ...memberAt(root, { dependencies: { next: '16.4.2' } }),
        hasOwnRuntime: true,
      })
      expect(result.summary).toBe('no current production build to serve')
    })
  })

  it('accepts a serve script the manifest declares', async () => {
    await withProject({}, async (root: string) => {
      const result: GateResult = await endToEnd(
        memberAt(root, {
          scripts: { start: 'vite preview' },
          airness: { serve: 'start', pretest: FAILING_PRETEST },
        }),
      )
      expect(result.summary).toBe(PRETEST_FAILED)
    })
  })

  it('still judges a serve script a Next.js application declares', async () => {
    await withProject({}, async (root: string) => {
      const member: Member = {
        ...memberAt(root, { dependencies: { next: '16.4.2' }, airness: { serve: 'missing' } }),
        hasOwnRuntime: true,
      }
      const result: GateResult = await endToEnd(member)
      expect(result.summary).toBe('the browser application cannot be served')
      expect(result.findings.join('\n')).toContain('"missing"')
    })
  })

  it('asks a browser application it cannot start itself for the script that serves it', async () => {
    await withProject({}, async (root: string) => {
      // A manifest that names Next on a member not taken for a Next.js application: a browser application the
      // harness has no way to start without the script.
      const result: GateResult = await endToEnd(memberAt(root, { dependencies: { next: '16.4.2' } }))
      expect(result.summary).toBe('the browser application cannot be served')
      expect(result.findings.join('\n')).toContain('"airness": { "serve": "<script>" }')
    })
  })
})

// A listener on a free port of the loopback interface, standing in for a server left running.
const listening = async (): Promise<Server> =>
  await new Promise<Server>((resolve) => {
    const server: Server = createServer()
    server.listen(0, '127.0.0.1', () => {
      resolve(server)
    })
  })

const portOf = (server: Server): string => {
  const address: ReturnType<Server['address']> = server.address()
  return String(typeof address === 'object' && address !== null ? address.port : 0)
}

describe('the addresses a verification run serves on', PROCESS_TIME, () => {
  it('leaves a declared port that something already listens on to whatever holds it', async () => {
    const declared: Server = await listening()
    const auxiliary: Server = await listening()
    try {
      await withProject({}, async (root: string) => {
        const result: GateResult = await endToEnd(
          memberAt(root, {
            scripts: { serve: 'node server.js' },
            airness: {
              serve: 'serve',
              pretest: FAILING_PRETEST,
              serverUrl: `http://127.0.0.1:${portOf(declared)}`,
              auxiliaryServers: [{ command: 'node cms.js', url: `http://127.0.0.1:${portOf(auxiliary)}/cms` }],
            },
          }),
        )
        expect(result.summary).toBe(PRETEST_FAILED)
      })
    } finally {
      declared.close()
      auxiliary.close()
    }
  })

  it('serves the default origin on a port it chose, and goes on to the preparation once every address is free', async () => {
    await withProject({}, async (root: string) => {
      const result: GateResult = await endToEnd(
        memberAt(root, {
          scripts: { serve: 'vite preview' },
          airness: { serve: 'serve', pretest: FAILING_PRETEST },
        }),
      )
      expect(result.ok).toBe(false)
      expect(result.summary).toBe(PRETEST_FAILED)
    })
  })
})

describe('an address taken in the moment after it was chosen', PROCESS_TIME, () => {
  it('is refused before Playwright starts, naming the address, while the addresses the run chose stay free', async () => {
    const declared: Server = await listening()
    const holder: Server = await listening()
    try {
      const taken: string = `http://127.0.0.1:${portOf(holder)}/cms`
      const result: GateResult = await endToEnd(
        memberAt('/project', {
          scripts: { serve: 'vite preview' },
          airness: {
            serve: 'serve',
            serverUrl: `http://127.0.0.1:${portOf(declared)}`,
            auxiliaryServers: [{ command: 'node cms.js', url: taken, fixedPort: true }],
          },
        }),
      )
      expect(result.ok).toBe(false)
      expect(result.summary).toBe('the browser endpoint is not free')
      expect(result.findings).toHaveLength(1)
      expect(result.findings.join('\n')).toContain(`port ${portOf(holder)} at ${taken} is in use`)
    } finally {
      declared.close()
      holder.close()
    }
  })
})

describe('the Playwright verdict', PROCESS_TIME, () => {
  it('passes a run that exits cleanly', () => {
    expect(judgeRun({ code: 0, output: '2 passed', stdout: '2 passed' }).ok).toBe(true)
  })

  it('names the browser install when Playwright found no browser', () => {
    const output: string = "browserType.launch: Executable doesn't exist at /cache/chromium-1243/chrome"
    expect(judgeRun({ code: 1, output, stdout: '' }).findings).toEqual([
      'Playwright found no browser executable; run docker pull ' +
        `${playwrightImageOf(resolvedVersion('@playwright/test'))} before verification`,
      output,
    ])
  })

  it('reports any other failure as the tool printed it', () => {
    const result: GateResult = judgeRun({
      code: 1,
      output: '1 failed',
      stdout: '',
    })
    expect(result.findings).toEqual(['1 failed'])
  })
})
