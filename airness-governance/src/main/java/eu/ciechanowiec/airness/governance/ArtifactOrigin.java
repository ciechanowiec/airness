package eu.ciechanowiec.airness.governance;

/**
 * An archive entry's name and exact content fingerprint, without retaining dependency bytes in memory.
 *
 * @param name   the archive entry name
 * @param digest the SHA-256 fingerprint of its bytes
 */
record ArtifactOrigin(String name, String digest) {
}
