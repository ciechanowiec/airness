// knip's own process, which the dead-code gate starts from the member's directory with knip's arguments, naming
// knip's entry point in AIRNESS_KNIP_ENTRY. knip creates the loader it reads configuration files through as soon as
// that module is first imported, for the working directory as though it were a file, so the loader reads the
// tsconfig.json nearest the directory above it: from the member itself, that is the enclosing project's, whose
// extends need not be installed yet and whose paths are not the member's. The loader is therefore created from
// inside the member's node_modules, where it reads the member's own tsconfig.json, and knip then runs from the
// member, as the configuration files it loads resolve their own relative paths against the working directory.
// knip reads its arguments after this script's, exactly as it reads them after its own entry point.
import { existsSync } from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const entry: string = process.env['AIRNESS_KNIP_ENTRY'] ?? ''
if (entry.length === 0) {
  throw new Error("The knip launcher requires knip's entry point in AIRNESS_KNIP_ENTRY")
}
const member: string = process.cwd()
const installed: string = path.join(member, 'node_modules')
process.chdir(existsSync(installed) ? installed : member)
await import(pathToFileURL(path.join(path.dirname(entry), '..', 'dist', 'util', 'jiti.js')).href)
process.chdir(member)
await import(pathToFileURL(entry).href)
