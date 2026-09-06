package eu.ciechanowiec.airness.spring;

/**
 * The bean the guards of these fixtures delegate their decision to. It decides nothing, because no
 * expression here is ever evaluated. What is read of it is which methods it answers to, which is what a
 * guard naming one of them has to be checked against.
 */
final class Clearance {

    private final String library;

    Clearance() {
        this.library = Clearance.class.getSimpleName();
    }

    /**
     * Answers whether the caller reaches the thing under the given reference, which is the method the
     * guards of these fixtures call.
     *
     * @param reference what the caller asked for
     * @return whether they may
     */
    boolean granted(String reference) {
        return this.library.equals(reference);
    }

    /**
     * Answers whether the library is open to everybody, which is what a guard reading a property of
     * this bean rather than calling a method of it asks for.
     *
     * @return whether it is
     */
    boolean isOpen() {
        return this.library.isEmpty();
    }
}
