package com.example.starter.failure;

import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

/**
 * What a reader is shown when a request could not be served, whatever ended it: a page nobody maps, a
 * record the reader may not see, a refusal by the application layer, or a fault of the application.
 * Every one of them reaches the container's error dispatch, which this answers, so one page and one log
 * line describe every failure.
 *
 * <p>The error dispatch keeps the method of the request that failed, so the page answers both methods the
 * application maps. A fault of the application is logged with its cause under a fresh reference, and the
 * page shows the reference and nothing the exception said.
 */
@Slf4j
@Controller
public final class FailurePages implements ErrorController {

    private static final String VIEW = "failure/page";

    private static final String REPORT = "The request failed with status {} and was reported under {}";

    /**
     * Answers the page for the failure the container dispatched here. A request made here directly has no
     * failure behind it and is answered as an address nobody maps.
     *
     * @param code  the status the request ended with, which a request made here directly carries none of
     * @param cause what ended the request, where it was an exception that reached the container
     * @return the failure page, carrying the status of the failure
     */
    @RequestMapping(path = "/error", method = {RequestMethod.GET, RequestMethod.POST})
    public ModelAndView page(
        @RequestAttribute(name = RequestDispatcher.ERROR_STATUS_CODE, required = false) @Nullable Integer code,
        @RequestAttribute(name = RequestDispatcher.ERROR_EXCEPTION, required = false) @Nullable Throwable cause
    ) {
        HttpStatusCode status = Optional.ofNullable(code).map(HttpStatusCode::valueOf).orElse(HttpStatus.NOT_FOUND);
        Failure failure = Failure.of(status);
        failure.reference()
            .ifPresent(reference -> log.atError().setCause(cause).log(REPORT, failure.status(), reference));
        return new ModelAndView(VIEW, Map.of("failure", failure), status);
    }
}
