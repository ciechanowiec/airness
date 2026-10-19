package eu.ciechanowiec.airness.governance;

/**
 * The recorded mitigation that covers an advisory, read from the exception the scanner applied.
 *
 * @param reason  why the vulnerability can't be exploited in this project
 * @param plan    what limits the risk now, and what will remove the vulnerable package
 * @param addedOn the date the exception was recorded
 * @param harness whether Airness answers for the exception rather than the project
 */
public record ReportMitigation(String reason, String plan, String addedOn, boolean harness) {

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
}
