package eu.ciechanowiec.airness.spring;

/**
 * A bean whose deciding method is narrower than public. The expression engine resolves a call against
 * public methods alone, so a guard calling this one fails on its first evaluation.
 */
final class Withheld {

    private final String library;

    Withheld() {
        this.library = Withheld.class.getSimpleName();
    }

    boolean granted(String reference) {
        return this.library.equals(reference);
    }
}
