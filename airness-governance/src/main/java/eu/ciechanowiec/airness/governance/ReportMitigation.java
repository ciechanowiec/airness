package eu.ciechanowiec.airness.governance;

/**
 * The recorded account that covers an advisory: a mitigation the project or the harness recorded, or the harness's
 * account of a container image finding no release of the image can fix yet.
 *
 * @param reason  why the vulnerability can't be exploited in this project, or, for {@link Kind#AWAITING_FIX}, why
 *                the build accepts it for now
 * @param plan    what limits the risk now and what will remove the vulnerable package, or, for
 *                {@link Kind#AWAITING_FIX}, when the build fails on it
 * @param addedOn the date the account was recorded
 * @param kind    who answers for the account, and whether it is a mitigation at all
 */
public record ReportMitigation(String reason, String plan, String addedOn, Kind kind) {

    /**
     * A mitigation the project, or the harness, answers for.
     *
     * @param reason  why the vulnerability can't be exploited in this project
     * @param plan    what limits the risk now, and what will remove the vulnerable package
     * @param addedOn the date the exception was recorded
     * @param harness whether Airness answers for the exception rather than the project
     */
    public ReportMitigation(String reason, String plan, String addedOn, boolean harness) {
        this(reason, plan, addedOn, harness ? Kind.HARNESS : Kind.PROJECT);
    }

    /**
     * Reads a mitigation from the notes of the exception the scanner applied.
     *
     * @param notes   the applied exception's notes
     * @param harness whether those notes belong to an Airness exception
     * @return the reason, plan and date the notes record
     */
    public static ReportMitigation of(String notes, boolean harness) {
        SuppressionNotes parsed = SuppressionNotes.parse(notes);
        return new ReportMitigation(parsed.reason(), parsed.mitigation(), parsed.addedOn(), harness);
    }

    /**
     * Whether Airness, rather than the project, recorded the account.
     *
     * @return whether the harness answers for it
     */
    public boolean harness() {
        return this.kind != Kind.PROJECT;
    }

    /**
     * Who answers for an account, and whether it is a mitigation.
     */
    public enum Kind {

        /**
         * A mitigation the project recorded.
         */
        PROJECT,

        /**
         * A mitigation the harness ships, such as one for a scanner's confirmed false match.
         */
        HARNESS,

        /**
         * No mitigation: a finding in a container image that no release of the image can fix yet, which the build
         * accepts until one can.
         */
        AWAITING_FIX
    }
}
