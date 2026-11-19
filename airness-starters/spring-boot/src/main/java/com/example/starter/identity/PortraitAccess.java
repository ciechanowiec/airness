package com.example.starter.identity;

import com.example.starter.media.MediaAccess;
import com.example.starter.media.MediaObject;
import com.example.starter.media.MediaPurpose;
import org.springframework.stereotype.Component;

/**
 * Lets anybody signed in see a portrait, since every page that names a person draws them by it. Nobody else
 * reaches a stored file at all.
 */
@Component
public final class PortraitAccess implements MediaAccess {

    @Override
    public boolean permits(MediaObject media) {
        return media.getPurpose() == MediaPurpose.PORTRAIT && Reader.email().isPresent();
    }
}
