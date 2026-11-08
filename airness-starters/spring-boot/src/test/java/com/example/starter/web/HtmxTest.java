package com.example.starter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HtmxTest {

    @Test
    void answersTheFragmentToARequestHtmxMade() {
        assertEquals("tasks/list :: results", new Htmx("true").view("tasks/list", "results"));
    }

    @Test
    void answersTheWholePageToARequestHtmxDidNotMake() {
        assertEquals("tasks/list", new Htmx("").view("tasks/list", "results"));
    }

    @Test
    void saysARequestHtmxMadeIsPartial() {
        assertTrue(new Htmx("true").partial());
    }

    @Test
    void saysARequestWithoutTheHeaderIsNotPartial() {
        assertFalse(new Htmx("").partial());
    }
}
