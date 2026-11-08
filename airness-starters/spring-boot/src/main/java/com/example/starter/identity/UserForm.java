package com.example.starter.identity;

import com.example.starter.web.Wording;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Everything an administrator states about an account on the form that admits one or restates one.
 *
 * <p>The password is drawn only on the page that admits an account. Restating an account changes its
 * name, address and role, and a form that asked for the password again in order to change a role would
 * either reset a password nobody asked to reset or refuse the change for want of a value already stored.
 *
 * <p>What is required and holds nothing is said by {@link #missing()} rather than by a rule beside each
 * component, because a rule saying a value is required is a nullness annotation, and the compiler refuses
 * one written beside the annotation that says the same value may arrive as nothing.
 *
 * @param profile  how the account is named and reached
 * @param role     what the account may do, or nothing where nobody has chosen yet
 * @param password the password the account signs in with, which is hashed and never stored as typed
 */
public record UserForm(@Valid UserProfile profile, @Nullable Role role, String password) {

    /**
     * The name of the control that carries the password.
     */
    static final String PASSWORD = "password";

    /**
     * Stands an empty value in the place of anything the binder could not read.
     *
     * @param profile  how the account is named and reached, or nothing
     * @param role     what it may do, or nothing
     * @param password the password it signs in with, or nothing
     */
    public UserForm(@Nullable UserProfile profile, @Nullable Role role, @Nullable String password) {
        this.profile = Objects.requireNonNullElse(profile, UserProfile.unnamed());
        this.role = role;
        this.password = Objects.requireNonNullElse(password, "");
    }

    /**
     * Answers the form the page for admitting an account opens with, which names nobody.
     *
     * @return an empty form
     */
    public static UserForm blank() {
        return new UserForm(UserProfile.unnamed(), null, "");
    }

    /**
     * Answers every required control of this form that was left alone, under the name of that control.
     *
     * @return one message per control that holds nothing, and nothing where each holds something
     */
    public Map<String, Wording> missing() {
        return Optional.ofNullable(this.role).isEmpty() ? Map.of("role", Wording.of("user.role.unchosen")) : Map.of();
    }

    /**
     * Answers what is wrong with the password on this form, which only the page that admits an account
     * asks.
     *
     * @return a message against the control that carries the password, and nothing where it is well chosen
     */
    public Map<String, Wording> unchosen() {
        return PasswordPolicy.refusal(this.password, PASSWORD);
    }

    /**
     * Answers the account this form states, carrying the given hash of the password that was typed.
     *
     * @param passwordHash the hash of the password
     * @param admittedAt   when the account is admitted
     * @return that account, enabled
     * @throws NullPointerException when the role the rules require was never chosen
     */
    public User stated(String passwordHash, Instant admittedAt) {
        return new User(this.profile, new UserAccess(this.chosen(), true), passwordHash, admittedAt);
    }

    /**
     * Answers the role this form states, which the rules above refuse a form without.
     *
     * @return what the account may do
     * @throws NullPointerException when the role was never chosen
     */
    public Role chosen() {
        return Objects.requireNonNull(this.role, "An account is given a role");
    }
}
