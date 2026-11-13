package com.example.starter.web;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * One page of a list, as the page that shows it needs it. The numbers are counted from one, because they
 * are read by somebody rather than by an index, and the pagination fragment reads them as they are.
 *
 * @param <T>   what the list holds
 * @param items the items on this page
 * @param page  which page of the list this is
 * @param pages how many pages the list has, which is at least one
 */
public record Listing<T>(List<T> items, int page, int pages) {

    /**
     * The number of the first page of every list.
     */
    public static final int FIRST_PAGE = 1;

    /**
     * Copies the items out of reach of whoever handed them over.
     */
    public Listing {
        items = List.copyOf(items);
    }

    /**
     * Answers which items the given page of a list reads, and in which order.
     *
     * @param page  which page of the list is shown, counted from one, where anything below one is the first
     * @param size  how many items a page holds
     * @param order the order the items are read in
     * @return the page request
     */
    public static Pageable request(int page, int size, Sort order) {
        return PageRequest.of(Math.max(page, FIRST_PAGE) - FIRST_PAGE, size, order);
    }

    /**
     * Answers the page of a list the database found, holding the given items in place of what it found.
     *
     * @param <T>   what the list holds
     * @param found what the database found for the page
     * @param items what the page shows of each item found, in the order found
     * @return that page of the list
     */
    public static <T> Listing<T> of(Page<?> found, List<T> items) {
        return new Listing<>(items, found.getNumber() + FIRST_PAGE, Math.max(found.getTotalPages(), FIRST_PAGE));
    }
}
