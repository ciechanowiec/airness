#!/usr/bin/env node
// A stand-in for an @airness/node gate: it writes a result in the node-gate contract, naming the Node it ran
// on, so a build can prove which Node ran its gates. A planted-finding.txt in the project makes it fail.
import { existsSync, writeFileSync } from 'node:fs';

const [command, gate, flag, result] = process.argv.slice(2);
if (command !== 'gate' || flag !== '--result' || result === undefined) {
  console.error('usage: airness-fake-gate gate <id> --result <file>');
  process.exit(2);
}
const planted = existsSync('planted-finding.txt');
const outcome = {
  contract: 1,
  gate,
  title: 'fake gate',
  status: planted ? 'failed' : 'passed',
  summary: `ran on Node ${process.version}`,
  findings: planted ? ['planted-finding.txt is planted'] : [],
  evidence: [],
};
writeFileSync(result, `${JSON.stringify(outcome)}\n`);
process.exitCode = planted ? 1 : 0;
