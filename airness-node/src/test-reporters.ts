/** Reporter names and options supported by the harness's pinned Vitest. */
type ReporterEntry = 'default' | ['github-actions', { jobSummary: { enabled: false } }]

/** Keep failure annotations on GitHub Actions without replacing the verification job's summary. */
export const testReporters = (environment: Readonly<Record<string, string | undefined>>): readonly ReporterEntry[] =>
  environment['GITHUB_ACTIONS'] === 'true'
    ? ['default', ['github-actions', { jobSummary: { enabled: false } }]]
    : ['default']
