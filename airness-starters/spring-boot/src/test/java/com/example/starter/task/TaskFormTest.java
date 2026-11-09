package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.web.Wording;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskFormTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Test
    void opensDueTodayAndOpen() {
        assertEquals(new TaskForm("", "", TaskStatus.OPEN, TODAY, 0), TaskForm.blank(TODAY));
    }

    @Test
    void saysAStatusAndADayNobodyGaveAreMissing() {
        Map<String, Wording> missing = new TaskForm("Write", "", null, null, 0).missing();
        assertEquals(
            Map.of("status", Wording.of("task.status.unchosen"), "dueOn", Wording.of("task.dueOn.blank")),
            missing
        );
    }

    @Test
    void saysNothingIsMissingOnceBothAreGiven() {
        assertTrue(TaskForm.blank(TODAY).missing().isEmpty());
    }

    @Test
    void trimsWhatWasTyped() {
        assertEquals("Write", new TaskForm("  Write ", "", TaskStatus.OPEN, TODAY, 0).title());
    }

    @Test
    void refusesADescriptionWiderThanItsColumn() {
        String description = "x".repeat(TaskContent.DESCRIPTION_LIMIT + 1);
        TaskForm form = new TaskForm("Write", description, TaskStatus.OPEN, TODAY, 0);
        try (ValidatorFactory validation = Validation.buildDefaultValidatorFactory()) {
            List<String> refusals = validation.getValidator().validate(form).stream()
                .map(ConstraintViolation::getMessageTemplate)
                .toList();
            assertEquals(List.of("task.description.long"), refusals);
        }
    }

    @Test
    void refusesToStateATaskWithoutADueDay() {
        TaskForm form = new TaskForm("Write", "", TaskStatus.OPEN, null, 0);
        assertThrows(NullPointerException.class, form::content);
    }

    @Test
    void opensOnWhatATaskSaysAndTheVersionItIsAt() {
        TaskContent content = new TaskContent("Write", "it", TaskStatus.DONE, TODAY);
        TaskForm form = TaskForm.of(new TaskView("reference", content, false, "", 3));
        assertEquals(new TaskForm("Write", "it", TaskStatus.DONE, TODAY, 3), form);
    }
}
