package com.example.starter.demo;

import com.example.starter.identity.Role;
import java.util.List;

/**
 * One person of the demonstration data and the tasks they keep.
 *
 * @param fullName the name the pages show
 * @param mailbox  the part of the address before the at sign, which the domain of the first administrator
 *                 completes
 * @param role     what the person may do
 * @param stopped  whether the account was stopped, as for somebody who has left
 * @param tasks    the tasks the person keeps
 */
record DemoPerson(String fullName, String mailbox, Role role, boolean stopped, List<DemoTask> tasks) {

    DemoPerson {
        tasks = List.copyOf(tasks);
    }

    /**
     * Answers the address the person signs in with.
     *
     * @param domain the domain of the first administrator's address
     * @return the address
     */
    String email(String domain) {
        return this.mailbox + "@" + domain;
    }
}
