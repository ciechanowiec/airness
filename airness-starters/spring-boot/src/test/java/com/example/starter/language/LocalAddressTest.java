package com.example.starter.language;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LocalAddressTest {

    @Test
    void keepsAPathOfThisApplication() {
        assertEquals("/tasks?status=OPEN", LocalAddress.of("/tasks?status=OPEN").path());
    }

    @Test
    void refusesAnAddressNamingAnotherHostByItsScheme() {
        assertEquals("/", LocalAddress.of("https://elsewhere.test/").path());
    }

    @Test
    void refusesAnAddressNamingAnotherHostByTwoSlashes() {
        assertEquals("/", LocalAddress.of("//elsewhere.test/").path());
    }

    @Test
    void refusesAnAddressABrowserReadsAsAnotherHostByABackslash() {
        assertEquals("/", LocalAddress.of("/\\elsewhere.test/").path());
    }
}
