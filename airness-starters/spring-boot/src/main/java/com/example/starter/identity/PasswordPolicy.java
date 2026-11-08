package com.example.starter.identity;

import com.example.starter.web.Wording;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * What a password chosen for an account must be. Every form that sets a password, and the first
 * administrator written at startup, is held to this one rule.
 */
@UtilityClass
public final class PasswordPolicy {

    /**
     * How short a password is refused. Twelve characters of anything outlast eight of something a
     * pattern generates.
     */
    static final int SHORTEST = 12;

    /**
     * How long a password is refused. BCrypt reads the first seventy-two bytes of what it is given and
     * ignores the rest, so a longer password would end in characters that decide nothing.
     */
    static final int LONGEST = 72;

    /**
     * Answers what is wrong with the given password, under the name of the control that carries it.
     *
     * @param password the password somebody chose
     * @param control  the name of the control the password was typed into
     * @return one refusal against that control, and nothing where the password is well chosen
     */
    public static Map<String, Wording> refusal(String password, String control) {
        return problem(password).map(key -> Map.of(control, Wording.of(key))).orElseGet(Map::of);
    }

    /**
     * Answers whether the given password meets the policy.
     *
     * @param password the password somebody chose
     * @return whether it may be set
     */
    public static boolean admits(String password) {
        return problem(password).isEmpty();
    }

    // The first rule the password breaks, in the order a reader is best told them.
    private static Optional<String> problem(String password) {
        return Stream.of(
            Map.entry(password.isBlank(), "password.blank"),
            Map.entry(password.length() < SHORTEST, "password.short"),
            Map.entry(password.length() > LONGEST, "password.long")
        ).filter(Map.Entry::getKey).map(Map.Entry::getValue).findFirst();
    }
}
