package com.example.starter.failure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * A failure the application reports to the reader. It carries the status of the response, the name of
 * the wording that explains it, and, for a failure of the application itself, the reference its report
 * was logged under. Nothing the exception said reaches the reader: the reference is what connects the
 * page they were shown to the cause an operator reads in the log.
 *
 * @param status    the HTTP status the response carries
 * @param key       the name the wording of the failure is written under
 * @param reference the reference the report of the failure was logged under, for a failure of the
 *                  application itself, and nothing for a refusal of the reader's request
 */
public record Failure(int status, String key, Optional<String> reference) {

    /**
     * Answers the failure that stands for the given status. A failure of the application is given a
     * fresh reference. A refusal of the request is the reader's answer and carries none.
     *
     * @param status the status the request ended with
     * @return the failure the page reports
     */
    public static Failure of(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return new Failure(status.value(), "failure.broken", Optional.of(UUID.randomUUID().toString()));
        }
        String key = switch (HttpStatus.resolve(status.value())) {
            case FORBIDDEN -> "failure.forbidden";
            case NOT_FOUND -> "failure.missing";
            case null, default -> "failure.refused";
        };
        return new Failure(status.value(), key, Optional.empty());
    }
}
