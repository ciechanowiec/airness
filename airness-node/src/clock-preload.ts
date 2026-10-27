// The application's clock during verification: started at one fixed instant, then advancing normally.
//
// Loaded through `NODE_OPTIONS=--import`, so it reaches every process of the verification tree - the
// build, the seeding command, every application server and every worker they start - before any of
// their own code runs. A page prerendered by the build and the same page rendered by the server then
// read the same date on every machine and every day, while sessions, cookie expiry and revalidation keep
// working because time still passes.
//
// Self-contained on purpose. It is evaluated at the start of every process in the tree, and loading the
// governance package for one subtraction would cost each of them that package's whole import graph.
// The `it/` clock contracts hold the variable name to the one governance declares: the CLI sets that
// variable, and a mismatch leaves the fixture's pages at today's year.

/** The variable naming the instant, `CLOCK_ENVIRONMENT_VARIABLE` in the pure policy modules. */
const CLOCK_VARIABLE: string = 'AIRNESS_CLOCK'

// Set once the clock is shifted. A process can reach this module twice - through `NODE_OPTIONS` and
// through the Playwright config, which loads it for the test workers `NODE_OPTIONS` does not reach -
// and the second load must not restart the clock at the instant.
const SHIFTED: symbol = Symbol.for('airness.clock.shifted')

const declared: string | undefined = process.env[CLOCK_VARIABLE]
const instant: number = declared === undefined ? Number.NaN : Date.parse(declared)

if (!(Number.isNaN(instant) || Object.hasOwn(globalThis, SHIFTED))) {
  // eslint-disable-next-line functional/immutable-data -- the preload marks the process global so a worker cannot shift Date twice
  Object.defineProperty(globalThis, SHIFTED, { value: true })
  const RealDate: DateConstructor = Date
  const startedAt: number = RealDate.now()
  const now = (): number => instant + (RealDate.now() - startedAt)
  // A proxy rather than a subclass, so `Date()` called as a function still answers a string and
  // `instanceof Date` still holds for every date created before or after the shift.
  const shifted: DateConstructor = new Proxy(RealDate, {
    construct: (target: DateConstructor, parameters: readonly unknown[], newTarget: NewableFunction): object => {
      const created: unknown = Reflect.construct(target, parameters.length === 0 ? [now()] : parameters, newTarget)
      if (typeof created !== 'object' || created === null) {
        throw new TypeError('Date construction returned no object')
      }
      return created
    },
    apply: (): string => new RealDate(now()).toString(),
    get: (target: DateConstructor, property: string | symbol, receiver: unknown): unknown =>
      property === 'now' ? now : Reflect.get(target, property, receiver),
  })
  // Redefined rather than assigned, keeping the global's own writable and non-enumerable attributes.
  // eslint-disable-next-line functional/immutable-data -- installing the verification clock replaces the process Date API before application code runs
  Object.defineProperty(globalThis, 'Date', { value: shifted })
}

export {}
