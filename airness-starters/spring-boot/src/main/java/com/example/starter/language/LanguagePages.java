package com.example.starter.language;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The language switch every page carries. It is a form rather than a link, because choosing a language
 * changes what the browser keeps, and the reader is sent back to the page they chose it on, now drawn in
 * the language just chosen.
 */
@Controller
@RequestMapping(LanguagePages.LANGUAGE)
public final class LanguagePages {

    /**
     * Where the language switch posts, which the filter chain leaves open to readers who have not signed
     * in, because the sign-in page has to be legible before anybody has.
     */
    public static final String LANGUAGE = "/language";

    /**
     * Keeps the language the reader chose, and sends them back to the page they chose it on.
     *
     * @param language the name of the language chosen, where an unknown name keeps English
     * @param back     the path of the page the reader chose the language on
     * @return the redirect to that page, setting the cookie that keeps the choice
     */
    @PostMapping
    public ResponseEntity<Void> choose(
        @RequestParam(name = "language", required = true) String language,
        @RequestParam(name = "back", defaultValue = "/") String back
    ) {
        Language chosen = Language.named(language).orElse(Language.ENGLISH);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(LocalAddress.of(back).path()))
            .header(HttpHeaders.SET_COOKIE, ReaderLanguage.kept(chosen).toString())
            .build();
    }
}
