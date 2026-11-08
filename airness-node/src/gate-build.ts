import { runApp } from './app-process.js'
import { clockedEnvironment } from './clock-environment.js'
// The Next.js facet's production build, which the bundle gate then measures and the e2e gate serves. It runs
// the member's own `next`, so the build is of the framework version the member locked, and records the build
// it produced, so the later gates judge this build of this tree and no other. Next writes its output to the
// member's ignored build directory, so the working tree stays as it was.
import { fromRun } from './exec.js'
import { isNextApp } from './facets.js'
import { failed, type GateResult, notApplicable } from './gate-result.js'
import { memberBin } from './member-tools.js'
import { buildDirectoryOf, forgetBuild, NEXT_ENVIRONMENT, stampBuild } from './next-build.js'
import type { Member } from './project.js'
import { withPretest } from './test-invocation.js'

const buildApp = (member: Member): GateResult => {
  const next: string | undefined = memberBin(member, 'next')
  if (next === undefined) {
    return failed('next could not be resolved from the member', [
      'the member declares next but resolves no installed copy; airness:node-dependencies installs it',
    ])
  }
  const built: GateResult = fromRun(
    runApp(member, [next, 'build'], {
      cwd: member.root,
      env: {
        ...NEXT_ENVIRONMENT,
        ...clockedEnvironment(member, '--no-deprecation'),
      },
    }),
    'the production build succeeds',
    'the production build failed',
  )
  if (!built.ok) {
    return built
  }
  const directory: { readonly directory: string } | { readonly problem: string } = buildDirectoryOf(member)
  if ('problem' in directory) {
    return failed('the build directory of the Next.js application could not be read', [directory.problem])
  }
  const problem: string | undefined = stampBuild(member, directory.directory)
  return problem === undefined ? built : failed('the production build cannot be recorded', [problem])
}

/** Produce the production build of a Next.js application, and record it. */
export const build = (member: Member): GateResult => {
  if (!isNextApp(member)) {
    return notApplicable('the member is no Next.js application, so the facet has nothing to build')
  }
  forgetBuild(member)
  return withPretest(member, (): GateResult => buildApp(member))
}
