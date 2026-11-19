package com.example.starter.identity;

import com.example.starter.TransactionTimeout;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rows that give accounts their portraits, each written in a transaction of its own, because the picture
 * is stored and rendered outside any transaction.
 *
 * <p>The class is not final because the container wraps it in a proxy, which is a subclass.
 */
@Component
public class PortraitRecords {

    private final UserPortraitRepository portraits;

    /**
     * Reads and writes the portraits through the given repository.
     *
     * @param portraits the portraits as the database holds them
     */
    public PortraitRecords(UserPortraitRepository portraits) {
        this.portraits = portraits;
    }

    /**
     * Answers the stored pictures of the given accounts.
     *
     * @param users the identifiers of the accounts
     * @return the identifier of each account's picture, under the identifier of the account, for each account
     *         with one
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public Map<Long, Long> pictures(Collection<Long> users) {
        return this.portraits.findAllById(users)
            .stream()
            .collect(Collectors.toMap(UserPortrait::getUserId, UserPortrait::getMediaId));
    }

    /**
     * Gives an account the given picture in place of the one it had.
     *
     * @param user    the identifier of the account
     * @param picture the identifier of the stored picture
     * @param at      when the account was given it
     * @return the identifier of the picture it had, which nothing shows any longer, or nothing where it had none
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public Optional<Long> replaced(long user, long picture, Instant at) {
        Optional<Long> previous = this.taken(user);
        this.portraits.save(new UserPortrait(user, picture, at));
        return previous;
    }

    /**
     * Takes an account's portrait away.
     *
     * @param user the identifier of the account
     * @return the identifier of the picture it had, which nothing shows any longer, or nothing where it had none
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public Optional<Long> removed(long user) {
        return this.taken(user);
    }

    private Optional<Long> taken(long user) {
        Optional<UserPortrait> held = this.portraits.findById(user);
        held.ifPresent(this.portraits::delete);
        this.portraits.flush();
        return held.map(UserPortrait::getMediaId);
    }
}
