package com.example.starter.identity;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The accounts as whatever they own reads them: the account of the reader, which is who owns what they
 * write, and the names the owners of a page of records are shown under. It is read inside the
 * transaction of the feature that asks, so it declares none of its own.
 */
@Component
public final class Owners {

    private final UserRepository repository;

    /**
     * Reads the accounts through the given repository.
     *
     * @param repository the accounts as the database holds them
     */
    public Owners(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * Answers the identifier of the account of the signed-in reader.
     *
     * @return that identifier
     * @throws IllegalStateException when the reader of the request has no account
     */
    public long reader() {
        return Reader.email()
            .flatMap(this.repository::findByProfileEmail)
            .map(User::getId)
            .orElseThrow(() -> new IllegalStateException("The reader of this request has no account"));
    }

    /**
     * Answers the names the given accounts are shown under, read in one query.
     *
     * @param identifiers the identifiers of the accounts
     * @return each name, under the identifier of its account
     */
    public Map<Long, String> names(Collection<Long> identifiers) {
        return this.repository.findAllById(identifiers)
            .stream()
            .collect(Collectors.toMap(User::getId, user -> user.getProfile().fullName()));
    }
}
