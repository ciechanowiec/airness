// The guard's own process, which a services wrapper starts detached, in a session of its own and writing
// nowhere, so that neither the terminal's Ctrl-C, nor the end of the group the wrapper runs in, nor a closed
// output pipe ends it with the wrapper. Its arguments are the wrapper's process number and the run's prefix.
import { run } from './exec.js'
import { guardRun, isRunning } from './services-guard.js'

const ARGUMENT_OFFSET: number = 2
const [pidArgument = '', prefix = '']: readonly (string | undefined)[] = process.argv.slice(ARGUMENT_OFFSET)
const pid: number = Number(pidArgument)
if (!Number.isSafeInteger(pid) || pid <= 0 || prefix.length === 0) {
  throw new Error('The services guard requires the process number of the wrapper it watches and the run prefix')
}
await guardRun(
  {
    isAlive: (): boolean => isRunning(pid),
    docker: (commandArguments: readonly string[]) => run('docker', commandArguments, { cwd: process.cwd() }),
    pause: async (milliseconds: number): Promise<void> => {
      await new Promise<void>((resolve): void => {
        setTimeout(resolve, milliseconds)
      })
    },
  },
  prefix,
)
