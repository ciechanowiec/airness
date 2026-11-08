package com.example.starter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WordingTest {

    @Test
    void namesNoValueWhenMadeFromAKeyAlone() {
        assertEquals(List.of(), Wording.of("task.title.blank").arguments());
    }

    @Test
    void keepsItsOwnCopyOfTheValuesItNames() {
        List<String> values = new ArrayList<>(List.of("3"));
        Wording wording = new Wording("pagination.page", values);
        values.add("4");
        assertEquals(List.of("3"), wording.arguments());
    }
}
