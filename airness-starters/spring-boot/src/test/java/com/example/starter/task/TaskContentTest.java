package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TaskContentTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Test
    void isOverdueOnceItsDayHasPassedAndItIsNotDone() {
        assertTrue(new TaskContent("Write", "", TaskStatus.OPEN, TODAY.minusDays(1)).overdueOn(TODAY));
    }

    @Test
    void isNotOverdueOnItsOwnDay() {
        assertFalse(new TaskContent("Write", "", TaskStatus.IN_PROGRESS, TODAY).overdueOn(TODAY));
    }

    @Test
    void isNeverOverdueOnceDone() {
        assertFalse(new TaskContent("Write", "", TaskStatus.DONE, TODAY.minusDays(1)).overdueOn(TODAY));
    }

    @Test
    void keepsWhatItSaysWhenMoved() {
        TaskContent content = new TaskContent("Write", "the letter", TaskStatus.OPEN, TODAY);
        assertEquals(new TaskContent("Write", "the letter", TaskStatus.DONE, TODAY), content.moved(TaskStatus.DONE));
    }
}
