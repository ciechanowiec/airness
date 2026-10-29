#!/usr/bin/env node
// The `ploaness` executable: the command line, started in the node project it judges.
import { runCommandLine } from './command-line.js'

// argv begins with the node binary and the script path; the command follows them.
const ARGV_COMMAND_OFFSET: number = 2

// Setting the exit code is how Node reports a verdict while still flushing stdout.
process.exitCode = await runCommandLine(process.argv.slice(ARGV_COMMAND_OFFSET), process.cwd())
