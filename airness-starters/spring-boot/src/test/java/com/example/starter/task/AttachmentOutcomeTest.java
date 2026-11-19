package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.starter.media.MediaRefusedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AttachmentOutcomeTest {

    @Test
    void saysWhyTheLibraryRefusedAFile() {
        assertAll(
            () -> assertEquals(AttachmentOutcome.EMPTY, AttachmentOutcome.of(MediaRefusedException.Reason.EMPTY)),
            () -> assertEquals(
                AttachmentOutcome.TOO_LARGE, AttachmentOutcome.of(MediaRefusedException.Reason.TOO_LARGE)
            ),
            () -> assertEquals(
                AttachmentOutcome.UNSUPPORTED, AttachmentOutcome.of(MediaRefusedException.Reason.UNSUPPORTED)
            )
        );
    }

    @Test
    void readsARefusalOutOfAnAddressByItsNameAlone() {
        assertAll(
            () -> assertEquals(Optional.of(AttachmentOutcome.TOO_MANY), AttachmentOutcome.named("TOO_MANY")),
            () -> assertEquals(Optional.empty(), AttachmentOutcome.named("anything else")),
            () -> assertEquals("attachments.refused.kind", AttachmentOutcome.UNSUPPORTED.key())
        );
    }
}
