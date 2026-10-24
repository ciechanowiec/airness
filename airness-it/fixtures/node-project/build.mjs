// Writes the page a node-run build step ships, naming the Node that built it.
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

// argv begins with the node binary and the script path.
const ARGUMENTS_START = 2
const [output] = process.argv.slice(ARGUMENTS_START)
mkdirSync(output, { recursive: true })
writeFileSync(path.join(output, 'index.html'), `<!doctype html>\n<p>built on Node ${process.version}</p>\n`)
