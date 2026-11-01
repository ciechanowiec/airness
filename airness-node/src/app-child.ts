// The harness-owned child of an application wrapper. Record the actual tool's status independently of
// the wrapper's exit code, so a wrapper that forgets to run or propagate the command cannot create a pass.
//
// A stop signal is passed on to the tool, and this process waits for it rather than ending at once: ended
// first, it would leave the test runner, its workers and the application server to the system, and the
// wrapper above it would remove the run's services while they still used them.
import { type ChildProcess, spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const ARGUMENT_OFFSET: number = 2
const STOP_SIGNALS: readonly NodeJS.Signals[] = ['SIGINT', 'SIGTERM', 'SIGHUP']
const [receipt, ...argumentsList]: readonly (string | undefined)[] = process.argv.slice(ARGUMENT_OFFSET)
if (receipt === undefined || argumentsList.length === 0) {
  throw new Error('The application child requires a receipt and a Node invocation')
}
const record = (code: number): void => {
  writeFileSync(receipt, JSON.stringify({ code }))
  process.exitCode = code
}
const tool: ChildProcess = spawn(
  process.execPath,
  argumentsList.filter((argument): argument is string => argument !== undefined),
  {
    stdio: 'inherit',
    env: process.env,
  },
)
const forward = (signal: NodeJS.Signals): void => {
  tool.kill(signal)
}
for (const signal of STOP_SIGNALS) {
  process.on(signal, forward)
}
tool.on('error', (): void => {
  record(1)
})
tool.on('close', (code: number | null): void => {
  for (const signal of STOP_SIGNALS) {
    process.off(signal, forward)
  }
  record(code ?? 1)
})
