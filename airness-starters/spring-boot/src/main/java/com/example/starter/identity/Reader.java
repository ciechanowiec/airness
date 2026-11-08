package com.example.starter.identity;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The reader in front of the application, as the security context states them. Nothing about the
 * request itself is read, which is what stops one reader acting as another.
 */
@UtilityClass
public final class Reader {

    /**
     * Answers the address the signed-in reader signs in with.
     *
     * @return that address, or nothing where nobody has signed in
     */
    public static Optional<String> email() {
        return signedIn().map(Authentication::getName);
    }

    /**
     * Answers whether the reader holds the given role.
     *
     * @param role the role asked about
     * @return whether the signed-in reader holds it, which nobody who has not signed in does
     */
    public static boolean holds(Role role) {
        return signedIn()
            .stream()
            .flatMap(reader -> reader.getAuthorities().stream())
            .map(GrantedAuthority::getAuthority)
            .anyMatch(role.getAuthority()::equals);
    }

    // An anonymous token answers true to being authenticated, so it is named here rather than left to
    // that question.
    private static Optional<Authentication> signedIn() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(Authentication::isAuthenticated)
            .filter(reader -> !(reader instanceof AnonymousAuthenticationToken));
    }
}
