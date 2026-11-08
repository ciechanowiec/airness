package com.example.starter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class NoticeTest {

    @Test
    void readsANoticeBackFromItsName() {
        assertEquals(Optional.of(Notice.SAVED), Notice.named("SAVED"));
    }

    @Test
    void readsNoNoticeFromANameNoneCarries() {
        assertTrue(Notice.named("ANYTHING").isEmpty());
    }

    @Test
    void redirectsToThePathCarryingItsName() {
        assertEquals("redirect:/tasks?notice=DELETED", Notice.DELETED.redirect("/tasks"));
    }

    @Test
    void namesTheKeyItsWordingIsWrittenUnder() {
        assertEquals("notice.passwordChanged", Notice.PASSWORD_CHANGED.key());
    }
}
