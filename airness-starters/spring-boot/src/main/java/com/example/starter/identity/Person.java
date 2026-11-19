package com.example.starter.identity;

import com.example.starter.media.MediaView;

/**
 * Somebody as a page draws them: the name they are shown under and the portrait beside it, which is
 * {@link MediaView#NONE} for somebody who has none and is drawn by their initials instead.
 *
 * @param name     the name they are shown under
 * @param portrait their portrait
 */
public record Person(String name, MediaView portrait) {

    /**
     * Answers somebody drawn by their name alone.
     *
     * @param name the name they are shown under
     * @return that person, without a portrait
     */
    public static Person named(String name) {
        return new Person(name, MediaView.NONE);
    }
}
