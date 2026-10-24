#!/usr/bin/env node
// A stand-in for an @airness/node gate: it writes a result in the node-gate contract, naming the Node it ran
// on, so a build can prove which Node ran its gates. A planted-finding.txt in the project makes it fail.
import { existsSync, writeFileSync } from 'node:fs'

// argv begins with the node binary and the script path.
const ARGUMENTS_START = 2
const [command, gate, flag, result] = process.argv.slice(ARGUMENTS_START)
const isInvoked = command === 'gate' && flag === '--result' && result !== undefined
const isPlanted = existsSync('planted-finding.txt')
if (isInvoked) {
  const outcome = {
    contract: 1,
    gate,
    title: 'fake gate',
    status: isPlanted ? 'failed' : 'passed',
    summary: `ran on Node ${process.version}`,
    findings: isPlanted ? ['planted-finding.txt is planted'] : [],
    evidence: [],
  }
  writeFileSync(result, `${JSON.stringify(outcome)}\n`)
} else {
  console.error('usage: airness-fake-gate gate <id> --result <file>')
}
process.exitCode = isInvoked && !isPlanted ? 0 : 1
