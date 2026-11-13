package com.example.starter.web;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The portraits people are drawn with, where the application ships one. A portrait is a file named after
 * the person under {@code static/images/people}, so the pages show a face for whoever has one and initials
 * for everybody else, and adding or removing a portrait is adding or removing a file.
 *
 * <p>Whether a file exists is read once for each name and remembered, because the files ship inside the
 * application and do not change while it runs.
 */
@Component("portraits")
public final class Portraits {

    private static final String FOLDER = "images/people/";

    private static final String EXTENSION = ".webp";

    private static final String STATIC = "static/";

    private final Map<String, Optional<String>> found;

    /**
     * Starts with no name looked up yet.
     */
    public Portraits() {
        this.found = new ConcurrentHashMap<>();
    }

    /**
     * Answers the address of the portrait the person with the given name is drawn with.
     *
     * @param name the name the person is shown under
     * @return the address of the portrait, or nothing where the application ships none for that name
     */
    public Optional<String> of(String name) {
        return this.found.computeIfAbsent(slug(name), Portraits::shipped);
    }

    /**
     * Answers the file name a portrait of the person with the given name is stored under: the name in lower
     * case and in plain Latin letters, its words joined by hyphens.
     *
     * @param name the name the person is shown under
     * @return the file name without its extension, empty for a name with no letter or digit
     */
    static String slug(String name) {
        String plain = Normalizer.normalize(name.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static Optional<String> shipped(String slug) {
        String path = FOLDER + slug + EXTENSION;
        return !slug.isEmpty() && new ClassPathResource(STATIC + path).exists()
            ? Optional.of("/" + path)
            : Optional.empty();
    }
}
