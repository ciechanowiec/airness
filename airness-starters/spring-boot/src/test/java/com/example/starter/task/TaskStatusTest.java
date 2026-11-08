package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskStatusTest {

    @Test
    void readsAStatusBackFromItsName() {
        assertEquals(Optional.of(TaskStatus.IN_PROGRESS), TaskStatus.named("IN_PROGRESS"));
    }

    @Test
    void readsNoStatusFromANameNoneCarries() {
        assertTrue(TaskStatus.named("LOST").isEmpty());
    }
}
