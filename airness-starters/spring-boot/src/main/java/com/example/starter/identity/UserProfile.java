package com.example.starter.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * How an account is named and reached. The address is both what the account signs in with and how it
 * is told apart from every other, so it is held once, in one canonical spelling: without surrounding
 * space and in lower case, because two spellings of one address are one mailbox.
 *
 * <p>The same record is what somebody types on the form that admits an account, because the stored
 * reading of these values and the typed reading of them are the same reading, so the rules that make
 * one well formed sit here once. The bounds are the widths the migration gave the columns.
 *
 * @param email    the address the account signs in with, unique across the application
 * @param fullName the name the account is shown under
 */
@Embeddable
public record UserProfile(
    @Email(message = "user.email.shape")
    @NotBlank(message = "user.email.blank")
    @Size(max = EMAIL_LIMIT, message = "user.email.long")
    @Column(name = "email", nullable = false, unique = true, length = EMAIL_LIMIT)
    String email,

    @NotBlank(message = "user.fullName.blank")
    @Size(max = NAME_LIMIT, message = "user.fullName.long")
    @Column(name = "full_name", nullable = false, length = NAME_LIMIT)
    String fullName
) {

    static final int EMAIL_LIMIT = 180;

    static final int NAME_LIMIT = 120;

    /**
     * Stands the empty string in the place of a value that arrived as nothing, and holds the address in
     * its canonical spelling.
     *
     * @param email    the address the account signs in with, or nothing
     * @param fullName the name the account is shown under, or nothing
     */
    public UserProfile(@Nullable String email, @Nullable String fullName) {
        this.email = canonical(Objects.requireNonNullElse(email, ""));
        this.fullName = Objects.requireNonNullElse(fullName, "").strip();
    }

    /**
     * Answers a profile nobody has named yet, which is what the form that admits an account opens with.
     *
     * @return a profile holding nothing
     */
    public static UserProfile unnamed() {
        return new UserProfile("", "");
    }

    /**
     * Answers the canonical spelling of an address, which is how every address is stored and looked up.
     *
     * @param email an address as somebody typed it
     * @return the address without surrounding space and in lower case
     */
    public static String canonical(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
