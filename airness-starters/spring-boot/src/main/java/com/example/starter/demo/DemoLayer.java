package com.example.starter.demo;

import com.example.starter.identity.Roster;
import com.example.starter.identity.UserForm;
import com.example.starter.identity.UserView;
import com.example.starter.identity.Users;
import com.example.starter.task.TaskContent;
import com.example.starter.task.Tasks;
import com.example.starter.web.Listing;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * The application layer as the demonstration data reaches it: the few things writing a team needs, each
 * done through the service that owns it, so the rules and the audit trail apply as they do to a page.
 */
@Component
public final class DemoLayer {

    private final Users users;

    private final Roster roster;

    private final Tasks tasks;

    private final DemoSeeds seeds;

    /**
     * Reaches the application through the given services.
     *
     * @param users  the accounts and what may be done to them
     * @param roster the accounts as an administrator lists them
     * @param tasks  the tasks the users keep
     * @param seeds  the pictures of the demonstration, and the day it is written on
     */
    public DemoLayer(Users users, Roster roster, Tasks tasks, DemoSeeds seeds) {
        this.users = users;
        this.roster = roster;
        this.tasks = tasks;
        this.seeds = seeds;
    }

    /**
     * Answers whether an account already signs in under the given address, which an administrator asks.
     *
     * @param email the address
     * @return whether it is taken
     */
    boolean taken(String email) {
        return this.roster.taken(email);
    }

    /**
     * Admits the account the given form states, which an administrator does.
     *
     * @param form the account
     */
    void admit(UserForm form) {
        this.users.register(form);
    }

    /**
     * Stops the account under the given address, which an administrator does.
     *
     * @param email the address
     */
    void stop(String email) {
        this.reference(email).ifPresent(reference -> this.users.enabling(reference, false));
    }

    /**
     * Writes a task for the signed-in reader.
     *
     * @param content the task
     * @return the reference of the task written
     */
    UUID create(TaskContent content) {
        return this.tasks.create(content);
    }

    /**
     * Gives the signed-in reader the portrait the demonstration ships for them.
     *
     * @param picture the path of the picture under the classpath
     */
    void portray(String picture) {
        this.seeds.portray(picture);
    }

    /**
     * Attaches the picture the demonstration ships for a task to that task, as its keeper.
     *
     * @param task    the reference of the task
     * @param picture the path of the picture under the classpath
     */
    void attach(UUID task, String picture) {
        this.seeds.attach(task, picture);
    }

    /**
     * Answers the day it is in the zone the application keeps.
     *
     * @return the day
     */
    LocalDate today() {
        return this.seeds.today();
    }

    private Optional<UUID> reference(String email) {
        int pages = this.roster.people(Listing.FIRST_PAGE).pages();
        return IntStream.rangeClosed(Listing.FIRST_PAGE, pages)
            .boxed()
            .flatMap(page -> this.roster.people(page).items().stream())
            .filter(view -> view.profile().email().equals(email))
            .findFirst()
            .map(UserView::reference)
            .map(UUID::fromString);
    }
}
