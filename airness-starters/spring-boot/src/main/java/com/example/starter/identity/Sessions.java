package com.example.starter.identity;

import java.util.stream.Stream;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * The sessions every account is signed in under. Ending them is how stopping an account, changing what
 * it may do, or changing its password takes effect at once rather than when its sessions would have
 * lapsed: the next request made under an ended session is sent to the sign-in page, where the account is
 * judged afresh.
 */
@Component
public final class Sessions {

    private final SessionRegistry registry;

    /**
     * Reads the sessions from the given registry, which the filter chain writes every sign-in into.
     *
     * @param registry the sessions of the application, by the account each is signed in under
     */
    public Sessions(SessionRegistry registry) {
        this.registry = registry;
    }

    /**
     * Ends every session the account under the given address is signed in under.
     *
     * @param email the address the account signs in with
     */
    public void end(String email) {
        this.signedIn(email).forEach(SessionInformation::expireNow);
    }

    /**
     * Ends every session the account under the given address is signed in under, apart from one.
     *
     * @param email the address the account signs in with
     * @param kept  the identifier of the session that stays, which matches none when it is empty
     */
    public void endOthers(String email, String kept) {
        this.signedIn(email).filter(session -> !session.getSessionId().equals(kept)).forEach(
            SessionInformation::expireNow
        );
    }

    private Stream<SessionInformation> signedIn(String email) {
        return this.registry.getAllPrincipals()
            .stream()
            .filter(UserDetails.class::isInstance)
            .map(UserDetails.class::cast)
            .filter(details -> details.getUsername().equals(email))
            .flatMap(details -> this.registry.getAllSessions(details, false).stream());
    }
}
