package com.example.starter.media;

import org.springframework.core.io.InputStreamSource;

/**
 * A file somebody hands the application: the name it was handed over under and where its bytes are read
 * from, which is an uploaded part as readily as a resource the application ships.
 *
 * @param name   the name the file was handed over under
 * @param source where its bytes are read from
 */
public record Upload(String name, InputStreamSource source) {
}
