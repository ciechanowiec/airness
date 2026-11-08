package com.example.starter.identity;

import com.example.starter.web.Control;
import com.example.starter.web.Notice;
import com.example.starter.web.Refusals;
import com.example.starter.web.Wording;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * The pages that show and change the accounts of the application. Nothing here decides who may do what:
 * every method of the application layer this holds is an administrator's, so a reader who is not one is
 * refused by the same rule whether the navigation offered them a way here or not.
 *
 * <p>Two rules are stated here as well as on the application layer: that an address is already taken,
 * and that the account being changed is the last administrator who may sign in. Both are refused by the
 * application layer, which is the last word, and this turns each into a message against its control.
 */
@Controller
@RequestMapping(UserPages.USERS)
public final class UserPages {

    static final String USERS = "/users";

    private static final String LIST = "identity/users";

    private static final String FORM = "identity/user";

    private static final String BOUND_FORM = "userForm";

    private static final String REFUSALS = "refusals";

    private static final String REFERENCE = "reference";

    private static final String EMAIL = "profile.email";

    private static final String ROLE = "role";

    private static final String NO_AUTOFILL = "off";

    private static final Control CALLED = new Control("user.control.fullName", "profile.fullName", "text", NO_AUTOFILL);

    private static final Control SIGNS_IN_AS = new Control("user.control.email", EMAIL, "email", NO_AUTOFILL);

    private static final Control DOES = new Control("user.control.role", ROLE, "select", NO_AUTOFILL);

    private static final Control PROVES_IT = new Control(
        "user.control.password", UserForm.PASSWORD, "password", "new-password"
    );

    private final Users users;

    private final Roster roster;

    private final Refusals refusals;

    /**
     * Shows the accounts through the given reads and changes them through the given application layer.
     *
     * @param users    the accounts of the application, and what may be done to one
     * @param roster   every account, as the pages read it
     * @param refusals everything wrong with a submitted form, under the name of its control
     */
    public UserPages(Users users, Roster roster, Refusals refusals) {
        this.users = users;
        this.roster = roster;
        this.refusals = refusals;
    }

    /**
     * Answers the controls the account form is made of.
     *
     * @return each control of the form, under the name the template asks for it by
     */
    @ModelAttribute("controls")
    public Map<String, Control> controls() {
        return Map.of("fullName", CALLED, "email", SIGNS_IN_AS, "role", DOES, "password", PROVES_IT);
    }

    /**
     * Answers no refusal against any control, so a template never asks whether the reader was refused.
     *
     * @return no refusal at all
     */
    @ModelAttribute(REFUSALS)
    public Map<String, String> nothingRefused() {
        return Map.of();
    }

    /**
     * Answers one page of every account.
     *
     * @param page  which page of the list to show, counted from one
     * @param model what the template is given
     * @return the name of the view that renders the accounts
     */
    @GetMapping
    public String list(@RequestParam(name = "page", defaultValue = "1") int page, Model model) {
        model.addAttribute("people", this.roster.people(page));
        return LIST;
    }

    /**
     * Answers the form that admits an account, holding nothing.
     *
     * @param model what the template is given
     * @return the name of the view that renders the form
     */
    @GetMapping("/new")
    public String blank(Model model) {
        model.addAttribute(BOUND_FORM, UserForm.blank());
        this.admitting(model);
        return FORM;
    }

    /**
     * Admits the account an administrator stated, or answers the form again with what is wrong on it.
     *
     * @param userForm what the administrator stated about the account
     * @param errors   what the binder could not read
     * @param model    what the template is given
     * @return the accounts, or the form again
     */
    @PostMapping
    public String create(
        @ModelAttribute(name = BOUND_FORM, binding = true)
        @Valid
        UserForm userForm,
        BindingResult errors,
        Model model
    ) {
        Map<String, Wording> known = merged(
            List.of(userForm.missing(), userForm.unchosen(), taken(this.roster.taken(userForm.profile().email())))
        );
        Map<String, String> wrong = this.refusals.of(userForm, errors, known);
        if (wrong.isEmpty()) {
            this.users.register(userForm);
            return Notice.CREATED.redirect(USERS);
        }
        refused(model, userForm, wrong);
        this.admitting(model);
        return FORM;
    }

    /**
     * Answers the form that restates an account, holding what it states today and no password.
     *
     * @param reference the reference the account is known by outside the database
     * @param model     what the template is given
     * @return the name of the view that renders the form
     * @throws ResponseStatusException when nobody carries that reference
     */
    @GetMapping("/{reference}/edit")
    public String edit(@PathVariable(name = REFERENCE, required = true) UUID reference, Model model) {
        UserView view = this.roster.person(reference).orElseThrow(UserPages::unknown);
        model.addAttribute(BOUND_FORM, view.described());
        restating(model, reference, view.sole());
        return FORM;
    }

    /**
     * Restates the account as the administrator now describes it, or answers the form again with what is
     * wrong on it.
     *
     * @param reference the reference the account is known by outside the database
     * @param userForm  what the administrator stated about the account
     * @param errors    what the binder could not read
     * @param model     what the template is given
     * @return the accounts, or the form again
     */
    @PostMapping("/{reference}")
    public String update(
        @PathVariable(name = REFERENCE, required = true) UUID reference,
        @ModelAttribute(name = BOUND_FORM, binding = true)
        @Valid
        UserForm userForm,
        BindingResult errors,
        Model model
    ) {
        boolean sole = this.roster.person(reference).orElseThrow(UserPages::unknown).sole();
        Map<String, Wording> known = merged(
            List.of(
                userForm.missing(),
                taken(this.roster.takenByAnother(userForm.profile().email(), reference)),
                stranded(sole && userForm.role() != Role.ADMIN)
            )
        );
        Map<String, String> wrong = this.refusals.of(userForm, errors, known);
        if (wrong.isEmpty()) {
            this.users.restate(reference, userForm);
            return Notice.SAVED.redirect(USERS);
        }
        refused(model, userForm, wrong);
        restating(model, reference, sole);
        return FORM;
    }

    /**
     * Lets the account sign in or stops it.
     *
     * @param reference the reference the account is known by outside the database
     * @param allowed   whether the account may sign in at all
     * @return the accounts, with that one under its new standing
     */
    @PostMapping("/{reference}/access")
    public String access(
        @PathVariable(name = REFERENCE, required = true) UUID reference,
        @RequestParam(name = "allowed", required = true) boolean allowed
    ) {
        this.users.enabling(reference, allowed);
        return (allowed ? Notice.ENABLED : Notice.DISABLED).redirect(USERS);
    }

    private static Map<String, Wording> merged(List<Map<String, Wording>> known) {
        return known.stream()
            .flatMap(refusals -> refusals.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first));
    }

    private static Map<String, Wording> taken(boolean already) {
        return already ? Map.of(EMAIL, Wording.of("user.email.taken")) : Map.of();
    }

    private static Map<String, Wording> stranded(boolean last) {
        return last ? Map.of(ROLE, Wording.of("user.role.soleAdministrator")) : Map.of();
    }

    private static void refused(Model model, UserForm userForm, Map<String, String> wrong) {
        model.addAttribute(BOUND_FORM, userForm);
        model.addAttribute(REFUSALS, wrong);
    }

    private void admitting(Model model) {
        model.addAttribute("target", USERS);
        model.addAttribute("heading", "user.heading.adding");
        model.addAttribute("submit", "user.submit.add");
        model.addAttribute("admitting", true);
        model.addAttribute("sole", false);
        model.addAttribute("roles", this.roster.roles());
    }

    // The last administrator who may sign in is offered the role they hold and nothing else, because
    // giving them another is a change the application layer refuses.
    private void restating(Model model, UUID reference, boolean sole) {
        model.addAttribute("target", "%s/%s".formatted(USERS, reference));
        model.addAttribute("heading", "user.heading.editing");
        model.addAttribute("submit", "user.submit.save");
        model.addAttribute("admitting", false);
        model.addAttribute("sole", sole);
        model.addAttribute("roles", this.roster.roles().stream().filter(role -> !sole || role == Role.ADMIN).toList());
    }

    private static ResponseStatusException unknown() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
