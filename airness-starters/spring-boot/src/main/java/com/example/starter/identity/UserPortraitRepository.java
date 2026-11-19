package com.example.starter.identity;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The portraits of the accounts as the database holds them, one per account at most, under the account's
 * identifier.
 */
public interface UserPortraitRepository extends JpaRepository<UserPortrait, Long> {
}
