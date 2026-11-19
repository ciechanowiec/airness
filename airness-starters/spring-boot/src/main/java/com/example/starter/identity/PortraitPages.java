package com.example.starter.identity;

import com.example.starter.media.Upload;
import com.example.starter.web.Notice;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * Changes and removes the reader's own portrait, from their account page, which each answer leads back to.
 * It carries no reference and offers no way to name anybody else.
 */
@Controller
@RequestMapping(PortraitPages.PORTRAIT)
public final class PortraitPages {

    static final String PORTRAIT = AccountPages.ACCOUNT + "/portrait";

    private final Portraits portraits;

    private final Owners owners;

    /**
     * Changes portraits through the given portraits, for the reader the given accounts know.
     *
     * @param portraits the pictures the accounts are shown with
     * @param owners    the accounts, which know the reader's own
     */
    public PortraitPages(Portraits portraits, Owners owners) {
        this.portraits = portraits;
        this.owners = owners;
    }

    /**
     * Gives the reader the chosen picture as their portrait.
     *
     * @param picture the picture the reader chose, which is absent where they chose none
     * @return the account page, saying the portrait changed or why the picture was not taken
     */
    @PostMapping
    public String change(@RequestParam(name = "picture", required = false) Optional<MultipartFile> picture) {
        Optional<PortraitRefusal> refusal = picture.filter(
            file -> !Objects.toString(file.getOriginalFilename(), "").isBlank()
        ).map(
            file -> this.portraits.change(
                this.owners.reader(), new Upload(Objects.toString(file.getOriginalFilename(), ""), file)
            )
        ).orElse(Optional.of(PortraitRefusal.NONE_CHOSEN));
        return refusal.map(
            reason -> "redirect:%s?%s=%s".formatted(AccountPages.ACCOUNT, PortraitRefusal.PARAMETER, reason.name())
        ).orElseGet(() -> Notice.PORTRAIT_CHANGED.redirect(AccountPages.ACCOUNT));
    }

    /**
     * Takes the reader's portrait away.
     *
     * @return the account page, saying the portrait was removed
     */
    @PostMapping("/delete")
    public String remove() {
        this.portraits.remove(this.owners.reader());
        return Notice.PORTRAIT_REMOVED.redirect(AccountPages.ACCOUNT);
    }
}
