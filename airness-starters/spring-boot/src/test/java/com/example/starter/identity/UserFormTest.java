package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.web.Wording;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserFormTest {

    @Test
    void saysARoleNobodyChoseIsMissing() {
        assertEquals(Map.of("role", Wording.of("user.role.unchosen")), UserForm.blank().missing());
    }

    @Test
    void saysNothingIsMissingOnceARoleIsChosen() {
        assertTrue(new UserForm(UserProfile.unnamed(), Role.USER, "").missing().isEmpty());
    }

    @Test
    void holdsItsPasswordToThePolicy() {
        assertEquals(Map.of("password", Wording.of("password.blank")), UserForm.blank().unchosen());
    }

    @Test
    void standsAnEmptyFormInThePlaceOfNothing() {
        assertEquals(new UserForm(null, null, null), UserForm.blank());
    }

    @Test
    void refusesToStateAnAccountWithoutARole() {
        UserForm form = UserForm.blank();
        Instant now = Instant.now();
        assertThrows(NullPointerException.class, () -> form.stated("hash", now));
    }

    @Test
    void statesAnEnabledAccount() {
        UserForm form = new UserForm(new UserProfile("ada@starter.test", "Ada"), Role.ADMIN, "");
        assertTrue(form.stated("hash", Instant.now()).getAccess().enabled());
    }
}
