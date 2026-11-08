package com.example.starter.web;

/**
 * One choice a control offers, as what the form submits and what the reader is shown.
 *
 * @param value what the form submits when this choice is made
 * @param label the words the choice is shown in
 */
public record Option(String value, String label) {
}
