package com.example.starter.task;

import com.example.starter.web.Wording;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * What a reader states on the form that writes or changes a task.
 *
 * <p>The status and the due day may arrive as nothing, because the controls that carry them may be
 * submitted untouched, and what a rule would say about that is said by {@link #missing()} instead: a
 * rule saying a value is required is a nullness annotation, which the compiler refuses beside the one
 * saying the same value may arrive as nothing. The version is the version of the task the form was
 * opened on, which a change is made against.
 *
 * @param title       what is to be done
 * @param description anything more about it
 * @param status      where the task stands, or nothing where none was chosen
 * @param dueOn       the day the task is due, or nothing where none was given
 * @param version     the version of the task the form was opened on, which is nought for a new task
 */
public record TaskForm(
    @NotBlank(message = "task.title.blank")
    @Size(max = TaskContent.TITLE_LIMIT, message = "task.title.long")
    String title,

    @Size(max = TaskContent.DESCRIPTION_LIMIT, message = "task.description.long") String description,

    @Nullable TaskStatus status,

    @Nullable
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate dueOn,

    long version
) {

    /**
     * Stands the empty string in the place of a text the binder could not read, which it hands over as
     * nothing, and trims what was typed.
     */
    public TaskForm {
        title = Objects.requireNonNullElse(title, "").strip();
        description = Objects.requireNonNullElse(description, "").strip();
    }

    /**
     * Answers the form the page for writing a task opens with, which is due on the given day and open.
     *
     * @param today the day the form is opened on
     * @return an empty form
     */
    public static TaskForm blank(LocalDate today) {
        return new TaskForm("", "", TaskStatus.OPEN, today, 0);
    }

    /**
     * Answers what the form that changes the given task opens with.
     *
     * @param task the task as a page shows it
     * @return everything that task says, and the version it is at
     */
    public static TaskForm of(TaskView task) {
        TaskContent content = task.content();
        return new TaskForm(content.title(), content.description(), content.status(), content.dueOn(), task.version());
    }

    /**
     * Answers every required control of this form that was left alone, under the name of that control.
     *
     * @return one message per control that holds nothing, and nothing where each holds something
     */
    public Map<String, Wording> missing() {
        Map<String, Wording> unchosen = Optional.ofNullable(this.status).isEmpty()
            ? Map.of("status", Wording.of("task.status.unchosen"))
            : Map.of();
        Map<String, Wording> undated = Optional.ofNullable(this.dueOn).isEmpty()
            ? Map.of("dueOn", Wording.of("task.dueOn.blank"))
            : Map.of();
        return Stream.of(unchosen, undated)
            .flatMap(refusal -> refusal.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Answers what the task says, as this form states it.
     *
     * @return the content of the task
     * @throws NullPointerException when a value the rules require was never given
     */
    public TaskContent content() {
        return new TaskContent(
            this.title,
            this.description,
            Objects.requireNonNull(this.status, "A task stands somewhere"),
            Objects.requireNonNull(this.dueOn, "A task is due on some day")
        );
    }
}
