package com.example.starter.media;

/**
 * What the first bytes of a file say it is.
 *
 * @param kind        how the file is drawn
 * @param contentType the media type the file is served as
 */
record Detected(MediaKind kind, String contentType) {
}
