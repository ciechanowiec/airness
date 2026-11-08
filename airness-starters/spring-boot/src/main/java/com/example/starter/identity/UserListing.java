package com.example.starter.identity;

import java.util.List;

/**
 * One page of the accounts, as the page that shows them needs it. The numbers are counted from one,
 * because they are read by somebody rather than by an index.
 *
 * @param people the accounts on this page
 * @param page   which page of the list this is
 * @param pages  how many pages the list has, which is at least one
 */
public record UserListing(List<UserView> people, int page, int pages) {

    /**
     * Copies the accounts out of reach of whoever handed them over.
     */
    public UserListing {
        people = List.copyOf(people);
    }
}
