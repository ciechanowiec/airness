package com.example.starter.identity;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * What follows every attempt to sign in. The framework announces each outcome, and this hands it to the
 * application layer, which records it and keeps the count the policy on refused sign-ins reads.
 */
@Component
public final class SignInOutcomes {

    private final SignIns signIns;

    /**
     * Hands every outcome to the given application layer.
     *
     * @param signIns every attempt to sign in, as the application records it
     */
    public SignInOutcomes(SignIns signIns) {
        this.signIns = signIns;
    }

    /**
     * Records a sign-in that was let in.
     *
     * @param event what the framework announced
     */
    @EventListener
    public void succeeded(AuthenticationSuccessEvent event) {
        this.signIns.succeeded(event.getAuthentication().getName());
    }

    /**
     * Records a sign-in that was refused, for whatever reason it was.
     *
     * @param event what the framework announced
     */
    @EventListener
    public void refused(AbstractAuthenticationFailureEvent event) {
        this.signIns.refused(event.getAuthentication().getName());
    }
}
