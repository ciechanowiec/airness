package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class TaskSortTest {

    @Test
    void readsAnOrderBackFromItsName() {
        assertEquals(TaskSort.NEWEST, TaskSort.named("NEWEST"));
    }

    @Test
    void endsEveryOrderOnTheIdentifier() {
        assertEquals(Sort.Order.asc("id"), TaskSort.TITLE.order().getOrderFor("id"));
    }
}
