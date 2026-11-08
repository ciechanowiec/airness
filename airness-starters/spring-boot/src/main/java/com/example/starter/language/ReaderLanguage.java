package com.example.starter.language;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Which language every page of one request is drawn in.
 *
 * <p>A reader who chose a language with the switch is answered in it, whether or not they have signed in,
 * because the choice is held in a cookie of its own. A reader who never chose is answered in the language
 * their browser asks for, where the application speaks it, and in English otherwise. A request that asks
 * for no language at all is answered in English too, rather than in the language of the machine the
 * server happens to run on.
 *
 * <p>The cookie holds a language and nothing else, so it is sent over plain HTTP as readily as over HTTPS:
 * whoever carries the traffic learns which language a reader reads, which the page they are shown says
 * anyway, while a cookie refused over plain HTTP would leave the switch doing nothing on a deployment
 * served without TLS. No script of the page can read it.
 */
@Component("localeResolver")
public final class ReaderLanguage implements LocaleResolver {

    private static final String COOKIE = "language";

    // A choice of language is worth remembering for as long as a reader keeps the browser, and a year is
    // the span a browser is still likely to be the same one.
    private static final Duration KEPT = Duration.ofDays(365);

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return chosen(request).or(() -> asked(request)).orElse(Language.ENGLISH).locale();
    }

    /**
     * Refuses to set a language by any route but the language switch, which keeps the choice through the
     * cookie {@link #kept(Language)} answers.
     *
     * @param request  the request
     * @param response the response
     * @param locale   the locale asked for
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setLocale(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable Locale locale) {
        throw new UnsupportedOperationException("A language is chosen through the language switch alone");
    }

    /**
     * Answers the cookie that keeps the given language for the reader's next requests.
     *
     * @param language the language the reader chose
     * @return the cookie the response sets
     */
    public static ResponseCookie kept(Language language) {
        return ResponseCookie.from(COOKIE, language.name())
            .path("/")
            .maxAge(KEPT)
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .build();
    }

    private static Optional<Language> chosen(HttpServletRequest request) {
        return Optional.ofNullable(request.getCookies())
            .stream()
            .flatMap(Arrays::stream)
            .filter(cookie -> COOKIE.equals(cookie.getName()))
            .map(Cookie::getValue)
            .flatMap(value -> Language.named(value).stream())
            .findFirst();
    }

    // The container answers getLocale with its own default when the browser named nothing, so the header
    // is asked first and the locale is read only from a request that carries one.
    private static Optional<Language> asked(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE))
            .flatMap(_ -> Language.of(request.getLocale()));
    }
}
