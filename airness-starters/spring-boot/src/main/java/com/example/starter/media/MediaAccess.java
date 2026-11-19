package com.example.starter.media;

/**
 * Says whether the reader may see a stored file. Each feature that stores files declares one for the files it
 * stores, and a file is served to a reader when any of them lets it. A file none of them lets is answered as
 * one that does not exist.
 */
@FunctionalInterface
public interface MediaAccess {

    /**
     * Answers whether the reader may see the given file.
     *
     * @param media the file
     * @return whether the reader may see it
     */
    boolean permits(MediaObject media);
}
