package com.example.starter.demo;

import com.example.starter.identity.Role;
import com.example.starter.identity.UserForm;
import com.example.starter.identity.UserProfile;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Writes the people and tasks of {@link DemoCast} when the application is asked to, so a local run opens
 * on a team in the middle of its work rather than on empty lists.
 *
 * <p>It is off unless {@code app.demo-data} is true, which a deployment never sets. It runs right after
 * {@link com.example.starter.identity.FirstAdministrator}, and writes nothing once any of its people
 * exists, so a restart neither repeats it nor touches an application whose data is its own, even where
 * one of its people was renamed since.
 *
 * <p>Everything goes through the application layer, signed in as whoever would have done it: the first
 * administrator admits the people, which the password policy judges and the audit trail records, and each
 * person writes their own tasks, so every task has the owner the pages would have given it. Every person
 * signs in with the first administrator's password and an address in its domain, so the data asks for no
 * credential of its own.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class DemoData implements ApplicationRunner {

    private static final String WRITTEN = "Demonstration data was written: {} people and {} tasks";

    private final DemoLayer layer;

    private final boolean wanted;

    private final String administrator;

    private final String password;

    /**
     * Writes the demonstration data through the given application layer.
     *
     * @param layer    the application layer the data is written through
     * @param wanted   whether the application is asked for demonstration data
     * @param email    the address the first administrator signs in with
     * @param password the password the first administrator signs in with
     */
    public DemoData(
        DemoLayer layer,
        @Value("${app.demo-data}") boolean wanted,
        @Value("${app.administrator.email}") String email,
        @Value("${app.administrator.password}") String password
    ) {
        this.layer = layer;
        this.wanted = wanted;
        this.administrator = UserProfile.canonical(email);
        this.password = password;
    }

    /**
     * Writes the demonstration data if it is asked for and not yet written, and does nothing otherwise.
     *
     * @param arguments what the command line passed to the container
     */
    @Override
    public void run(ApplicationArguments arguments) {
        if (this.wanted && this.answering(this.administrator, Role.ADMIN, this::absent)) {
            this.write();
        }
    }

    private boolean absent() {
        return DemoCast.PEOPLE.stream().noneMatch(person -> this.layer.taken(person.email(this.domain())));
    }

    private void write() {
        LocalDate today = this.layer.today();
        this.acting(this.administrator, Role.ADMIN, () -> DemoCast.PEOPLE.forEach(this::admit));
        DemoCast.PEOPLE.forEach(
            person -> this.acting(
                person.email(this.domain()), person.role(),
                () -> person.tasks().forEach(task -> this.layer.create(task.content(today)))
            )
        );
        this.acting(
            this.administrator, Role.ADMIN,
            () -> DemoCast.PEOPLE.stream()
                .filter(DemoPerson::stopped)
                .forEach(person -> this.layer.stop(person.email(this.domain())))
        );
        int written = DemoCast.PEOPLE.stream().mapToInt(person -> person.tasks().size()).sum();
        log.atInfo().log(WRITTEN, DemoCast.PEOPLE.size(), written);
    }

    private void admit(DemoPerson person) {
        UserProfile profile = new UserProfile(person.email(this.domain()), person.fullName());
        this.layer.admit(new UserForm(profile, person.role(), this.password));
    }

    private String domain() {
        return this.administrator.substring(this.administrator.indexOf('@') + 1);
    }

    private void acting(String email, Role role, Runnable work) {
        this.answering(
            email, role, () -> {
                work.run();
                return email;
            }
        );
    }

    // The services judge the reader the security context names, so the work runs with that reader signed
    // in, and the context is emptied afterwards, so nothing later on this thread acts as them.
    private <T> T answering(String email, Role role, Supplier<T> work) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, null, List.of(role)));
        SecurityContextHolder.setContext(context);
        try {
            return work.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
