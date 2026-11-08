package com.example.starter.web;

import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * The words of the application, in the language the reader of the page was resolved to.
 *
 * <p>A page asks this bean for a wording chosen by a value rather than by the page, such as the status a
 * task happens to be in. Nothing composes a key out of a value: a page hands over the constant and this
 * answers its wording, so the reach of the lookup is this class rather than the template engine. A key
 * nothing declares is an error rather than a blank, so a missing wording is never shipped to a reader.
 */
@Component
public final class Vocabulary {

    private final MessageSource messages;

    /**
     * Words the application through the given messages.
     *
     * @param messages the wordings of the application, in every language it speaks
     */
    public Vocabulary(MessageSource messages) {
        this.messages = messages;
    }

    /**
     * Answers the wording of the given constant.
     *
     * @param named the constant to word
     * @return the words that constant is shown in
     */
    public String of(Named named) {
        return this.of(named.key());
    }

    /**
     * Answers the wording written under the given key.
     *
     * @param key the name a wording is written under
     * @return the words written under that key
     */
    public String of(String key) {
        return this.messages.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    /**
     * Answers the given constants as a control offers them, each carrying the constant a form submits
     * and the words it is shown in.
     *
     * @param constants the vocabulary the control offers, in the order it is offered in
     * @return what the control offers, in the order it was given
     */
    public List<Option> options(List<? extends Named> constants) {
        return constants.stream().map(constant -> new Option(constant.name(), this.of(constant))).toList();
    }
}
