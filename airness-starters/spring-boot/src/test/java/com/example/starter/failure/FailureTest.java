package com.example.starter.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

class FailureTest {

    // A status that falls in the range of client errors and that no name of the protocol stands for.
    private static final int UNNAMED = 499;

    @Test
    void givesAFaultOfTheApplicationAReference() {
        assertTrue(Failure.of(HttpStatus.INTERNAL_SERVER_ERROR).reference().isPresent());
    }

    @Test
    void wordsAFaultOfTheApplicationAsOne() {
        assertEquals("failure.broken", Failure.of(HttpStatus.SERVICE_UNAVAILABLE).key());
    }

    @Test
    void givesARefusalNoReference() {
        assertTrue(Failure.of(HttpStatus.NOT_FOUND).reference().isEmpty());
    }

    @Test
    void wordsAMissingPageAsOne() {
        assertEquals("failure.missing", Failure.of(HttpStatus.NOT_FOUND).key());
    }

    @Test
    void wordsAForbiddenPageAsOne() {
        assertEquals("failure.forbidden", Failure.of(HttpStatus.FORBIDDEN).key());
    }

    @Test
    void wordsAnyOtherRefusalAsARefusal() {
        assertEquals("failure.refused", Failure.of(HttpStatus.BAD_REQUEST).key());
    }

    @Test
    void wordsAStatusNoNameStandsForAsARefusal() {
        assertEquals("failure.refused", Failure.of(HttpStatusCode.valueOf(UNNAMED)).key());
    }
}
