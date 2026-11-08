package com.example.starter.web;

/**
 * One labelled control of a form, as the page describes it. The four values describe one thing and are
 * handed to a template as one argument, because a fragment is capped at four parameters like any other
 * callable and four positional strings told apart only by their order are what that cap prevents.
 *
 * @param key     the name the label of this control is written under
 * @param name    what the control is called in the submitted form
 * @param type    the input type of the control, or {@code prose} for the box a paragraph is typed into
 * @param purpose what a browser may fill the control with, as its autocomplete attribute states it
 */
public record Control(String key, String name, String type, String purpose) {
}
