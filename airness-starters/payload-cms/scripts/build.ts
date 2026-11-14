// Builds the standalone server the Dockerfile copies into the image, as `mvn clean verify` runs it.
//
// `next build` imports every route module while it collects page data, and each one imports src/payload.config.ts,
// which validates the server environment when it is imported and refuses to start without it. The build connects to
// nothing, so it is handed placeholders: they stay out of the build's output, and the image reads the real values
// when it starts. The application reads no variable the bundler replaces at build time, NEXT_PUBLIC_SERVER_URL
// included, which it reads from its environment at run time, so no value of a deployment reaches the image here.
import { execFileSync } from 'node:child_process'

const PLACEHOLDERS: Readonly<Record<string, string>> = {
  PAYLOAD_SECRET: 'placeholder-of-the-build',
  DATABASE_URL: 'postgres://127.0.0.1:5432/placeholder',
  NEXT_PUBLIC_SERVER_URL: 'http://localhost:3000',
  S3_ENDPOINT: 'http://127.0.0.1:9000',
  S3_BUCKET: 'placeholder',
  S3_ACCESS_KEY_ID: 'placeholder',
  S3_SECRET_ACCESS_KEY: 'placeholder',
}

execFileSync('next', ['build'], {
  env: {
    ...process.env,
    ...PLACEHOLDERS,
    NEXT_TELEMETRY_DISABLED: '1',
    NODE_OPTIONS: '--no-deprecation --max-old-space-size=8000',
  },
  stdio: 'inherit',
})
