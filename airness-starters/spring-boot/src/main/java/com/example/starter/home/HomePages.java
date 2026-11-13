package com.example.starter.home;

import com.example.starter.identity.Reader;
import com.example.starter.task.TaskGlances;
import com.example.starter.task.Tasks;
import com.example.starter.web.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The page the application opens on. Somebody who has not signed in is told what the application is and
 * where to sign in. Somebody who has is shown how their tasks stand, each count leading to the list of
 * those tasks.
 */
@Controller
public final class HomePages {

    private static final String INTRODUCTION = "home/introduction";

    private static final String DASHBOARD = "home/dashboard";

    private final Tasks tasks;

    private final TaskGlances glances;

    /**
     * Counts the reader's tasks through the given application layer.
     *
     * @param tasks   the tasks, and what may be done to one
     * @param glances what needs attention, what falls due this week and what each person keeps
     */
    public HomePages(Tasks tasks, TaskGlances glances) {
        this.tasks = tasks;
        this.glances = glances;
    }

    /**
     * Answers the introduction or, for somebody signed in, the dashboard.
     *
     * @param model what the template is given
     * @return the name of the view that renders the page
     */
    @GetMapping(PageModel.HOME)
    public String home(Model model) {
        if (Reader.email().isEmpty()) {
            return INTRODUCTION;
        }
        model.addAttribute("overview", this.tasks.overview());
        model.addAttribute("attention", this.glances.attention());
        model.addAttribute("week", this.glances.week());
        model.addAttribute("load", this.glances.load());
        return DASHBOARD;
    }
}
