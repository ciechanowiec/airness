package com.example.starter.language;

/**
 * An address of this application that a reader is sent back to. It is read out of a request, so it is
 * held to being a path of this application before anybody is redirected to it: an address naming
 * another host, by a scheme or by a leading pair of slashes, is answered with the home page instead.
 *
 * @param path the path the reader is sent back to, always beginning with a single slash
 */
public record LocalAddress(String path) {

    private static final String ROOT = "/";

    /**
     * Answers the address the given path leads back to, which is the path itself where it stays within
     * this application and the home page otherwise.
     *
     * @param asked the path the request asked to be sent back to
     * @return the address a reader may be sent back to
     */
    public static LocalAddress of(String asked) {
        boolean local = asked.startsWith(ROOT) && !asked.startsWith("//") && !asked.startsWith("/\\");
        return new LocalAddress(local ? asked : ROOT);
    }
}
