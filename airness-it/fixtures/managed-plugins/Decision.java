package com.example;

/**
 * A branch whose two outcomes are covered by different test phases.
 */
public final class Decision {

    /**
     * Reports whether the decision is enabled.
     *
     * @param enabled whether to select the enabled outcome
     * @return one for enabled and zero otherwise
     */
    public int value(boolean enabled) {
        return enabled ? 1 : 0;
    }
}
