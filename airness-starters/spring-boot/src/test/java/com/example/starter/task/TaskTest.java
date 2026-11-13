package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final LocalDate DUE = LocalDate.parse("2026-11-08");

    private static Task task() {
        return new Task(1, new TaskContent("Write", "", TaskStatus.OPEN, DUE), Instant.now());
    }

    @Test
    void staysTheSameTaskWhenRevised() {
        Task task = task();
        assertEquals(task, task.revised(task.getContent().moved(TaskStatus.DONE), Instant.now(), 0));
    }

    @Test
    void hashesAsTheSameTaskWhenRevised() {
        Task task = task();
        assertEquals(task.hashCode(), task.revised(task.getContent(), Instant.now(), 0).hashCode());
    }

    @Test
    void isAnotherTaskThanOneWrittenSeparately() {
        assertNotEquals(task(), task());
    }

    @Test
    void carriesTheVersionTheRevisionWasMadeAgainst() {
        assertEquals(4, task().revised(task().getContent(), Instant.now(), 4).getVersion());
    }

    @Test
    void isNoOtherKindOfObject() {
        Task task = task();
        Object reference = task.getReference();
        assertNotEquals(task, reference, "a task equals only a task, not even the reference it is known by");
    }
}
