package com.example.starter.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every account of the application, as the pages that manage them read it: one page of them, one of
 * them, and the questions the account form asks before it is submitted. Reading here is an
 * administrator's alone. Nothing persistent leaves this class, and no hash is among what does.
 *
 * <p>Every method is an administrator's, which is why the guard sits on the class. The class is not final
 * because the container wraps it in a proxy, which is a subclass, and both the
 * transaction and the authorization are applied by that proxy.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class Roster {

    private static final int TIMEOUT_SECONDS = 10;

    private static final int FIRST_PAGE = 1;

    // Twenty rows fit on a screen without scrolling past the head of the table.
    private static final int PAGE_SIZE = 20;

    private static final long LAST_ONE = 1;

    private final UserRepository repository;

    /**
     * Reads the accounts through the given repository.
     *
     * @param repository the accounts as the database holds them
     */
    public Roster(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * Answers one page of every account, each told whether it is the last administrator who may sign
     * in, so the list can withhold a control the application layer would refuse.
     *
     * @param page which page of the list to read, counted from one
     * @return that page of the accounts
     */
    @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
    public UserListing people(int page) {
        Pageable wanted = PageRequest.of(Math.max(page, FIRST_PAGE) - FIRST_PAGE, PAGE_SIZE);
        Page<User> found = this.repository.admitted(wanted);
        long administrators = this.repository.enabledUnder(Role.ADMIN);
        List<UserView> views = found.getContent()
            .stream()
            .map(user -> UserView.of(user, sole(user, administrators)))
            .toList();
        return new UserListing(views, found.getNumber() + FIRST_PAGE, Math.max(found.getTotalPages(), FIRST_PAGE));
    }

    /**
     * Answers one account, which is what the form that restates it opens with.
     *
     * @param reference the reference the account is known by outside the database
     * @return that account, or nothing when nobody carries the reference
     */
    @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
    public Optional<UserView> person(UUID reference) {
        long administrators = this.repository.enabledUnder(Role.ADMIN);
        return this.repository.findByReference(reference).map(user -> UserView.of(user, sole(user, administrators)));
    }

    /**
     * Answers the roles an account may be given, which is what the control that states one offers.
     *
     * @return every role
     */
    public List<Role> roles() {
        return List.of(Role.values());
    }

    /**
     * Answers whether an account already signs in under the given address.
     *
     * @param email the address in its canonical spelling
     * @return whether an account under that address exists
     */
    @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
    public boolean taken(String email) {
        return this.repository.existsByProfileEmail(email);
    }

    /**
     * Answers whether an account other than the one under the given reference signs in under the given
     * address, which is what the form that restates an account asks: its own address is taken by itself.
     *
     * @param email     the address in its canonical spelling
     * @param reference the reference of the account that is allowed to carry it
     * @return whether another account under that address exists
     */
    @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
    public boolean takenByAnother(String email, UUID reference) {
        return this.repository.existsByProfileEmailAndReferenceNot(email, reference);
    }

    private static boolean sole(User user, long administrators) {
        UserAccess access = user.getAccess();
        return access.enabled() && access.role() == Role.ADMIN && administrators == LAST_ONE;
    }
}
