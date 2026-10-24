// The client bundle budget: the sum of the gzipped client JavaScript of a Next.js production build, against a
// ceiling. A gzipped byte count of built JavaScript is a fixed function of the source and the pinned versions,
// so the number moves only when a change moves the bundle; it catches an accidental heavyweight dependency
// rather than a per-route delta. The ceiling is the harness's (settings.ts), and a project may only lower it.

/** One built client asset and its gzipped size. */
export interface BundleFile {
  readonly path: string
  readonly gzipBytes: number
}

/** The measured client JavaScript against the budget. */
export interface BundleReport {
  readonly totalGzipBytes: number
  readonly budgetBytes: number
  readonly isWithinBudget: boolean
  readonly fileCount: number
}

/**
 * Sum the gzipped sizes of the built assets and compare the total against the budget.
 * @param files the built client assets.
 * @param budgetBytes the ceiling.
 * @returns the report.
 */
export const evaluateBundle = (files: readonly BundleFile[], budgetBytes: number): BundleReport => {
  const totalGzipBytes: number = files.reduce((sum: number, file: BundleFile): number => sum + file.gzipBytes, 0)
  return { totalGzipBytes, budgetBytes, isWithinBudget: totalGzipBytes <= budgetBytes, fileCount: files.length }
}

/** Marks the line on which the build-directory probe reports the directory, among whatever else Next prints. */
export const BUILD_DIRECTORY_MARKER: string = 'airness-build-directory:'

/**
 * Read the one directory the probe reported. Missing, repeated or malformed evidence selects nothing.
 * @param text the probe's standard output.
 * @returns the reported directory, or undefined.
 */
export const parseBuildDirectory = (text: string): string | undefined => {
  const lines: readonly string[] = text
    .split('\n')
    .map((line: string): string => line.trim())
    .filter((line: string): boolean => line.startsWith(BUILD_DIRECTORY_MARKER))
  if (lines.length !== 1) {
    return undefined
  }
  try {
    const value: unknown = JSON.parse((lines[0] ?? '').slice(BUILD_DIRECTORY_MARKER.length))
    return typeof value === 'string' && value.trim().length > 0 ? value : undefined
  } catch {
    return undefined
  }
}
