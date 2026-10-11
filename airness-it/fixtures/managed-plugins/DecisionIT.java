package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DecisionIT {

    @Test
    void coversTheDisabledOutcome() {
        assertEquals(0, new Decision().value(false));
    }
}
