package com.example.starter.audit;

/**
 * How what the audit trail records turned out.
 */
public enum AuditOutcome {

    /**
     * It was done.
     */
    SUCCEEDED,

    /**
     * It was asked for and refused.
     */
    REFUSED
}
