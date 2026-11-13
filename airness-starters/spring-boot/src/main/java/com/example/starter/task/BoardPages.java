package com.example.starter.task;

import com.example.starter.web.Htmx;
import com.example.starter.web.Notice;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * The board: the tasks the reader may see, one column for each status, and the moves made on it.
 *
 * <p>A move made on the board is answered with the board, which htmx swaps in place of the old one, or,
 * for a reader without script, with the board again as a page.
 */
@Controller
@RequestMapping(BoardPages.BOARD)
public final class BoardPages {

    /**
     * The address of the board.
     */
    static final String BOARD = "/tasks/board";

    private static final String VIEW = "tasks/board";

    private static final String NOTHING = "";

    private final Tasks tasks;

    private final TaskGlances glances;

    /**
     * Draws and changes the board through the given application layer.
     *
     * @param tasks   the tasks, and what may be done to one
     * @param glances the board the tasks are laid out on
     */
    public BoardPages(Tasks tasks, TaskGlances glances) {
        this.tasks = tasks;
        this.glances = glances;
    }

    /**
     * Answers every status, in the order the columns stand and a card's menu offers them.
     *
     * @return the statuses
     */
    @ModelAttribute("statuses")
    public List<TaskStatus> statuses() {
        return List.of(TaskStatus.values());
    }

    /**
     * Answers the board.
     *
     * @param model what the template is given
     * @return the name of the view that renders the board
     */
    @GetMapping
    public String board(Model model) {
        model.addAttribute("board", this.glances.board());
        return VIEW;
    }

    /**
     * Moves a task to another column, answering htmx with the board alone and anybody else with the board
     * again.
     *
     * @param reference the reference the task is known by outside the database
     * @param status    the name of the status the task moves to
     * @param header    what htmx said about this request, which is empty when the caller is not htmx
     * @param model     what the template is given
     * @return the board, or a redirect to it
     * @throws ResponseStatusException when the reader may not see a task under that reference, or the
     *                                 status names none
     */
    @PostMapping("/{reference}/status")
    public String move(
        @PathVariable(name = "reference", required = true) UUID reference,
        @RequestParam(name = "status", required = true) String status,
        @RequestHeader(name = Htmx.HEADER, defaultValue = NOTHING) String header,
        Model model
    ) {
        if (this.tasks.task(reference).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        TaskStatus moved = TaskStatus.named(status)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        this.tasks.move(reference, moved);
        if (new Htmx(header, NOTHING).partial()) {
            model.addAttribute("board", this.glances.board());
            return VIEW + " :: columns";
        }
        return Notice.SAVED.redirect(BOARD);
    }
}
