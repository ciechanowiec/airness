package com.example.starter.task;

import com.example.starter.media.Upload;
import com.example.starter.web.Notice;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Attaches files to a task and takes them from it, from the task's own page, which each answer leads back to.
 * A task the reader may not see is answered as one that does not exist.
 *
 * <p>Several files can be chosen at once. Each is attached on its own, so one refused file leaves the others
 * attached, and the page then says why the first refused one was refused.
 */
@Controller
@RequestMapping(TaskPages.TASKS)
public final class AttachmentPages {

    private static final String REFERENCE = "reference";

    private final Attachments attachments;

    /**
     * Attaches files through the given attachments.
     *
     * @param attachments the files attached to tasks, and what may be done to them
     */
    public AttachmentPages(Attachments attachments) {
        this.attachments = attachments;
    }

    /**
     * Attaches the chosen files to a task.
     *
     * @param reference the reference the task is known by outside the database
     * @param files     the files the reader chose
     * @return the task's page, saying the files were attached or why one was not
     * @throws ResponseStatusException when the reader may not see a task under that reference
     */
    @PostMapping("/{reference}/attachments")
    public String attach(
        @PathVariable(name = REFERENCE, required = true) UUID reference,
        @RequestParam(name = "files", required = false) @Nullable List<MultipartFile> files
    ) {
        List<MultipartFile> chosen = Optional.ofNullable(files)
            .orElse(List.of())
            .stream()
            .filter(file -> !Objects.toString(file.getOriginalFilename(), "").isBlank())
            .toList();
        Optional<AttachmentOutcome> refusal = chosen.isEmpty()
            ? Optional.of(AttachmentOutcome.NONE_CHOSEN)
            : chosen.stream()
                .map(file -> this.attached(reference, file))
                .toList()
                .stream()
                .flatMap(Optional::stream)
                .findFirst();
        String page = page(reference);
        return refusal.map(outcome -> "redirect:%s?%s=%s".formatted(page, AttachmentOutcome.PARAMETER, outcome.name()))
            .orElseGet(() -> Notice.ATTACHED.redirect(page));
    }

    /**
     * Takes a file from a task and removes it.
     *
     * @param reference the reference the task is known by outside the database
     * @param media     the reference the file is known by
     * @return the task's page, saying the file was removed
     * @throws ResponseStatusException when the reader may not see a task under that reference
     */
    @PostMapping("/{reference}/attachments/{media}/delete")
    public String detach(
        @PathVariable(name = REFERENCE, required = true) UUID reference,
        @PathVariable(name = "media", required = true) UUID media
    ) {
        try {
            this.attachments.detach(reference, media);
        } catch (IllegalStateException exception) {
            throw unknown(exception);
        }
        return Notice.DETACHED.redirect(page(reference));
    }

    private Optional<AttachmentOutcome> attached(UUID reference, MultipartFile file) {
        try {
            return this.attachments.attach(
                reference, new Upload(Objects.toString(file.getOriginalFilename(), ""), file)
            );
        } catch (IllegalStateException exception) {
            throw unknown(exception);
        }
    }

    private static String page(UUID reference) {
        return "%s/%s/edit".formatted(TaskPages.TASKS, reference);
    }

    private static ResponseStatusException unknown(IllegalStateException cause) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, null, cause);
    }
}
