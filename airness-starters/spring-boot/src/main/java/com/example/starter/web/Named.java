package com.example.starter.web;

/**
 * A constant that is written one way and read another.
 *
 * <p>Every vocabulary of the application, such as the role of a user or the status of a task, is an
 * enum shown to a reader as words. The constant is one fact and its wording is one per language, so
 * the constant carries the name its wording is written under and the wording itself is looked up.
 */
public interface Named {

    /**
     * Answers the name this constant's wording is written under.
     *
     * @return the message key of this constant
     */
    String key();

    /**
     * Answers the constant itself, as a form submits it and as a column holds it. Every implementer is
     * an enum, which declares this already.
     *
     * @return the name of this constant
     */
    String name();
}
