package com.example.starter.web;

/**
 * What htmx says about the request it made. htmx names itself in a header on every request, and a
 * handler that sees it answers with the fragment of the page that htmx replaces rather than the page.
 *
 * <p>A link or form the page shell boosts is made by htmx too, but it navigates: htmx swaps the whole body
 * with what it is answered, so a boosted request is answered with the whole page, like a request htmx did
 * not make.
 */
public final class Htmx {

    /**
     * The header htmx sets on every request it makes.
     */
    public static final String HEADER = "HX-Request";

    /**
     * The header htmx sets on a request a boosted link or form made.
     */
    public static final String BOOSTED = "HX-Boosted";

    private final boolean partial;

    /**
     * Reads what the headers of a request say about who made it and why.
     *
     * @param header  the value of the htmx header, which is empty when the caller is not htmx
     * @param boosted the value of the boosted header, which is empty unless a boosted link or form made it
     */
    public Htmx(String header, String boosted) {
        this.partial = Boolean.parseBoolean(header) && !Boolean.parseBoolean(boosted);
    }

    /**
     * Answers whether htmx made the request to replace a part of the page, rather than to navigate.
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
