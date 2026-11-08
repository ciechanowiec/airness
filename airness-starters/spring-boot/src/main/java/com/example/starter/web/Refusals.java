package com.example.starter.web;

import jakarta.validation.Validator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

/**
 * Everything wrong with a submitted form, under the name of the control that carries it, worded in the
 * language of the reader.
 *
 * <p>A form is validated here rather than by the binder alone, because the binder stops running the
 * rules once it could not read a value, so a form with two mistakes would report one of them. Three
 * sources are read, in the order they bind: what the binder could not read, what the rules of the form
 * refuse, and what the caller already knows, such as an address another account already uses.
 *
 * <p>A rule states a bare message key rather than the braced form the validator interpolates, so the
 * validator hands the key back untouched and it is worded here, under the language of the reader.
 */
@Component
public final class Refusals {

    private final MessageSource messages;

    private final Validator rules;

    /**
     * Reads a form against the given rules and words what is refused through the given messages.
     *
     * @param messages what turns a refusal into the sentence a reader is shown
     * @param rules    what a form is held to once the binder has read it
     */
    public Refusals(MessageSource messages, Validator rules) {
        this.messages = messages;
        this.rules = rules;
    }

    /**
     * Answers everything wrong with the given form, under the name of the control that carries it.
     *
     * @param form   the form as the binder built it, which is held to its own rules here
     * @param errors what the binder could not read
     * @param also   what the caller already knows is wrong, under the same control names
     * @return one message per refused control, and nothing at all where the form is well formed
     */
    public Map<String, String> of(Object form, BindingResult errors, Map<String, Wording> also) {
        Stream<Map.Entry<String, String>> unread = errors.getFieldErrors()
            .stream()
            .filter(FieldError::isBindingFailure)
            .map(refusal -> Map.entry(refusal.getField(), this.worded(refusal)));
        Stream<Map.Entry<String, String>> broken = this.rules.validate(form)
            .stream()
            .map(violation -> Map.entry(violation.getPropertyPath().toString(), this.worded(violation.getMessage())));
        Stream<Map.Entry<String, String>> known = also.entrySet()
            .stream()
            .map(refusal -> Map.entry(refusal.getKey(), this.worded(refusal.getValue())));
        return Stream.of(unread, broken, known)
            .flatMap(refusals -> refusals)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first));
    }

    private String worded(MessageSourceResolvable refusal) {
        return this.messages.getMessage(refusal, LocaleContextHolder.getLocale());
    }

    private String worded(String key) {
        return this.messages.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private String worded(Wording wording) {
        return this.messages.getMessage(wording.key(), wording.arguments().toArray(), LocaleContextHolder.getLocale());
    }
}
