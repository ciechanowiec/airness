// Exercise interruption and orphan cleanup with real processes and the harness's pinned browser containers.
import { type ChildProcessByStdio, spawn } from 'node:child_process'
import { createInterface } from 'node:readline'
import type { Readable } from 'node:stream'
import { type Browser, type PinnedServer, startPinnedServer } from '#browser/pinned-server'
import { type RunResult, run } from '#harness/exec'

const READY: string = 'browser-server-ready'
const ARGUMENT_OFFSET: number = 2
const START_TIMEOUT: number = 120_000
const HEARTBEAT: number = 1000
// The label every container the harness starts records its owner's process in, as docker filters on it.
const OWNER_PID_LABEL: string = 'airness.owner.pid'

type Child = ChildProcessByStdio<null, Readable, Readable>

const start = async (): Promise<Child> =>
  await new Promise<Child>((resolve, reject): void => {
    const child: Child = spawn(process.execPath, [import.meta.filename, 'child'], { stdio: ['ignore', 'pipe', 'pipe'] })
    const timer: ReturnType<typeof setTimeout> = setTimeout((): void => {
      child.kill('SIGTERM')
      reject(new Error('The browser child did not become ready'))
    }, START_TIMEOUT)
    createInterface({ input: child.stdout }).on('line', (line: string): void => {
      if (line !== READY) {
        return
      }
      clearTimeout(timer)
      resolve(child)
    })
    child.once('error', (error: Error): void => {
      clearTimeout(timer)
      reject(error)
    })
    child.once('exit', (code: number | null): void => {
      clearTimeout(timer)
      reject(new Error(`The browser child exited before readiness: ${String(code)}`))
    })
  })

const stop = async (child: Child, signal: NodeJS.Signals): Promise<void> => {
  await new Promise<void>((resolve): void => {
    child.once('exit', (): void => {
      resolve()
    })
    child.kill(signal)
  })
}

const hasServer = (child: Child): boolean => {
  const result: RunResult = run(
    'docker',
    ['ps', '--all', '--quiet', `--filter=label=${OWNER_PID_LABEL}=${String(child.pid)}`],
    { cwd: process.cwd() },
  )
  if (result.code !== 0) {
    throw new Error(result.output)
  }
  return result.stdout.trim() !== ''
}

const verifyLifecycle = async (): Promise<void> => {
  const first: Child = await start()
  const second: Child = await start()
  try {
    await stop(first, 'SIGTERM')
    if (hasServer(first) || !hasServer(second)) {
      throw new Error('Graceful interruption must remove its own browser server and preserve the other live run')
    }
    await stop(second, 'SIGKILL')
    const third: Child = await start()
    try {
      if (hasServer(second) || !hasServer(third)) {
        throw new Error('A new run must remove an orphan while preserving its own server')
      }
    } finally {
      await stop(third, 'SIGTERM')
    }
    console.info('interruption and orphan cleanup preserve other live runs')
  } finally {
    first.kill('SIGTERM')
    second.kill('SIGTERM')
  }
}

if (process.argv[ARGUMENT_OFFSET] === 'child') {
  const server: PinnedServer = await startPinnedServer('lifecycle')
  const browser: Browser = await server.connect('chromium')
  await browser.close()
  console.info(READY)
  setInterval((): void => {
    process.stdout.write('')
  }, HEARTBEAT)
} else {
  await verifyLifecycle()
}
