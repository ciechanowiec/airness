// The harness-owned child of an application wrapper. Record the actual tool's status independently of
// the wrapper's exit code, so a wrapper that forgets to run or propagate the command cannot create a pass.
import { type SpawnSyncReturns, spawnSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const ARGUMENT_OFFSET: number = 2
const [receipt, ...argumentsList]: readonly (string | undefined)[] = process.argv.slice(ARGUMENT_OFFSET)
if (receipt === undefined || argumentsList.length === 0) {
  throw new Error('The application child requires a receipt and a Node invocation')
}
const outcome: SpawnSyncReturns<Buffer> = spawnSync(
  process.execPath,
  argumentsList.filter((argument): argument is string => argument !== undefined),
  {
    stdio: 'inherit',
    env: process.env,
  },
)
const code: number = outcome.status ?? 1
writeFileSync(receipt, JSON.stringify({ code }))
process.exitCode = code
