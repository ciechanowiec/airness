package com.example.starter.audit;

/**
 * What was done that the audit trail records: every outcome of a sign-in and of a current password typed
 * to change it, every change to an account and to what it may do, the writing of the first administrator,
 * and every change an administrator makes to a task somebody else keeps.
 */
public enum AuditAction {

    /**
     * Somebody tried to sign in.
     */
    SIGN_IN,

    /**
     * Too many refused sign-ins in a row locked an account for a while.
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
     * An administrator changed what an account may do, which is recorded beside the restatement that did
     * it, so a change of what an account may do is never read as a change of its name.
     */
    ROLE_CHANGED,

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
     * typed was wrong or the account was locked.
     */
    PASSWORD_CHANGED,

    /**
     * An administrator changed what a task somebody else keeps says.
     */
    TASK_REVISED,

    /**
     * An administrator moved a task somebody else keeps to another status.
     */
    TASK_MOVED,

    /**
     * An administrator removed a task somebody else keeps.
     */
    TASK_REMOVED
}
