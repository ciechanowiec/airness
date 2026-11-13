package com.example.starter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * What every page is drawn with, whichever feature draws it: the words of the application, the notice
 * of a change the reader was sent on after, the address of the page itself, which the language switch
 * sends the reader back to, and no refusal, which a page that draws a refused form replaces.
 */
@ControllerAdvice
public final class PageModel {

    /**
     * The name a page reads what is wrong with its form under.
     */
    public static final String REFUSALS = "refusals";

    /**
     * The address of the home page, where a reader lands once signed in.
     */
    public static final String HOME = "/";

    private final Vocabulary words;

    /**
     * Tells every page the words of the application through the given vocabulary.
     *
     * @param words the words of the application, in the language the reader was resolved to
     */
    public PageModel(Vocabulary words) {
        this.words = words;
    }

    /**
     * Answers the words of the application, which a page asks for the wording of a constant it holds.
     *
     * @return the vocabulary of this request
     */
    @ModelAttribute("words")
    public Vocabulary words() {
        return this.words;
    }

    /**
     * Answers what the page tells the reader it was sent on to after a change, which the address
     * carries.
     *
     * @param notice the name of the notice the address carries, which is empty where it carries none
     * @return that notice, and nothing where the address carries none the application says
     */
    @ModelAttribute("notice")
    public Optional<Notice> notice(@RequestParam(name = Notice.PARAMETER, defaultValue = "") String notice) {
        return Notice.named(notice);
    }

    /**
     * Answers no refusal against any control, so a template never asks whether the reader was refused.
     *
     * @return no refusal at all
     */
    @ModelAttribute(REFUSALS)
    public Map<String, String> nothingRefused() {
        return Map.of();
    }

    /**
     * Answers the address of the page being drawn, with the query it was asked with, where it can be asked
     * for again. A page drawn in answer to a post, such as a form drawn again with what is wrong on it,
     * stands at an address that answers a post alone, so the home page stands in for it.
     *
     * @param request the request the page answers
     * @return the path and query of that request, or the home page where it was not a read
     */
    @ModelAttribute("here")
    public String here(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return HOME;
        }
        return request.getRequestURI() + Optional.ofNullable(request.getQueryString()).map("?"::concat).orElse("");
    }
}
