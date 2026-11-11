package com.example.starter.failure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.identity.UserRepository;
import eu.ciechanowiec.airness.spring.ExpectedServerError;
import java.net.http.HttpResponse;
import org.apache.catalina.core.ContainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@DatabaseTest
class FailurePagesTest {

    private final UserRepository repository;

    FailurePagesTest(UserRepository repository) {
        this.repository = repository;
    }

    @Test
    void answersAnAddressNobodyMapsAsMissing(@LocalServerPort int port) {
        HttpResponse<String> answer = Caller.administrator(port).get("/nowhere");
        assertAll(
            () -> assertEquals(HttpStatus.NOT_FOUND.value(), answer.statusCode()),
            () -> assertTrue(answer.body().contains("Nothing is to be found at this address."))
        );
    }

    @Test
    void answersARequestForTheFailurePageItselfAsMissing(@LocalServerPort int port) {
        assertEquals(HttpStatus.NOT_FOUND.value(), new Caller(port).get("/error").statusCode());
    }

    @Test
    void answersAPostWithoutATokenAsForbidden(@LocalServerPort int port) {
        HttpResponse<String> answer = new Caller(port).forged("/language", "language=POLISH&back=/");
        assertAll(
            () -> assertEquals(HttpStatus.FORBIDDEN.value(), answer.statusCode()),
            () -> assertTrue(answer.body().contains("This is not yours to open."))
        );
    }

    @Test
    @ExpectedServerError(
        logger = ContainerBase.class,
        reason = "the servlet container reports the fault before it hands the request to the fault page"
    )
    @ExpectedServerError(
        logger = FailurePages.class,
        reason = "the fault page logs the refusal under the reference it hands the reader"
    )
    void answersAFaultWithTheReferenceItWasReportedUnder(@LocalServerPort int port) {
        // The application layer refuses to stop the last administrator, which the page never offers, so a
        // request made anyway ends in the refusal itself, a fault the reader is given a reference for.
        String administrator = this.repository.findByProfileEmail(Caller.ADMINISTRATOR)
            .orElseThrow()
            .getReference()
            .toString();
        HttpResponse<String> answer = Caller.administrator(port)
            .post("/users/%s/access".formatted(administrator), "allowed=false");
        assertAll(
            () -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), answer.statusCode()),
            () -> assertTrue(answer.body().contains("when you report it")),
            () -> assertFalse(answer.body().contains("IllegalStateException"))
        );
    }
}
