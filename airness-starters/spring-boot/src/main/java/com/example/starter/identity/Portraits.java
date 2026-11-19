package com.example.starter.identity;

import com.example.starter.media.MediaKind;
import com.example.starter.media.MediaLibrary;
import com.example.starter.media.MediaObject;
import com.example.starter.media.MediaPurpose;
import com.example.starter.media.MediaRefusedException;
import com.example.starter.media.MediaView;
import com.example.starter.media.Upload;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The pictures the accounts are shown with. A portrait is stored and rendered like any other file, at the
 * small widths an avatar is drawn at, and a picture an account no longer shows is removed together with its
 * bytes. The portraits of a page are read in two queries whatever its length.
 */
@Component
public final class Portraits {

    private final PortraitRecords records;

    private final MediaLibrary library;

    /**
     * Reads and writes the portraits through the given rows and library.
     *
     * @param records the rows that give accounts their portraits
     * @param library the stored files
     */
    public Portraits(PortraitRecords records, MediaLibrary library) {
        this.records = records;
        this.library = library;
    }

    /**
     * Answers the portraits of the given accounts.
     *
     * @param users the identifiers of the accounts
     * @return each portrait as a page shows it, under the identifier of its account, for each account with one
     */
    public Map<Long, MediaView> of(Collection<Long> users) {
        Map<Long, Long> pictures = this.records.pictures(users);
        Map<Long, MediaView> drawn = this.library.views(pictures.values());
        return pictures.entrySet()
            .stream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, picture -> drawn.getOrDefault(picture.getValue(), MediaView.NONE))
            );
    }

    /**
     * Gives an account the given picture, removing the one it had. A PDF is refused, since a portrait is a
     * picture of somebody and not the first page of a document.
     *
     * @param user   the identifier of the account
     * @param upload the picture
     * @return why the picture was refused, or nothing where the account now shows it
     */
    public Optional<PortraitRefusal> change(long user, Upload upload) {
        try {
            MediaObject picture = this.library.store(upload, user, MediaPurpose.PORTRAIT);
            return picture.getFile().kind() == MediaKind.IMAGE ? this.shown(user, picture) : this.refused(picture);
        } catch (MediaRefusedException exception) {
            return Optional.of(PortraitRefusal.of(exception.getReason()));
        }
    }

    private Optional<PortraitRefusal> shown(long user, MediaObject picture) {
        this.records.replaced(user, picture.getId(), picture.getCreatedAt())
            .ifPresent(previous -> this.library.discard(List.of(previous)));
        return Optional.empty();
    }

    private Optional<PortraitRefusal> refused(MediaObject document) {
        this.library.discard(List.of(document.getId()));
        return Optional.of(PortraitRefusal.UNSUPPORTED);
    }

    /**
     * Takes an account's portrait away and removes the picture.
     *
     * @param user the identifier of the account
     */
    public void remove(long user) {
        this.records.removed(user).ifPresent(previous -> this.library.discard(List.of(previous)));
    }
}
