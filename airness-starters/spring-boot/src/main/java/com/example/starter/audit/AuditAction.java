package com.example.starter.audit;

/**
 * What was done that the audit trail records: every outcome of a sign-in, every change to an account
 * and to what it may do, and the writing of the first administrator.
 */
public enum AuditAction {

    /**
     * Somebody tried to sign in.
     */
    SIGN_IN,

    /**
     * Too many refused sign-ins in a row stopped an account signing in for a while.
     */
    ACCOUNT_LOCKED,

    /**
     * The first administrator was written into an application that had no account.
     */
    ADMINISTRATOR_BOOTSTRAPPED,

    /**
     * An administrator admitted a new account.
     */
    ACCOUNT_CREATED,

    /**
     * An administrator changed the name, the address or the role of an account.
     */
    ACCOUNT_RESTATED,

    /**
     * An administrator let an account sign in again.
     */
    ACCOUNT_ENABLED,

    /**
     * An administrator stopped an account signing in.
     */
    ACCOUNT_DISABLED,

    /**
     * Somebody changed the password they sign in with.
     */
    PASSWORD_CHANGED
}
