package eu.ciechanowiec.airness.governance;

/**
 * One thing the blocklist or the runner image rule refused, ready to be printed beside the file that named it.
 *
 * <p>Every refusal names the reason and the replacement, because a refusal that stops a build without
 * saying what to use instead sends the reader to the same search the rule already did.
 *
 * @param subject     the reference, the coordinate, the label, or the name as written
 * @param reason      why it is refused
 * @param replacement what to use instead
 */
record Refusal(String subject, String reason, String replacement) {

    /**
     * The refusal as one offence line.
     *
     * @param location the file, the inventory, or the environment that carries the subject
     * @return the offence
     */
    String at(String location) {
        return "%s: %s - %s; use %s".formatted(location, this.subject, this.reason, this.replacement);
    }
}
