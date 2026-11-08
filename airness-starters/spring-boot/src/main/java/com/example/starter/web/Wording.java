package com.example.starter.web;

import java.util.List;

/**
 * One thing a reader is told, carried as the name its wording is written under and the values that
 * wording names. The values are carried written out, because only the sentence knows where they go.
 *
 * @param key       the name the wording is written under
 * @param arguments the values the wording names, in the order its placeholders count them
 */
public record Wording(String key, List<String> arguments) {

    /**
     * Keeps its own copy of the arguments, so what is carried is what was stated.
     */
    public Wording {
        arguments = List.copyOf(arguments);
    }

    /**
     * Answers a wording that names no value at all, which is what most refusals are.
     *
     * @param key the name the wording is written under
     * @return that wording
     */
    public static Wording of(String key) {
        return new Wording(key, List.of());
    }
}
