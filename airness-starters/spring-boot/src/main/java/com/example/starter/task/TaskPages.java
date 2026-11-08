package com.example.starter.task;

import com.example.starter.web.Control;
import com.example.starter.web.Htmx;
import com.example.starter.web.Notice;
import com.example.starter.web.Refusals;
import com.example.starter.web.Wording;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * The pages that show and change the tasks. The controller holds the application layer and never a
 * repository, and nothing here decides who may see what: a task the reader may not see is answered as
 * one that does not exist.
 *
 * <p>The list answers htmx with its results alone, so searching and narrowing replace the table in
 * place, and the same address answers a whole page where htmx is not running. A change of status made
 * from the list answers htmx with the changed row alone.
 */
@Controller
@RequestMapping(TaskPages.TASKS)
public final class TaskPages {

    static final String TASKS = "/tasks";

    private static final String LIST = "tasks/list";

    private static final String FORM = "tasks/form";

    private static final String ROW = "tasks/row";

    private static final String BOUND_FORM = "taskForm";

    private static final String REFUSALS = "refusals";

    private static final String REFERENCE = "reference";

    private static final String NOTHING = "";

    private static final String NO_AUTOFILL = "off";

    private static final Control TITLE = new Control("task.control.title", "title", "text", NO_AUTOFILL);

    private static final Control DESCRIPTION = new Control(
        "task.control.description", "description", "prose", NO_AUTOFILL
    );

    private static final Control STATUS = new Control("task.control.status", "status", "select", NO_AUTOFILL);

    private static final Control DUE_ON = new Control("task.control.dueOn", "dueOn", "date", NO_AUTOFILL);

    private static final Wording CHANGED_MEANWHILE = Wording.of("task.changedMeanwhile");

    private final Tasks tasks;

    private final Refusals refusals;

    private final Clock clock;

    /**
     * Shows and changes the tasks through the given application layer.
     *
     * @param tasks    the tasks, and what may be done to one
     * @param refusals everything wrong with a submitted form, under the name of its control
     * @param clock    the clock the day a new task opens on is read from
     */
    public TaskPages(Tasks tasks, Refusals refusals, Clock clock) {
        this.tasks = tasks;
        this.refusals = refusals;
        this.clock = clock;
    }

    /**
     * Answers the controls the task form is made of.
     *
     * @return each control of the form, under the name the template asks for it by
     */
    @ModelAttribute("controls")
    public Map<String, Control> controls() {
        return Map.of("title", TITLE, "description", DESCRIPTION, "status", STATUS, "dueOn", DUE_ON);
    }

    /**
     * Answers no refusal against any control.
     *
     * @return no refusal at all
     */
    @ModelAttribute(REFUSALS)
    public Map<String, String> nothingRefused() {
        return Map.of();
    }

    /**
     * Answers the statuses a task can stand at, which the form and the list both offer.
     *
     * @return every status
     */
    @ModelAttribute("statuses")
    public List<TaskStatus> statuses() {
        return List.of(TaskStatus.values());
    }

    /**
     * Answers the tasks the reader may see, narrowed as the address says, as a whole page or, for htmx,
     * as the results alone.
     *
     * @param filter what the reader narrowed the list to
     * @param page   which page of the list to show, counted from one
     * @param header what htmx said about this request, which is empty when the caller is not htmx
     * @param model  what the template is given
     * @return the name of the view that renders the tasks
     */
    @GetMapping
    public String list(
        @ModelAttribute(name = "filter", binding = true)
        @Valid
        TaskFilter filter,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestHeader(name = Htmx.HEADER, defaultValue = NOTHING) String header,
        Model model
    ) {
        model.addAttribute("listing", this.tasks.listing(filter, page));
        model.addAttribute("sorts", List.of(TaskSort.values()));
        return new Htmx(header).view(LIST, "results");
    }

    /**
     * Answers the form that writes a task, due today and open.
     *
     * @param model what the template is given
     * @return the name of the view that renders the form
     */
    @GetMapping("/new")
    public String blank(Model model) {
        model.addAttribute(BOUND_FORM, TaskForm.blank(LocalDate.now(this.clock)));
        writing(model);
        return FORM;
    }

    /**
     * Writes the task the reader stated, or answers the form again with what is wrong on it.
     *
     * @param taskForm what the reader stated about the task
     * @param errors   what the binder could not read
     * @param model    what the template is given
     * @return the tasks, or the form again
     */
    @PostMapping
    public String create(
        @ModelAttribute(name = BOUND_FORM, binding = true)
        @Valid
        TaskForm taskForm,
        BindingResult errors,
        Model model
    ) {
        Map<String, String> wrong = this.refusals.of(taskForm, errors, taskForm.missing());
        if (wrong.isEmpty()) {
            this.tasks.create(taskForm.content());
            return Notice.CREATED.redirect(TASKS);
        }
        refused(model, taskForm, wrong);
        writing(model);
        return FORM;
    }

    /**
     * Answers the form that changes a task, holding what it says today.
     *
     * @param reference the reference the task is known by outside the database
     * @param model     what the template is given
     * @return the name of the view that renders the form
     * @throws ResponseStatusException when the reader may not see a task under that reference
     */
    @GetMapping("/{reference}/edit")
    public String edit(@PathVariable(name = REFERENCE, required = true) UUID reference, Model model) {
        TaskView task = this.tasks.task(reference).orElseThrow(TaskPages::unknown);
        model.addAttribute(BOUND_FORM, TaskForm.of(task));
        changing(model, task);
        return FORM;
    }

    /**
     * Changes the task as the reader now states it, or answers the form again with what is wrong on it,
     * which includes that somebody changed the task after the reader opened it.
     *
     * @param reference the reference the task is known by outside the database
     * @param taskForm  what the reader stated about the task
     * @param errors    what the binder could not read
     * @param model     what the template is given
     * @return the tasks, or the form again
     */
    @PostMapping("/{reference}")
    public String update(
        @PathVariable(name = REFERENCE, required = true) UUID reference,
        @ModelAttribute(name = BOUND_FORM, binding = true)
        @Valid
        TaskForm taskForm,
        BindingResult errors,
        Model model
    ) {
        TaskView task = this.tasks.task(reference).orElseThrow(TaskPages::unknown);
        Map<String, Wording> stale = this.tasks.current(reference, taskForm.version())
            ? Map.of()
            : Map.of("version", CHANGED_MEANWHILE);
        Map<String, Wording> known = Stream.of(taskForm.missing(), stale)
            .flatMap(refusal -> refusal.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, String> wrong = this.refusals.of(taskForm, errors, known);
        if (wrong.isEmpty()) {
            this.tasks.revise(reference, taskForm.content(), taskForm.version());
            return Notice.SAVED.redirect(TASKS);
        }
        refused(model, taskForm, wrong);
        changing(model, task);
        return FORM;
    }

    /**
     * Moves a task to another status from the list, answering htmx with the changed row alone and
     * anybody else with the list again.
     *
     * @param reference the reference the task is known by outside the database
     * @param status    the name of the status the task now stands at
     * @param header    what htmx said about this request, which is empty when the caller is not htmx
     * @param model     what the template is given
     * @return the changed row, or the list
     * @throws ResponseStatusException when the reader may not see a task under that reference, or the
     *                                 status names none
     */
    @PostMapping("/{reference}/status")
    public String move(
        @PathVariable(name = REFERENCE, required = true) UUID reference,
        @RequestParam(name = "status", required = true) String status,
        @RequestHeader(name = Htmx.HEADER, defaultValue = NOTHING) String header,
        Model model
    ) {
        this.seen(reference);
        TaskStatus moved = TaskStatus.named(status)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        model.addAttribute("task", this.tasks.move(reference, moved));
        return new Htmx(header).partial() ? ROW : Notice.SAVED.redirect(TASKS);
    }

    /**
     * Removes a task.
     *
     * @param reference the reference the task is known by outside the database
     * @return the tasks
     * @throws ResponseStatusException when the reader may not see a task under that reference
     */
    @PostMapping("/{reference}/delete")
    public String delete(@PathVariable(name = REFERENCE, required = true) UUID reference) {
        this.seen(reference);
        this.tasks.remove(reference);
        return Notice.DELETED.redirect(TASKS);
    }

    // A task the reader may not see is answered as one that does not exist, before anything is changed.
    private void seen(UUID reference) {
        if (this.tasks.task(reference).isEmpty()) {
            throw unknown();
        }
    }

    // What a page needs to draw a refused form again: the form itself, which the binder leaves out of the
    // model whenever it could not read one of the values, and what is wrong with it.
    private static void refused(Model model, TaskForm taskForm, Map<String, String> wrong) {
        model.addAttribute(BOUND_FORM, taskForm);
        model.addAttribute(REFUSALS, wrong);
    }

    private static void writing(Model model) {
        model.addAttribute("target", TASKS);
        model.addAttribute("heading", "task.heading.writing");
        model.addAttribute("submit", "task.submit.write");
        model.addAttribute("existing", false);
    }

    private static void changing(Model model, TaskView task) {
        model.addAttribute("target", "%s/%s".formatted(TASKS, task.reference()));
        model.addAttribute("heading", "task.heading.changing");
        model.addAttribute("submit", "task.submit.save");
        model.addAttribute("existing", true);
        model.addAttribute("task", task);
    }

    private static ResponseStatusException unknown() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
