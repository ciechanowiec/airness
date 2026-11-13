package com.example.starter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HtmxTest {

    private static final String YES = "true";

    @Test
    void answersTheFragmentToARequestHtmxMade() {
        assertEquals("tasks/list :: results", new Htmx(YES, "").view("tasks/list", "results"));
    }

    @Test
    void answersTheWholePageToARequestHtmxDidNotMake() {
        assertEquals("tasks/list", new Htmx("", "").view("tasks/list", "results"));
    }

    @Test
    void saysARequestHtmxMadeIsPartial() {
        assertTrue(new Htmx(YES, "").partial());
    }

    @Test
    void saysARequestWithoutTheHeaderIsNotPartial() {
        assertFalse(new Htmx("", "").partial());
    }

    @Test
    void answersTheWholePageToANavigationHtmxBoosted() {
        assertEquals("tasks/list", new Htmx(YES, YES).view("tasks/list", "results"));
    }
}
