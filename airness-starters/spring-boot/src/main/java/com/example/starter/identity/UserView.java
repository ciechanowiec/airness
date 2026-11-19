package com.example.starter.identity;

import com.example.starter.media.MediaView;

/**
 * What a list of the accounts shows about one of them, and what the form that restates one opens with.
 * No password and no hash is among it, because a view carrying one would be one rendered wherever a
 * template forgot.
 *
 * @param reference the reference the account is known by outside the database, as a link carries it
 * @param profile   how the account is named and reached
 * @param access    what the account may do, and whether it may sign in
 * @param sole      whether this is the only administrator left who may sign in
 * @param portrait  the portrait the account is shown with, or {@link MediaView#NONE}
 */
public record UserView(String reference, UserProfile profile, UserAccess access, boolean sole, MediaView portrait) {

    /**
     * Answers what a list shows about the given account.
     *
     * @param user     the account as the database holds it
     * @param sole     whether it is the only administrator left who may sign in
     * @param portrait the portrait it is shown with, or {@link MediaView#NONE}
     * @return that account as a list shows it
     */
    public static UserView of(User user, boolean sole, MediaView portrait) {
        return new UserView(user.getReference().toString(), user.getProfile(), user.getAccess(), sole, portrait);
    }

    /**
     * Answers what the form that restates this account opens with, which holds no password.
     *
     * @return everything this account states about itself
     */
    public UserForm described() {
        return new UserForm(this.profile, this.access.role(), "");
    }
}
