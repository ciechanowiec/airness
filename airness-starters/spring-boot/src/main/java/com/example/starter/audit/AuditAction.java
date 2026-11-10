package com.example.starter.audit;

/**
 * What was done that the audit trail records: every outcome of a sign-in, every change to an account
 * and to what it may do, the writing of the first administrator, and every change somebody makes to a
 * task another account keeps, which only an administrator may.
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
     * Somebody changed the password they sign in with, or was refused because the current password they
     * typed was not theirs.
     */
    PASSWORD_CHANGED,

    /**
     * Somebody changed what a task another account keeps says.
     */
    TASK_REVISED,

    /**
     * Somebody moved a task another account keeps to another status.
     */
    TASK_MOVED,

    /**
     * Somebody removed a task another account keeps.
     */
    TASK_REMOVED
}
