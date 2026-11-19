package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.media.MediaFile;
import com.example.starter.media.MediaKind;
import com.example.starter.media.MediaObject;
import com.example.starter.media.MediaPurpose;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class PortraitAccessTest {

    private static MediaObject stored(MediaPurpose purpose) {
        return new MediaObject(
            new MediaFile("me.png", MediaKind.IMAGE, "image/png", 1, "ab"), purpose, 1, Instant.EPOCH
        );
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void showsAPortraitToAnybodySignedIn() {
        Caller.acting(Caller.ADMINISTRATOR, Role.USER.getAuthority());
        assertAll(
            () -> assertTrue(new PortraitAccess().permits(stored(MediaPurpose.PORTRAIT))),
            () -> assertFalse(new PortraitAccess().permits(stored(MediaPurpose.ATTACHMENT)))
        );
    }

    @Test
    void showsAPortraitToNobodyWhoHasNotSignedIn() {
        assertFalse(new PortraitAccess().permits(stored(MediaPurpose.PORTRAIT)));
    }
}
