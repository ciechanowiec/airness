package com.example.starter.identity;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * What every page is told about the reader in front of it, so the navigation offers only what the
 * reader may open. This decides nothing: the rule that refuses an action lives on the application layer
 * and holds whether the control was drawn or the request was simply made.
 */
@ControllerAdvice
public final class Readers {

    /**
     * Answers whether somebody has signed in, which decides whether a page offers signing in or out.
     *
     * @return whether the reader of the page has signed in
     */
    @ModelAttribute("signedIn")
    public boolean signedIn() {
        return Reader.email().isPresent();
    }

    /**
     * Answers whether the reader manages the accounts of the application.
     *
     * @return whether the reader is an administrator
     */
    @ModelAttribute("administrator")
    public boolean administrator() {
        return Reader.holds(Role.ADMIN);
    }
}
