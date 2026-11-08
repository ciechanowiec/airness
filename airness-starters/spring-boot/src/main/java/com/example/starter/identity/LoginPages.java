package com.example.starter.identity;

import com.example.starter.web.Control;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The page somebody signs in on. The form is posted to the same address and answered by the framework
 * rather than by a handler here, so nothing here decides whether a password is right.
 */
@Controller
@RequestMapping(LoginPages.LOGIN)
public final class LoginPages {

    /**
     * Where somebody signs in, which the filter chain leaves open and points a refused reader back to.
     */
    static final String LOGIN = "/login";

    private static final String PAGE = "identity/login";

    private static final String FAILED = "failed";

    private static final String ENDED = "ended";

    private static final Control SIGNS_IN_AS = new Control("login.control.email", "username", "email", "username");

    private static final Control PROVES_IT = new Control(
        "login.control.password", "password", "password", "current-password"
    );

    /**
     * Answers the page somebody signs in on.
     *
     * @param failed whether the last attempt was refused, which the chain says by sending the reader back
     *               here with the parameter set
     * @param ended  whether the reader's session was ended because their account changed
     * @param model  what the template is given
     * @return the name of the view that renders the form
     */
    @GetMapping
    public String page(
        @RequestParam(name = FAILED, defaultValue = "false") boolean failed,
        @RequestParam(name = ENDED, defaultValue = "false") boolean ended,
        Model model
    ) {
        model.addAttribute(FAILED, failed);
        model.addAttribute(ENDED, ended);
        model.addAttribute("address", SIGNS_IN_AS);
        model.addAttribute("secret", PROVES_IT);
        return PAGE;
    }
}
