package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.starter.media.MediaRefusedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortraitRefusalTest {

    @Test
    void saysWhyTheLibraryRefusedAPicture() {
        assertAll(
            () -> assertEquals(PortraitRefusal.EMPTY, PortraitRefusal.of(MediaRefusedException.Reason.EMPTY)),
            () -> assertEquals(PortraitRefusal.TOO_LARGE, PortraitRefusal.of(MediaRefusedException.Reason.TOO_LARGE)),
            () -> assertEquals(
                PortraitRefusal.UNSUPPORTED, PortraitRefusal.of(MediaRefusedException.Reason.UNSUPPORTED)
            )
        );
    }

    @Test
    void readsARefusalOutOfAnAddressByItsNameAlone() {
        assertAll(
            () -> assertEquals(Optional.of(PortraitRefusal.EMPTY), PortraitRefusal.named("EMPTY")),
            () -> assertEquals(Optional.empty(), PortraitRefusal.named("anything else")),
            () -> assertEquals("account.portrait.refused.kind", PortraitRefusal.UNSUPPORTED.key())
        );
    }
}
