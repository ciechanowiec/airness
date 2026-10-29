// The findings of a Payload tool that failed, such as the Payload CLI or the configuration probe. Their output can
// be a stack trace through bundled code, which prints whole minified modules on one line, so the findings keep
// what explains the failure and stay readable in a result file.

// Long enough to explain a failure, short enough that a result file stays readable.
const MAX_FINDING_LINES: number = 40
const MAX_FINDING_LENGTH: number = 400

const shortened = (line: string): string =>
  line.length > MAX_FINDING_LENGTH ? `${line.slice(0, MAX_FINDING_LENGTH)} [cut]` : line

/**
 * The lines of a tool's output, as findings: the non-blank ones, the last of them when there are many, each cut
 * to a readable length.
 * @param output what the tool printed.
 * @returns at most the last forty non-blank lines.
 */
export const asFindings = (output: string): readonly string[] => {
  const lines: readonly string[] = output.split('\n').filter((line: string): boolean => line.trim().length > 0)
  return lines.length === 0
    ? ['the tool printed nothing to explain its failure']
    : lines.slice(-MAX_FINDING_LINES).map((line: string): string => shortened(line))
}
