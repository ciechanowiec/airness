package com.example.starter.identity;

import com.example.starter.web.Wording;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a reader states on the page that changes the password they sign in with: the one they sign in
 * with today, which proves it is them at the keyboard, and the one they choose.
 *
 * @param current the password the reader signs in with today
 * @param chosen  the password the reader signs in with from now on
 */
public record PasswordForm(String current, String chosen) {

    /**
     * The name of the control that carries the current password.
     */
    static final String CURRENT = "current";

    /**
     * The name of the control that carries the chosen password.
     */
    static final String CHOSEN = "chosen";

    /**
     * Stands an empty value in the place of anything the binder could not read.
     *
     * @param current the current password, or nothing
     * @param chosen  the chosen password, or nothing
     */
    public PasswordForm(@Nullable String current, @Nullable String chosen) {
        this.current = Objects.requireNonNullElse(current, "");
        this.chosen = Objects.requireNonNullElse(chosen, "");
    }

    /**
     * Answers what is wrong with the password chosen on this form.
     *
     * @return a message against the control that carries it, and nothing where it is well chosen
     */
    public Map<String, Wording> unchosen() {
        return PasswordPolicy.refusal(this.chosen, CHOSEN);
    }
}
