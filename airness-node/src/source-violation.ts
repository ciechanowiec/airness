/** A language-level defect found in project source, at the line that carries it. */
export interface SourceViolation {
  readonly line: number
  readonly rule: string
  readonly reason: string
}
