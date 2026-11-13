package com.example.starter.identity;

import com.example.starter.web.Wording;
import java.util.Map;

/**
 * How an attempt to prove a password turned out under the policy on refused sign-ins.
 */
public enum Proof {

    /**
     * The password was the account's own.
     */
    PROVEN,

    /**
     * The password was not the account's own, and the attempt was counted.
     */
    WRONG,

    /**
     * The account is locked, so the password was not compared at all.
     */
    LOCKED;

    /**
     * Answers what the page tells the reader about this outcome, under the name of the control the
     * password was typed into.
     *
     * @param control the name of the control the password was typed into
     * @return one refusal against that control, and nothing where the password was proved
     */
    public Map<String, Wording> refusal(String control) {
        return switch (this) {
            case PROVEN -> Map.of();
            case WRONG -> Map.of(control, Wording.of("account.current.wrong"));
            case LOCKED -> Map.of(control, Wording.of("account.current.locked"));
        };
    }
}
