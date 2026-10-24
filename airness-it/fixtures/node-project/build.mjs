// Writes the page a node-run build step ships, naming the Node that built it.
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const [output] = process.argv.slice(2);
mkdirSync(output, { recursive: true });
writeFileSync(join(output, 'index.html'), `<!doctype html>\n<p>built on Node ${process.version}</p>\n`);
