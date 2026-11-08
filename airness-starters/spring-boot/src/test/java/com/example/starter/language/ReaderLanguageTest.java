package com.example.starter.language;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;

@DatabaseTest
class ReaderLanguageTest {

    private static final String LOGIN = "/login";

    private static final String POLISH = "lang=\"pl\"";

    private static final String ENGLISH = "lang=\"en\"";

    private static String landing(HttpResponse<String> response) {
        return response.headers().firstValue(HttpHeaders.LOCATION).orElse("");
    }

    @Test
    void answersInPolishABrowserAskingForIt(@LocalServerPort int port) {
        assertTrue(new Caller(port).reading(LOGIN, "pl-PL,pl;q=0.9").body().contains(POLISH));
    }

    @Test
    void answersInEnglishABrowserAskingForNoLanguage(@LocalServerPort int port) {
        assertTrue(new Caller(port).get(LOGIN).body().contains(ENGLISH));
    }

    @Test
    void answersInEnglishABrowserAskingForALanguageNotSpoken(@LocalServerPort int port) {
        assertTrue(new Caller(port).reading(LOGIN, "de-DE").body().contains(ENGLISH));
    }

    @Test
    void keepsTheLanguageChosenWithTheSwitch(@LocalServerPort int port) {
        Caller caller = new Caller(port);
        HttpResponse<String> answer = caller.post("/language", "language=POLISH&back=/login");
        assertAll(
            () -> assertTrue(landing(answer).endsWith(LOGIN)),
            () -> assertTrue(caller.get(LOGIN).body().contains(POLISH))
        );
    }

    @Test
    void prefersTheLanguageChosenOverTheOneTheBrowserAsksFor(@LocalServerPort int port) {
        Caller caller = new Caller(port);
        caller.post("/language", "language=ENGLISH&back=/");
        assertTrue(caller.reading(LOGIN, "pl").body().contains(ENGLISH));
    }

    @Test
    void keepsTheChoiceWhereThePageCannotReadIt(@LocalServerPort int port) {
        String cookie = new Caller(port).post("/language", "language=POLISH&back=/")
            .headers()
            .firstValue(HttpHeaders.SET_COOKIE)
            .orElse("");
        assertAll(
            () -> assertTrue(cookie.contains("HttpOnly"), cookie),
            () -> assertTrue(cookie.contains("SameSite=Lax"), cookie)
        );
    }

    @Test
    void answersALanguageNobodySpeaksInEnglish(@LocalServerPort int port) {
        Caller caller = new Caller(port);
        caller.post("/language", "language=KLINGON&back=/");
        assertTrue(caller.reading(LOGIN, "pl").body().contains(ENGLISH));
    }

    @Test
    void sendsTheReaderHomeRatherThanToAnotherHost(@LocalServerPort int port) {
        assertEquals("/", landing(new Caller(port).post("/language", "language=POLISH&back=//elsewhere.test/")));
    }
}
