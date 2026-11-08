package com.example.starter.language;

import java.util.List;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * What every page is told about the languages it can be read in, which is what the language switch in
 * the header of every page offers.
 */
@ControllerAdvice
public final class LanguageSwitch {

    /**
     * Answers the languages the application speaks, in the order the switch offers them.
     *
     * @return every language
     */
    @ModelAttribute("languages")
    public List<Language> languages() {
        return List.of(Language.values());
    }
}
