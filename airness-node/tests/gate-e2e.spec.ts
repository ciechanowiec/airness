import { createServer, type Server } from 'node:net'
import { describe, expect, it } from 'vitest'
import type { GateResult } from '@/exec.js'
import { BROWSER_INSTALL_REPAIR, endToEnd, judgeRun, serveProblem } from '@/gate-e2e.js'
import type { Member } from '@/project.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

// Only the decisions the gate takes before it starts a browser - whether it applies, whether the member
// says how it is served, whether its endpoint is free - and how it reads a run. Playwright itself is exercised
// by the Maven integration fixtures.

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
      expect(serveProblem(memberAt(root))).toContain('"airness": { "serve": "<script>" }')
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
      expect(result.findings.join('\n')).toContain('"start"')
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

describe('an occupied browser endpoint', PROCESS_TIME, () => {
  it('refuses an already-running auxiliary server before starting the suite', async () => {
    const server: Server = await listening()
    try {
      const primary: Server = await listening()
      const primaryUrl: string = `http://127.0.0.1:${portOf(primary)}`
      await new Promise<void>((resolve): void => {
        primary.close((): void => {
          resolve()
        })
      })
      const manifest: unknown = {
        scripts: { serve: 'node server.js' },
        airness: {
          serve: 'serve',
          serverUrl: primaryUrl,
          auxiliaryServers: [{ command: 'node cms.js', url: `http://127.0.0.1:${portOf(server)}` }],
        },
      }
      const result: GateResult = await endToEnd(memberAt('/project', manifest))
      expect(result.ok).toBe(false)
      expect(result.findings.join('\n')).toContain(`:${portOf(server)}`)
    } finally {
      server.close()
    }
  })
  it('is refused before Playwright starts', async () => {
    const server: Server = await listening()
    try {
      const manifest: unknown = {
        scripts: { serve: 'vite preview' },
        airness: { serve: 'serve', serverUrl: `http://127.0.0.1:${portOf(server)}` },
      }
      const result: GateResult = await endToEnd(memberAt('/project', manifest))
      expect(result.ok).toBe(false)
      expect(result.findings.join('\n')).toContain('is in use')
    } finally {
      server.close()
    }
  })
})

describe('the Playwright verdict', PROCESS_TIME, () => {
  it('passes a run that exits cleanly', () => {
    expect(judgeRun({ code: 0, output: '2 passed', stdout: '2 passed' }).ok).toBe(true)
  })

  it('names the browser install when Playwright found no browser', () => {
    const output: string = "browserType.launch: Executable doesn't exist at /cache/chromium-1243/chrome"
    expect(judgeRun({ code: 1, output, stdout: '' }).findings[0]).toBe(BROWSER_INSTALL_REPAIR)
  })

  it('reports any other failure as the tool printed it', () => {
    const result: GateResult = judgeRun({ code: 1, output: '1 failed', stdout: '' })
    expect(result.findings).toEqual(['1 failed'])
  })
})

describe('serving a browser application', PROCESS_TIME, () => {
  it('needs no serve script for a Next.js application', () => {
    const member: Member = { ...memberAt('/project'), hasOwnRuntime: true }
    expect(serveProblem(member)).toBeUndefined()
  })

  it('accepts a serve script the manifest declares', () => {
    const manifest: unknown = { scripts: { start: 'vite preview' }, airness: { serve: 'start' } }
    expect(serveProblem(memberAt('/project', manifest))).toBeUndefined()
  })

  it('still judges a serve script a Next.js application declares', () => {
    const member: Member = { ...memberAt('/project', { airness: { serve: 'missing' } }), hasOwnRuntime: true }
    expect(serveProblem(member)).toContain('"missing"')
  })
})
