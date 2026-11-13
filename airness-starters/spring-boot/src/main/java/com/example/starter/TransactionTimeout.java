package com.example.starter;

import lombok.experimental.UtilityClass;

/**
 * How long a transaction of the application may run before it is rolled back. Every transaction reads it
 * from here, so the application layers of every feature are held to the same limit.
 */
@UtilityClass
public final class TransactionTimeout {

    /**
     * The longest a transaction may run, in seconds.
     */
    public static final int SECONDS = 10;
}
