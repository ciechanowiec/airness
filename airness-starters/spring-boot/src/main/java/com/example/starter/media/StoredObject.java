package com.example.starter.media;

import java.io.InputStream;

/**
 * The bytes of one object as the store answers them, with how many there are.
 *
 * @param content the bytes, which the reader closes
 * @param length  how many bytes there are
 */
public record StoredObject(InputStream content, long length) {
}
