package com.example.starter.identity;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * What every page is told about the reader in front of it, so the navigation offers only what the
 * reader may open. This decides nothing: the rule that refuses an action lives on the application layer
 * and holds whether the control was drawn or the request was simply made.
 */
@ControllerAdvice
public final class ReaderModel {

    private final UserRepository repository;

    /**
     * Reads the reader's account from the given repository.
     *
     * @param repository the accounts of the application
     */
    public ReaderModel(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * Answers the name the signed-in reader is shown under, which the navigation greets them with.
     *
     * @return the name, or empty where nobody has signed in
     */
    @ModelAttribute("readerName")
    public String readerName() {
        return Reader.email()
            .flatMap(this.repository::findByProfileEmail)
            .map(user -> user.getProfile().fullName())
            .orElse("");
    }

    /**
     * Answers the address the signed-in reader signs in with, by which the list of accounts knows the
     * reader's own row and offers no control that would stop the reader themselves.
     *
     * @return the address, or empty where nobody has signed in
     */
    @ModelAttribute("readerEmail")
    public String readerEmail() {
        return Reader.email().orElse("");
    }

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
