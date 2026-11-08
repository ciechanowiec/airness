package com.example.starter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * What every page is drawn with, whichever feature draws it: the words of the application, the notice
 * of a change the reader was sent on after, and the address of the page itself, which the language
 * switch sends the reader back to.
 */
@ControllerAdvice
public final class Pages {

    private final Vocabulary words;

    /**
     * Tells every page the words of the application through the given vocabulary.
     *
     * @param words the words of the application, in the language the reader was resolved to
     */
    public Pages(Vocabulary words) {
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
     * Answers the address of the page being drawn, with the query it was asked with.
     *
     * @param request the request the page answers
     * @return the path and query of that request
     */
    @ModelAttribute("here")
    public String here(HttpServletRequest request) {
        return request.getRequestURI() + Optional.ofNullable(request.getQueryString()).map("?"::concat).orElse("");
    }
}
