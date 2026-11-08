package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void namesTheKeyItsWordingIsWrittenUnder() {
        assertEquals("identity.role.user", Role.USER.key());
    }
}
