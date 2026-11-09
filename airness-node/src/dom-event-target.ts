// happy-dom, which runs the component suites, gives each window an `EventTarget` class of its own that extends its
// implementation, while every node it creates extends the implementation itself. A node is then no instance of the
// global `EventTarget`, as it is in a browser, and a library that tells an element from a selector or a list by
// `instanceof EventTarget` - Motion's viewport tracking does - takes the element for neither and watches nothing.

/** A class, as `instanceof` takes one. */
type Constructor = abstract new (...parameters: never[]) => unknown

// A predicate rather than an assertion, so the narrowing is checked: `typeof` alone yields a function with no
// construct signature.
const isConstructor = (value: unknown): value is Constructor => typeof value === 'function'

/** Where the DOM's globals live: the test worker's `globalThis`, or a window in a spec. */
export interface DomScope {
  readonly EventTarget?: unknown
  readonly document?: unknown
}

/**
 * Make every node of the scope's document an instance of its global `EventTarget`, when the DOM breaks that.
 * The global class then recognises whatever its parent recognises, which is every node and every instance of
 * itself. A DOM whose nodes are already instances, and a scope with no document, are left as they are.
 * @param scope the globals of the DOM the suite runs in.
 */
export const unifyEventTarget = (scope: DomScope): void => {
  const { EventTarget: global, document } = scope
  if (document === undefined || !isConstructor(global) || document instanceof global) {
    return
  }
  const implementation: unknown = Reflect.getPrototypeOf(global)
  if (isConstructor(implementation) && document instanceof implementation) {
    Reflect.defineProperty(global, Symbol.hasInstance, {
      configurable: true,
      value: (candidate: unknown): boolean => candidate instanceof implementation,
    })
  }
}
