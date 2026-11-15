// The packages a built image holds, listed while the image still exists and written beside the gate's result,
// where the closing step reads them: it looks every one up in the vulnerability database and holds the system
// packages to the image's base, whose newer release is the repair for one it ships vulnerable. The image gate
// removes every image it builds, so this is the only moment the image can be read.
//
// The list is Trivy's CycloneDX document, made by the scanner image Airness pins with no network and no
// vulnerability database: it names packages and judges nothing. Java archives are left out, because the ones an
// image ships are the Maven build's output, which the dependency scan already judges.
import path from 'node:path'
import type { RunResult } from './exec.js'
import type { GateContext } from './gate-result.js'
import { type ImageMachine, TRIVY_IMAGE_VARIABLE } from './image-machine.js'
import { asRecord, parseJsonc } from './json-shapes.js'
import { type Member, writeJson } from './project.js'

const EVIDENCE_CONTRACT: number = 1
const TAIL_LINES: number = 30
const UNSAFE_NAME: RegExp = /[^A-Z0-9.]+/gi

/** One run of the image gate over one member: the machine it builds on, the member, and where it writes evidence. */
export interface ImageRun {
  readonly machine: ImageMachine
  readonly member: Member
  readonly context: GateContext
}

/** What listing the packages of one image came back with. */
export interface CatalogueOutcome {
  readonly findings: readonly string[]
  readonly evidence: readonly string[]
}

const NOTHING: CatalogueOutcome = { findings: [], evidence: [] }

/**
 * The end of what a command printed, which names the step that failed and why.
 * @param output what the command printed.
 * @returns its last non-blank lines, each marked as quoted output.
 */
export const tailOf = (output: string): readonly string[] =>
  output
    .split('\n')
    .map((line: string): string => line.trimEnd())
    .filter((line: string): boolean => line.length > 0)
    .slice(-TAIL_LINES)
    .map((line: string): string => `| ${line}`)

/**
 * The Dockerfile as the closing step reads it: relative to the node project's directory, in POSIX form.
 * @param member the member that holds the Dockerfile.
 * @param dockerfile the Dockerfile, relative to the member.
 * @returns the path from the node project's directory.
 */
const projectDockerfile = (member: Member, dockerfile: string): string =>
  path.posix.join(member.path.split(path.sep).join(path.posix.sep), dockerfile)

/**
 * The file the packages of one Dockerfile's image are written to, unique among a node project's members.
 * @param member the member that holds the Dockerfile.
 * @param dockerfile the Dockerfile, relative to the member.
 * @returns a plain file name, such as `image-web-Dockerfile.json`.
 */
const imageEvidenceName = (member: Member, dockerfile: string): string =>
  `image-${projectDockerfile(member, dockerfile).replaceAll(UNSAFE_NAME, '-')}.json`

const isCycloneDx = (text: string): boolean => {
  try {
    return asRecord(parseJsonc(text).value)['bomFormat'] === 'CycloneDX'
  } catch {
    return false
  }
}

/**
 * List the packages of a built image and write them beside the gate's result.
 * @param run the gate's run: the machine the image was built on, the member, and the gate's context, without whose
 * result file nothing is listed, since nothing would read it.
 * @param dockerfile the Dockerfile, relative to the member.
 * @param tag the image the Dockerfile built.
 * @returns the findings of a list that could not be made, and the evidence file written.
 */
export const catalogue = (run: ImageRun, dockerfile: string, tag: string): CatalogueOutcome => {
  const { machine, member, context } = run
  if (context.evidenceDirectory === undefined) {
    return NOTHING
  }
  const scanner: string | undefined = machine.scannerImage()
  if (scanner === undefined || scanner.length === 0) {
    return {
      findings: [
        `${dockerfile}: the packages of the image cannot be listed, because ${TRIVY_IMAGE_VARIABLE} names no ` +
          'scanner image; run the gate through `mvn verify -Pextended`, which passes the image Airness pins',
      ],
      evidence: [],
    }
  }
  const listed: RunResult = machine.catalogue(tag, scanner)
  if (listed.code !== 0 || !isCycloneDx(listed.stdout)) {
    return {
      findings: [
        `${dockerfile}: the packages of the image could not be listed (exit code ${String(listed.code)}):`,
        ...tailOf(listed.output),
      ],
      evidence: [],
    }
  }
  const name: string = imageEvidenceName(member, dockerfile)
  writeJson(path.join(context.evidenceDirectory, name), {
    contract: EVIDENCE_CONTRACT,
    dockerfile: projectDockerfile(member, dockerfile),
    sbom: parseJsonc(listed.stdout).value,
  })
  return { findings: [], evidence: [name] }
}
