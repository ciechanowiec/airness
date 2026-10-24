import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

// The specs import the sources through the `@/` alias the source rules ask for, rather than climbing out
// of `tests/` with a parent-relative import.
const config: ReturnType<typeof defineConfig> = defineConfig({
  resolve: { alias: { '@': fileURLToPath(new URL('src', import.meta.url)) } },
  test: { include: ['tests/**/*.spec.ts'] },
})

export default config
