// The boot's own process, which the image gate starts under the member's test wrapper: its arguments are the file
// it writes its verdict to and the request, as JSON. The verdict file, not the exit code alone, is what the gate
// reads, so a wrapper that swallows the boot's failure or never runs it cannot pass for one that booted.
import { writeFileSync } from 'node:fs'
import { type BootRequest, type BootVerdict, bootImage, parseBootRequest } from './image-boot.js'
import { SYSTEM_BOOT_RUNTIME } from './image-machine.js'

const ARGUMENT_OFFSET: number = 2
const [verdictFile, requestText]: readonly (string | undefined)[] = process.argv.slice(ARGUMENT_OFFSET)
const request: BootRequest | undefined = parseBootRequest(requestText)
if (verdictFile === undefined || request === undefined) {
  throw new Error('The image boot requires the file it writes its verdict to and the request, as JSON')
}
const verdict: BootVerdict = await bootImage(request, SYSTEM_BOOT_RUNTIME)
writeFileSync(verdictFile, JSON.stringify(verdict))
for (const finding of verdict.findings) {
  console.error(finding)
}
process.exitCode = verdict.ok ? 0 : 1
