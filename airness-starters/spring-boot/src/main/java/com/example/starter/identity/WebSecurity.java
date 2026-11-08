package com.example.starter.identity;

import com.example.starter.language.LanguagePages;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Who may reach the application, and what a caller may reach without saying who they are.
 *
 * <p>Every public address is named one at a time, with its method, and the last word is that anything
 * else needs somebody signed in, because a chain whose last word is weaker makes every line above it
 * decorative. Which role may do what is not decided here: it is stated on the application layer, where
 * the same rule covers a hidden button and a request that was made anyway.
 *
 * <p>Every sign-in is written into a session registry, which is what lets stopping an account end the
 * sessions it is signed in under at once. A session lapses after the idle time the configuration
 * declares, and signing out ends it.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public final class WebSecurity {

    // Everything the pages use is served by this application, so every kind of resource is held to this
    // origin. A script or a style written inline is refused, which is why the templates carry no code.
    // Forms post only here, and nothing may frame a page.
    static final String CONTENT_POLICY
        = "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; object-src 'none'";

    private static final String HOME = "/";

    private static final String REFUSED = LoginPages.LOGIN + "?failed=true";

    private static final String ENDED = LoginPages.LOGIN + "?ended=true";

    // Where the container sends a request that ended in a failure, which is a dispatch of its own that
    // this chain covers too. It is open, so a reader who has not signed in is shown the failure rather
    // than the sign-in form, and it discloses a status and nothing else.
    private static final String FAILURE = "/error";

    private static final String HEALTH = "/actuator/health";

    // BCrypt without an argument hashes at the work factor chosen for the hardware of 2011. Twelve is
    // roughly a quarter of a second today: slow to attack, quick enough that the form answers at once.
    private static final int WORK_FACTOR = 12;

    private static final int UNLIMITED = -1;

    /**
     * Answers the chain every request passes through.
     *
     * @param http     the chain under construction, which the container supplies
     * @param registry the sessions of the application, by the account each is signed in under
     * @return the chain that leaves the public addresses open and needs a signed-in reader for the rest
     */
    @Bean
    @SneakyThrows
    SecurityFilterChain chain(HttpSecurity http, SessionRegistry registry) {
        return http
            .authorizeHttpRequests(
                requests -> requests
                    .requestMatchers("/css/**", "/js/**", "/favicon.svg").permitAll()
                    .requestMatchers(HttpMethod.GET, HOME).permitAll()
                    .requestMatchers(HttpMethod.GET, LoginPages.LOGIN).permitAll()
                    .requestMatchers(HttpMethod.POST, LanguagePages.LANGUAGE).permitAll()
                    .requestMatchers(HttpMethod.GET, FAILURE).permitAll()
                    .requestMatchers(HttpMethod.POST, FAILURE).permitAll()
                    .requestMatchers(HttpMethod.GET, HEALTH).permitAll()
                    .anyRequest().authenticated()
            ).formLogin(
                form -> form
                    .loginPage(LoginPages.LOGIN)
                    .failureUrl(REFUSED)
                    .defaultSuccessUrl(HOME)
            ).logout(exit -> exit.logoutSuccessUrl(HOME))
            .sessionManagement(
                session -> session.sessionConcurrency(
                    concurrency -> concurrency.maximumSessions(UNLIMITED).sessionRegistry(registry).expiredUrl(ENDED)
                )
            ).headers(
                headers -> headers
                    .contentSecurityPolicy(policy -> policy.policyDirectives(CONTENT_POLICY))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            ).build();
    }

    /**
     * Answers the registry every sign-in is written into.
     *
     * @return the sessions of the application, by the account each is signed in under
     */
    @Bean
    SessionRegistry signedInSessions() {
        return new SessionRegistryImpl();
    }

    /**
     * Answers what tells the registry that the container ended a session, so a session that lapsed or
     * was signed out of is not held in it any longer.
     *
     * @return the publisher of the container's session events
     */
    @Bean
    HttpSessionEventPublisher sessionEvents() {
        return new HttpSessionEventPublisher();
    }

    /**
     * Answers what turns a password into the hash stored in its place, and compares the two.
     *
     * @return the encoder every password of the application passes through
     */
    @Bean
    PasswordEncoder encoder() {
        return new BCryptPasswordEncoder(WORK_FACTOR);
    }
}
