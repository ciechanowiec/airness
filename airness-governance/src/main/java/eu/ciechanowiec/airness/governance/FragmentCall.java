package eu.ciechanowiec.airness.governance;

/**
 * One call on a fragment, as the expression that made it named its parts.
 *
 * <p>Either name may be left out, and each absence means something the caller has to answer for. A
 * call that names no template reaches the document it was written in, and a call that names no
 * fragment reaches a whole template rather than a declaration inside one.
 *
 * @param template  the template the call reaches, and nothing when it reaches its own document
 * @param fragment  the fragment the call reaches, and nothing when it reaches a whole template
 * @param arguments how many arguments the call hands over
 */
record FragmentCall(String template, String fragment, int arguments) {

    /**
     * Whether the call reaches the document it was written in rather than another one.
     *
     * @return whether the call names no template
     */
    boolean local() {
        return this.template.isEmpty();
    }

    /**
     * Whether the call reaches a whole template rather than a fragment declared in one.
     *
     * @return whether the call names no fragment
     */
    boolean whole() {
        return this.fragment.isEmpty();
    }
}
