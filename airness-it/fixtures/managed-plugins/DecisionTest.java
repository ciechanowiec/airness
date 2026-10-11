package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DecisionTest {

    @Test
    void coversTheEnabledOutcome() {
        assertEquals(1, new Decision().value(true));
    }
}
