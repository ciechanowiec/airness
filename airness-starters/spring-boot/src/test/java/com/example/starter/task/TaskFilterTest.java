package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskFilterTest {

    private static final int PAGE_SIZE = 20;

    @Test
    void holdsASearchToTheLengthATitleCanHave() {
        String searched = "x".repeat(TaskContent.TITLE_LIMIT + 10);
        assertEquals(TaskContent.TITLE_LIMIT, new TaskFilter(searched, "", "").search().length());
    }

    @Test
    void readsTheStatusTheListIsNarrowedTo() {
        assertEquals(Optional.of(TaskStatus.DONE), new TaskFilter("", "DONE", "").narrowedTo());
    }

    @Test
    void ordersByDueDayWhereTheAddressNamedNoOrder() {
        assertEquals(TaskSort.DUE, new TaskFilter("", "", "").order());
    }

    @Test
    void isNarrowedBySearchingAlone() {
        assertTrue(new TaskFilter("letter", "", "").narrowed());
    }

    @Test
    void isNarrowedByAStatusAlone() {
        assertTrue(new TaskFilter("", "OPEN", "").narrowed());
    }

    @Test
    void isNotNarrowedWithNeitherSearchNorStatus() {
        assertFalse(new TaskFilter("", "", "TITLE").narrowed());
    }

    @Test
    void restatesItsNarrowingAsAQuery() {
        assertEquals("&search=a+letter&status=OPEN&sort=TITLE", new TaskFilter("a letter", "OPEN", "TITLE").query());
    }

    @Test
    void readsThePageItIsAskedForCountedFromNought() {
        assertEquals(2, new TaskFilter("", "", "").pageable(3, PAGE_SIZE).getPageNumber());
    }

    @Test
    void readsTheFirstPageForAPageBelowOne() {
        assertEquals(0, new TaskFilter("", "", "").pageable(0, PAGE_SIZE).getPageNumber());
    }
}
