package com.example.starter.task;

import lombok.experimental.UtilityClass;

/**
 * How long the text of a task may be. The column that stores a text, the form that takes it and the search
 * that looks for it all hold to the same limit, so a value one accepts is never one another refuses.
 */
@UtilityClass
final class TaskLimits {

    /**
     * The most characters a title holds.
     */
    static final int TITLE = 120;

    /**
     * The most characters a description holds.
     */
    static final int DESCRIPTION = 2000;
}
