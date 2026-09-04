package eu.ciechanowiec.airness.governance;

/**
 * One bare name the blocklist refuses: a system package a Dockerfile installs, or a JDK distribution a
 * workflow or a version manager selects.
 *
 * @param name        the name as the file writes it
 * @param reason      why it is refused
 * @param replacement what to name instead
 */
record BlockedName(String name, String reason, String replacement) {
}
