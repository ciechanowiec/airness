package com.example.starter.identity;

import com.example.starter.web.Control;
import com.example.starter.web.Notice;
import com.example.starter.web.Refusals;
import com.example.starter.web.Wording;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The page a reader changes their own password on. It carries no reference: what it changes belongs to
 * whoever asked for it, so there is no value a caller could send that would name somebody else.
 */
@Controller
@RequestMapping(AccountPages.ACCOUNT)
public final class AccountPages {

    static final String ACCOUNT = "/account";

    private static final String PAGE = "identity/account";

    private static final String BOUND_FORM = "passwordForm";

    private static final String REFUSALS = "refusals";

    private static final Control CURRENT = new Control(
        "account.control.current", PasswordForm.CURRENT, "password", "current-password"
    );

    private static final Control CHOSEN = new Control(
        "account.control.chosen", PasswordForm.CHOSEN, "password", "new-password"
    );

    private final Users users;

    private final Refusals refusals;

    /**
     * Changes the reader's password through the given application layer.
     *
     * @param users    the accounts of the application
     * @param refusals everything wrong with a submitted form, under the name of its control
     */
    public AccountPages(Users users, Refusals refusals) {
        this.users = users;
        this.refusals = refusals;
    }

    /**
     * Answers the controls the password form is made of.
     *
     * @return each control of the form, under the name the template asks for it by
     */
    @ModelAttribute("controls")
    public Map<String, Control> controls() {
        return Map.of(PasswordForm.CURRENT, CURRENT, PasswordForm.CHOSEN, CHOSEN);
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
     * Answers the page the reader changes their password on.
     *
     * @return the name of the view that renders the page
     */
    @GetMapping
    public String page() {
        return PAGE;
    }

    /**
     * Changes the reader's password, or answers the page again with what is wrong on it. The page is
     * never drawn holding either password, because a password written back into a page is one in a
     * response, in a cache and in whatever the browser does with a form it has seen.
     *
     * @param passwordForm what the reader typed
     * @param errors       what the binder could not read
     * @param model        what the template is given
     * @return the page again, saying the password was changed, or the page with what is wrong on it
     */
    @PostMapping("/password")
    public String change(
        @ModelAttribute(name = BOUND_FORM, binding = true)
        @Valid
        PasswordForm passwordForm,
        BindingResult errors,
        Model model
    ) {
        Map<String, Wording> proof = this.users.proves(passwordForm.current())
            ? Map.of()
            : Map.of(PasswordForm.CURRENT, Wording.of("account.current.wrong"));
        Map<String, Wording> known = Stream.of(passwordForm.unchosen(), proof)
            .flatMap(refusal -> refusal.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, String> wrong = this.refusals.of(passwordForm, errors, known);
        if (wrong.isEmpty()) {
            this.users.changePassword(passwordForm.current(), passwordForm.chosen());
            return Notice.PASSWORD_CHANGED.redirect(ACCOUNT);
        }
        model.addAttribute(REFUSALS, wrong);
        return PAGE;
    }
}
