package com.example.starter.language;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * A language the application speaks. The enum is the one place a language is declared: every locale the
 * application resolves and every choice the language switch offers is read against these constants.
 *
 * <p>Each one carries the words it names itself in rather than words in the reader's language, so a
 * reader who has landed in a language they cannot read still finds the way out written in their own.
 */
public enum Language {

    /**
     * The language the application falls back to.
     */
    ENGLISH(Locale.ENGLISH, "English"),

    /**
     * Polish.
     */
    POLISH(Locale.of("pl"), "Polski");

    private final Locale locale;

    private final String endonym;

    Language(Locale locale, String endonym) {
        this.locale = locale;
        this.endonym = endonym;
    }

    /**
     * Answers the language the given locale asks for. Only the language is compared, so a browser asking
     * for Polish as spoken in one country is answered as one asking for it in another.
     *
     * @param asked the locale a caller asks to be answered in
     * @return the language that locale names, and nothing where the application speaks none of it
     */
    public static Optional<Language> of(Locale asked) {
        return Arrays.stream(values())
            .filter(language -> language.locale.getLanguage().equals(asked.getLanguage()))
            .findFirst();
    }

    /**
     * Answers the language a constant name stands for, which is how a submitted choice and a stored
     * cookie are read back.
     *
     * @param name the name of the constant, as a form submits it
     * @return the language of that name, and nothing where no language carries it
     */
    public static Optional<Language> named(String name) {
        return Arrays.stream(values()).filter(language -> language.name().equals(name)).findFirst();
    }

    /**
     * Answers the locale every message is read under for this language.
     *
     * @return the locale of this language
     */
    public Locale locale() {
        return this.locale;
    }

    /**
     * Answers what this language is called in itself, which is what the language switch shows.
     *
     * @return the words this language names itself in
     */
    public String endonym() {
        return this.endonym;
    }
}
