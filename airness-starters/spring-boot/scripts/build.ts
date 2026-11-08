import { fileURLToPath } from 'node:url'
import { buildAssets } from '@scripts/assets'

await buildAssets(fileURLToPath(new URL('../target/frontend', import.meta.url)))
