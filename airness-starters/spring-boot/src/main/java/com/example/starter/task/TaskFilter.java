package com.example.starter.task;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * What a reader narrowed the list of tasks to, as the address of the list carries it: the text the
 * titles hold, the status the tasks stand at and the order they are read in. The address is the whole of
 * the state, with the page beside these, so a narrowed list can be shared and reopened.
 *
 * @param search the text the titles hold, which is empty where the reader searched for nothing
 * @param status the name of the status the tasks stand at, which is empty for every status
 * @param sort   the name of the order the tasks are read in
 */
public record TaskFilter(String search, String status, String sort) {

    // A search longer than any title can hold finds nothing a shorter one would not.
    private static final int LONGEST_SEARCH = TaskContent.TITLE_LIMIT;

    private static final int FIRST_PAGE = 1;

    /**
     * Stands the empty string in the place of anything the address left out, and holds a search to the
     * length a title can have.
     *
     * @param search the text the titles hold, or nothing
     * @param status the name of the status, or nothing
     * @param sort   the name of the order, or nothing
     */
    public TaskFilter(@Nullable String search, @Nullable String status, @Nullable String sort) {
        String searched = Objects.requireNonNullElse(search, "").strip();
        this.search = searched.length() > LONGEST_SEARCH ? searched.substring(0, LONGEST_SEARCH) : searched;
        this.status = Objects.requireNonNullElse(status, "");
        this.sort = Objects.requireNonNullElse(sort, "");
    }

    /**
     * Answers the status the list is narrowed to.
     *
     * @return that status, and nothing where the list shows every status
     */
    public Optional<TaskStatus> narrowedTo() {
        return TaskStatus.named(this.status);
    }

    /**
     * Answers the order the list is read in.
     *
     * @return that order, which is by due day where the address named none
     */
    public TaskSort order() {
        return TaskSort.named(this.sort);
    }

    /**
     * Answers whether the reader narrowed the list at all, which is what decides whether an empty list
     * says nothing matched or that there is nothing yet.
     *
     * @return whether a search or a status narrows the list
     */
    public boolean narrowed() {
        return !this.search.isEmpty() || this.narrowedTo().isPresent();
    }

    /**
     * Answers the criterion of the tasks this filter shows, among the given ones.
     *
     * @param visible the criterion of the tasks the reader may see
     * @return the criterion of the tasks the list shows
     */
    public Specification<Task> narrowing(Specification<Task> visible) {
        Stream<Specification<Task>> searched = this.search.isEmpty() ? Stream.empty() : Stream.of(
            TaskCriteria.titled(this.search)
        );
        Stream<Specification<Task>> standing = this.narrowedTo().map(TaskCriteria::standing).stream();
        List<Specification<Task>> all = Stream.of(Stream.of(visible), searched, standing).flatMap(each -> each)
            .toList();
        return Specification.allOf(all);
    }

    /**
     * Answers which tasks the given page of the list reads, and in which order.
     *
     * @param page which page of the list is shown, counted from one, where anything below one is the first
     * @param size how many tasks a page holds
     * @return the page request
     */
    public Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, FIRST_PAGE) - FIRST_PAGE, size, this.order().order());
    }

    /**
     * Answers the narrowing as query parameters, each led by its ampersand, which every step through
     * the pages carries so the second page of a search is the second page of that search.
     *
     * @return the query that restates this narrowing, without the page
     */
    public String query() {
        return "&search=%s&status=%s&sort=%s".formatted(
            encoded(this.search), encoded(this.status), encoded(this.order().name())
        );
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
