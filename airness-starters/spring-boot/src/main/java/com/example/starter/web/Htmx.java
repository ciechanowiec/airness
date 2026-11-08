package com.example.starter.web;

/**
 * What htmx says about the request it made. htmx names itself in a header on every request, and a
 * handler that sees it answers with the fragment of the page that htmx replaces rather than the page.
 */
public final class Htmx {

    /**
     * The header htmx sets on every request it makes.
     */
    public static final String HEADER = "HX-Request";

    private final boolean partial;

    /**
     * Reads what the header of a request says about who made it.
     *
     * @param header the value of the htmx header, which is empty when the caller is not htmx
     */
    public Htmx(String header) {
        this.partial = Boolean.parseBoolean(header);
    }

    /**
     * Answers whether htmx made the request, and so asks for the part of the page it replaces.
     *
     * @return whether the request asks for a fragment rather than a page
     */
    public boolean partial() {
        return this.partial;
    }

    /**
     * Answers the view a handler renders: the named fragment of the page when htmx asked for one, and
     * the whole page otherwise. One template therefore serves the first visit and every update.
     *
     * @param page     the template of the whole page
     * @param fragment the fragment of that template which htmx swaps in
     * @return the name of the view that the handler returns
     */
    public String view(String page, String fragment) {
        return this.partial ? "%s :: %s".formatted(page, fragment) : page;
    }
}
