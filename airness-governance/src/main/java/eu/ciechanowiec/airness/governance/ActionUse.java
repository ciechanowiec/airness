package eu.ciechanowiec.airness.governance;

/**
 * What the value of one {@code uses} key names, as far as the Node runtime rule is concerned: an action in
 * another repository, whose metadata says which runtime runs it, something the rule passes over, or a value
 * whose action cannot be read at all. {@link ActionUses#read(String)} tells them apart.
 */
sealed interface ActionUse permits ActionReference, ActionUse.Skipped, ActionUse.Unjudgeable {

    /**
     * A value the rule passes over: a local action, a Docker action or a reusable workflow.
     */
    record Skipped() implements ActionUse {
    }

    /**
     * A value whose action cannot be read.
     *
     * @param reason      why it cannot
     * @param replacement what to write instead
     */
    record Unjudgeable(String reason, String replacement) implements ActionUse {
    }
}
